package cn.iocoder.yudao.module.zsjos.service.withdrawal;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.infra.framework.file.core.utils.FileTypeUtils;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentUploadRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.cashback.CashbackDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.withdrawal.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.cashback.CashbackMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.withdrawal.*;
import cn.iocoder.yudao.module.zsjos.service.audit.BusinessAuditService;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.CashbackConstants.STATUS_AVAILABLE;
import static cn.iocoder.yudao.module.zsjos.enums.CashbackConstants.STATUS_WITHDRAWING;
import static cn.iocoder.yudao.module.zsjos.enums.CashbackConstants.STATUS_WITHDRAWN;
import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_STATUS_ENABLED;
import static cn.iocoder.yudao.module.zsjos.enums.WithdrawalConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.module.zsjos.service.audit.AuditActionCatalog.*;

@Service
@Slf4j
public class WithdrawalServiceImpl implements WithdrawalService {
    private static final BigDecimal DEFAULT_MIN_AMOUNT = new BigDecimal("10.00");
    private static final Pattern CARD_PATTERN = Pattern.compile("\\d{12,32}");
    private static final Set<String> PROOF_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");
    private static final long MAX_PROOF_SIZE = 20L * 1024 * 1024;
    @Resource private WithdrawalMapper withdrawalMapper;
    @Resource private WithdrawalItemMapper itemMapper;
    @Resource private PartnerBankCardMapper cardMapper;
    @Resource private CashbackMapper cashbackMapper;
    @Resource private PartnerMapper partnerMapper;
    @Resource private BpmProcessInstanceApi processInstanceApi;
    @Resource private BpmProcessTaskApi processTaskApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private PermissionApi permissionApi;
    @Resource private ConfigApi configApi;
    @Resource private FileApi fileApi;
    @Resource private BusinessAuditService auditService;
    @Resource private WithdrawalNotifyPublisher notifyPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long apply(Long userId, WithdrawalApplyReqVO request) {
        PartnerDO partner = partnerMapper.selectEnabledByUserId(userId);
        if (partner == null || !PARTNER_STATUS_ENABLED.equals(partner.getStatus())) throw exception(WITHDRAWAL_PARTNER_INVALID);
        String cardNumber = normalizeCard(request.getCardNumber());
        List<Long> ids = request.getCashbackIds().stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (ids.isEmpty() || ids.size() != request.getCashbackIds().size()) throw exception(WITHDRAWAL_CASHBACK_INVALID);
        BigDecimal availableBalance = cashbackMapper.selectAvailableByBeneficiary(userId).stream()
                .map(CashbackDO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CashbackDO> selected = new ArrayList<>(ids.size());
        BigDecimal amount = BigDecimal.ZERO;
        for (Long id : ids) {
            CashbackDO cashback = cashbackMapper.selectByIdForUpdate(id, TenantContextHolder.getRequiredTenantId());
            if (cashback == null || !Objects.equals(cashback.getBeneficiaryUserId(), userId)
                    || !Objects.equals(cashback.getPartnerId(), partner.getId()) || !STATUS_AVAILABLE.equals(cashback.getStatus())) {
                throw exception(WITHDRAWAL_CASHBACK_INVALID);
            }
            selected.add(cashback); amount = amount.add(cashback.getAmount());
        }
        if (amount.compareTo(minimumAmount()) < 0) throw exception(WITHDRAWAL_AMOUNT_TOO_LOW);
        if (amount.compareTo(availableBalance) > 0) throw exception(WITHDRAWAL_CASHBACK_INVALID);
        List<Long> financeUsers = financeReviewers();
        if (financeUsers.isEmpty()) throw exception(WITHDRAWAL_PROCESS_UNAVAILABLE);
        LocalDateTime now = LocalDateTime.now();
        WithdrawalDO record = new WithdrawalDO().setWithdrawalNo(number()).setPartnerId(partner.getId())
                .setApplicantUserId(userId).setStatus(STATUS_PENDING).setVerificationStatus(VERIFY_NORMAL)
                .setApplicationAmount(amount).setAvailableBalanceSnapshot(availableBalance)
                .setAccountNameSnapshot(request.getAccountName().trim()).setCardNumberSnapshot(cardNumber)
                .setBankNameSnapshot(request.getBankName().trim())
                .setBranchNameSnapshot(StrUtil.trim(request.getBranchName())).setSubmittedAt(now).setVersion(0);
        withdrawalMapper.insert(record);
        try {
            for (CashbackDO cashback : selected) {
                itemMapper.insert(new WithdrawalItemDO().setWithdrawalId(record.getId()).setCashbackId(cashback.getId())
                        .setAmountSnapshot(cashback.getAmount()).setActiveFlag(true));
                if (cashbackMapper.transitionStatus(cashback.getId(), cashback.getVersion(), STATUS_AVAILABLE, STATUS_WITHDRAWING) != 1) {
                    throw exception(WITHDRAWAL_STATE_INVALID);
                }
            }
        } catch (DuplicateKeyException duplicate) {
            throw exception(WITHDRAWAL_CASHBACK_INVALID);
        }
        if (Boolean.TRUE.equals(request.getSaveCard())) saveCard(record);
        BpmProcessInstanceCreateReqDTO process = new BpmProcessInstanceCreateReqDTO();
        process.setProcessDefinitionKey(PROCESS_DEFINITION_KEY); process.setBusinessKey("withdrawal:" + record.getId());
        process.setVariables(Map.of("withdrawalId", record.getId(), "applicationAmount", amount));
        process.setStartUserSelectAssignees(Map.of(TASK_DEFINITION_KEY, financeUsers));
        try {
            record.setProcessInstanceId(processInstanceApi.createProcessInstance(userId, process));
        } catch (RuntimeException ex) {
            log.error("[apply][withdrawalId({}) BPM start failed]", record.getId(), ex);
            throw exception(WITHDRAWAL_PROCESS_UNAVAILABLE);
        }
        withdrawalMapper.updateById(record);
        auditService.record(CATEGORY_WITHDRAWAL, WITHDRAWAL_SUBMITTED, "withdrawal", String.valueOf(record.getId()),
                "partner", Map.of("amount", amount, "cashbackCount", selected.size()));
        notifyPublisher.publish(SCENE_SUBMITTED, record.getId(), "withdrawal-submitted:" + record.getId(), userId,
                notifyPayload(record, financeUsers));
        return record.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "withdrawal", bizId = "#id", action = "cancel")
    public void cancel(Long id, Long userId) {
        WithdrawalDO record = lock(id);
        if (!Objects.equals(record.getApplicantUserId(), userId)) throw exception(WITHDRAWAL_PERMISSION_DENIED);
        if (!STATUS_PENDING.equals(record.getStatus())) throw exception(WITHDRAWAL_STATE_INVALID);
        record.setStatus(STATUS_CANCELLED).setCancelledByUserId(userId).setCancelledAt(LocalDateTime.now());
        withdrawalMapper.updateById(record); releaseCashbacks(record.getId());
        processInstanceApi.cancelProcessInstanceByStartUser(userId, record.getProcessInstanceId(), "兼职撤销提现申请");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "withdrawal", bizId = "#id", action = "review")
    public void rejectApproved(Long id, Long userId, String reason) {
        WithdrawalDO record = lock(id);
        if (!STATUS_APPROVED.equals(record.getStatus())) throw exception(WITHDRAWAL_STATE_INVALID);
        record.setStatus(STATUS_REJECTED).setReviewedByUserId(userId).setReviewedAt(LocalDateTime.now())
                .setRejectionReason(reason.trim());
        withdrawalMapper.updateById(record); releaseCashbacks(record.getId());
        auditService.record(CATEGORY_WITHDRAWAL, WITHDRAWAL_REJECTED, "withdrawal", String.valueOf(id),
                "finance", Map.of("amount", record.getApplicationAmount(), "reason", record.getRejectionReason()));
        notifyPublisher.publish(SCENE_REJECTED, id, "withdrawal-rejected:" + id,
                userId, notifyPayload(record, financeReviewers()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "withdrawal", bizId = "#id", action = "payout")
    public void recordPayout(Long id, Long userId, WithdrawalPayoutReqVO request) {
        WithdrawalDO record = lock(id);
        if (!STATUS_APPROVED.equals(record.getStatus())) throw exception(WITHDRAWAL_STATE_INVALID);
        String transactionNo = request.getBankTransactionNo().trim();
        WithdrawalDO duplicate = withdrawalMapper.selectByTransactionNo(transactionNo);
        if (duplicate != null && !Objects.equals(duplicate.getId(), id)) throw exception(WITHDRAWAL_TRANSACTION_DUPLICATE);
        FileInfoRespDTO proof = requireProof(request.getProofFileId(), userId);
        List<WithdrawalItemDO> items = itemMapper.selectByWithdrawalId(id);
        for (WithdrawalItemDO item : items) {
            CashbackDO cashback = cashbackMapper.selectByIdForUpdate(item.getCashbackId(), TenantContextHolder.getRequiredTenantId());
            if (cashback == null || !STATUS_WITHDRAWING.equals(cashback.getStatus())
                    || cashbackMapper.transitionStatus(cashback.getId(), cashback.getVersion(), STATUS_WITHDRAWING, STATUS_WITHDRAWN) != 1) {
                throw exception(WITHDRAWAL_STATE_INVALID);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        record.setStatus(STATUS_PAID).setBankTransactionNo(transactionNo).setProofFileId(proof.getId())
                .setProofFileNameSnapshot(proof.getName()).setProofFileTypeSnapshot(proof.getType())
                .setPayoutRemark(StrUtil.trim(request.getRemark())).setPaidByUserId(userId).setPaidAt(now);
        try { withdrawalMapper.updateById(record); } catch (DuplicateKeyException duplicateKey) {
            throw exception(WITHDRAWAL_TRANSACTION_DUPLICATE);
        }
        auditService.record(CATEGORY_WITHDRAWAL, WITHDRAWAL_PAYOUT, "withdrawal", String.valueOf(id),
                "finance", Map.of("amount", record.getApplicationAmount(), "proofFileId", proof.getId()));
        notifyPublisher.publish(SCENE_PAID, id, "withdrawal-paid:" + id, userId,
                notifyPayload(record, financeReviewers()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleProcessResult(String processInstanceId, Integer processStatus, String reason) {
        if (!BpmProcessInstanceStatusEnum.isProcessEndStatus(processStatus)) return;
        WithdrawalDO found = withdrawalMapper.selectByProcessInstanceId(processInstanceId);
        if (found == null) return;
        WithdrawalDO record = lock(found.getId());
        if (!STATUS_PENDING.equals(record.getStatus())) return;
        LocalDateTime now = LocalDateTime.now();
        var statuses = processTaskApi.getProcessNodeStatuses(processInstanceId, Set.of(TASK_DEFINITION_KEY));
        if (BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(processStatus)) {
            if (!statuses.isEmpty()) record.setReviewedByUserId(statuses.getFirst().getReviewerUserId());
            record.setReviewedAt(now);
            record.setStatus(STATUS_APPROVED).setApprovedAmount(record.getApplicationAmount());
            auditService.record(CATEGORY_WITHDRAWAL, WITHDRAWAL_APPROVED, "withdrawal",
                    String.valueOf(record.getId()), "finance", Map.of("amount", record.getApplicationAmount()));
        } else {
            boolean rejected = BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(processStatus);
            if (rejected) {
                if (!statuses.isEmpty()) record.setReviewedByUserId(statuses.getFirst().getReviewerUserId());
                record.setReviewedAt(now);
            }
            record.setStatus(rejected ? STATUS_REJECTED : STATUS_CANCELLED)
                    .setRejectionReason(rejected ? reason : null);
            releaseCashbacks(record.getId());
            if (STATUS_REJECTED.equals(record.getStatus())) {
                auditService.record(CATEGORY_WITHDRAWAL, WITHDRAWAL_REJECTED, "withdrawal",
                        String.valueOf(record.getId()), "finance", Map.of("amount", record.getApplicationAmount(),
                                "reason", StrUtil.nullToEmpty(reason)));
            }
        }
        withdrawalMapper.updateById(record);
        notifyPublisher.publish(STATUS_APPROVED.equals(record.getStatus()) ? SCENE_APPROVED : SCENE_REJECTED,
                record.getId(), "withdrawal-process-result:" + processInstanceId, 0L,
                notifyPayload(record, financeReviewers()));
    }

    @Override
    public PageResult<WithdrawalRespVO> getPage(WithdrawalPageReqVO request, Long applicantUserId) {
        PageResult<WithdrawalDO> page = withdrawalMapper.selectPage(request, applicantUserId);
        return new PageResult<>(page.getList().stream().map(item -> toResponse(item, false)).toList(), page.getTotal());
    }

    @Override
    @ZsjosPermission(bizType = "withdrawal", bizId = "#id", action = "read")
    public WithdrawalRespVO getDetail(Long id, Long userId, boolean fullCard) {
        WithdrawalDO record = withdrawalMapper.selectById(id);
        if (record == null) throw exception(WITHDRAWAL_NOT_EXISTS);
        if (fullCard && !permissionApi.hasAnyPermissions(userId, "zsjos:withdrawal:finance-query")) {
            throw exception(WITHDRAWAL_PERMISSION_DENIED);
        }
        WithdrawalRespVO response = toResponse(record, fullCard);
        if (fullCard) auditService.record(CATEGORY_WITHDRAWAL, WITHDRAWAL_CARD_VIEW, "withdrawal",
                String.valueOf(id), "finance", Map.of("purpose", "withdrawal_review"));
        return response;
    }

    @Override
    public List<BankCardRespVO> getMyCards(Long userId) {
        return cardMapper.selectByOwner(userId).stream().map(card -> new BankCardRespVO().setId(card.getId())
                .setAccountName(card.getAccountName()).setMaskedCardNumber(mask(card.getCardNumber()))
                .setBankName(card.getBankName()).setBranchName(card.getBranchName()).setDefaultCard(card.getDefaultCard())).toList();
    }

    @Override
    public LeadAttachmentUploadRespVO uploadProof(Long userId, MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_PROOF_SIZE) throw exception(WITHDRAWAL_PROOF_INVALID);
        byte[] content = file.getBytes(); String type = FileTypeUtils.getMineType(content, file.getOriginalFilename());
        if (!PROOF_TYPES.contains(type)) throw exception(WITHDRAWAL_PROOF_INVALID);
        FileInfoRespDTO saved = fileApi.createFileInfo(content, file.getOriginalFilename(), "zsjos/withdrawal-proof", type);
        return new LeadAttachmentUploadRespVO(saved.getId(), saved.getUrl(), saved.getName(), saved.getType(), saved.getSize());
    }

    private WithdrawalDO lock(Long id) {
        WithdrawalDO record = withdrawalMapper.selectByIdForUpdate(id, TenantContextHolder.getRequiredTenantId());
        if (record == null) throw exception(WITHDRAWAL_NOT_EXISTS); return record;
    }

    private void releaseCashbacks(Long withdrawalId) {
        for (WithdrawalItemDO item : itemMapper.selectByWithdrawalId(withdrawalId)) {
            if (!Boolean.TRUE.equals(item.getActiveFlag())) continue;
            CashbackDO cashback = cashbackMapper.selectByIdForUpdate(item.getCashbackId(), TenantContextHolder.getRequiredTenantId());
            if (cashback == null || cashbackMapper.transitionStatus(cashback.getId(), cashback.getVersion(),
                    STATUS_WITHDRAWING, STATUS_AVAILABLE) != 1) throw exception(WITHDRAWAL_STATE_INVALID);
        }
        itemMapper.deactivate(withdrawalId);
    }

    private WithdrawalRespVO toResponse(WithdrawalDO record, boolean fullCard) {
        WithdrawalRespVO response = BeanUtils.toBean(record, WithdrawalRespVO.class);
        response.setMaskedCardNumber(mask(record.getCardNumberSnapshot()));
        response.setCardNumber(fullCard ? record.getCardNumberSnapshot() : null);
        response.setItems(itemMapper.selectByWithdrawalId(record.getId()).stream().map(item ->
                new WithdrawalRespVO.Item().setCashbackId(item.getCashbackId()).setAmount(item.getAmountSnapshot())).toList());
        if (record.getProofFileId() != null) {
            try { response.setProofUrl(fileApi.presignGetUrl(record.getProofFileId(), 600)); }
            catch (ServiceException ignored) { response.setProofUrl(null); }
        }
        return response;
    }

    private void saveCard(WithdrawalDO record) {
        cardMapper.insert(new PartnerBankCardDO().setPartnerId(record.getPartnerId())
                .setOwnerUserId(record.getApplicantUserId()).setAccountName(record.getAccountNameSnapshot())
                .setCardNumber(record.getCardNumberSnapshot()).setBankName(record.getBankNameSnapshot())
                .setBranchName(record.getBranchNameSnapshot()).setDefaultCard(false).setVersion(0));
    }

    private FileInfoRespDTO requireProof(Long fileId, Long userId) {
        FileInfoRespDTO file;
        try { file = fileApi.getFileInfo(fileId); } catch (ServiceException ex) { throw exception(WITHDRAWAL_PROOF_INVALID); }
        if (file == null || !PROOF_TYPES.contains(file.getType()) || file.getSize() == null || file.getSize() > MAX_PROOF_SIZE
                || !String.valueOf(userId).equals(file.getCreator()) || StrUtil.isBlank(file.getPath())
                || !file.getPath().startsWith("zsjos/withdrawal-proof/")) throw exception(WITHDRAWAL_PROOF_INVALID);
        return file;
    }

    private List<Long> financeReviewers() {
        return adminUserApi.getUserListByStatus(CommonStatusEnum.ENABLE.getStatus()).stream()
                .map(user -> user.getId()).filter(id -> permissionApi.hasAnyPermissions(id, "zsjos:withdrawal:review"))
                .sorted().toList();
    }

    public void sendFinanceReminder() {
        int overdueDays = configuredInt(REMINDER_OVERDUE_DAYS_KEY, 7, 1, 365);
        List<WithdrawalDO> active = withdrawalMapper.selectPendingReminder(LocalDateTime.now().minusDays(overdueDays));
        List<WithdrawalDO> allActive = withdrawalMapper.selectList(new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<WithdrawalDO>()
                .in(WithdrawalDO::getStatus, List.of(STATUS_PENDING, STATUS_APPROVED)));
        long pending = allActive.stream().filter(item -> STATUS_PENDING.equals(item.getStatus())).count();
        List<WithdrawalDO> approved = allActive.stream().filter(item -> STATUS_APPROVED.equals(item.getStatus())).toList();
        Map<String, Object> payload = new LinkedHashMap<>(); payload.put("pendingCount", pending);
        payload.put("approvedCount", approved.size());
        payload.put("approvedAmount", approved.stream().map(WithdrawalDO::getApplicationAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        payload.put("overdueCount", active.size()); payload.put("financeUserIds", financeReviewers());
        notifyPublisher.publish(SCENE_FINANCE_REMINDER, 0L, "withdrawal-weekly:" + java.time.LocalDate.now(), 0L, payload);
    }

    private Map<String, Object> notifyPayload(WithdrawalDO record, List<Long> financeUsers) {
        Map<String, Object> payload = new LinkedHashMap<>(); payload.put("withdrawal.id", record.getId());
        payload.put("withdrawal.amount", record.getApplicationAmount()); payload.put("applicantUserId", record.getApplicantUserId());
        if (StrUtil.isNotBlank(record.getRejectionReason())) payload.put(NOTIFICATION_REJECTION_REASON, record.getRejectionReason());
        payload.put("financeUserIds", financeUsers); return payload;
    }

    private int configuredInt(String key, int fallback, int min, int max) {
        try { int value = Integer.parseInt(configApi.getConfigValueByKey(key)); return value >= min && value <= max ? value : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }

    private BigDecimal minimumAmount() {
        String value = configApi.getConfigValueByKey(MIN_AMOUNT_KEY);
        if (StrUtil.isBlank(value)) return DEFAULT_MIN_AMOUNT;
        try { BigDecimal parsed = new BigDecimal(value).setScale(2); return parsed.signum() > 0 ? parsed : DEFAULT_MIN_AMOUNT; }
        catch (RuntimeException ignored) { return DEFAULT_MIN_AMOUNT; }
    }

    private static String normalizeCard(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s", "");
        if (!CARD_PATTERN.matcher(normalized).matches()) throw exception(WITHDRAWAL_BANK_CARD_INVALID);
        return normalized;
    }
    private static String mask(String value) {
        if (value == null || value.length() < 8) return "****";
        return value.substring(0, 4) + " **** **** " + value.substring(value.length() - 4);
    }
    private static String number() {
        return "WD" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }
}
