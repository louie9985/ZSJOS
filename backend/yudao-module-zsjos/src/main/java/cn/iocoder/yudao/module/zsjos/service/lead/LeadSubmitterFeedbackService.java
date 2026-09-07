package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.hutool.core.util.DesensitizedUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.infra.framework.file.core.utils.FileTypeUtils;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerAccountService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadSubmitterFeedbackConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class LeadSubmitterFeedbackService {
    private static final Set<String> FILE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "application/pdf",
            "text/plain", "application/msword", "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    @Resource private LeadMapper leadMapper;
    @Resource private LeadSubmitterFeedbackMapper leadSubmitterFeedbackMapper;
    @Resource private LeadSubmitterFeedbackAttachmentMapper attachmentMapper;
    @Resource private LeadSubmitterFeedbackPermissionProvider permission;
    @Resource private LeadObjectPermissionService identityPermission;
    @Resource private PartnerAccountService partnerAccountService;
    @Resource private AdminUserApi adminUserApi;
    @Resource private FileApi fileApi;
    @Resource private LeadNotifyEventPublisher publisher;

    @ZsjosPermission(bizType = "lead-submitter-feedback", bizId = "#leadId", action = "create")
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long leadId, Long userId, LeadSubmitterFeedbackReqVO request) {
        LeadDO lead = lockedLead(leadId);
        // Recheck ownership under the Lead lock so a concurrent transfer cannot authorize the former owner.
        if (!Objects.equals(userId, lead.getOwnerUserId())) throw exception(LEAD_PERMISSION_DENIED);
        List<Long> fileIds = request.getAttachmentIds() == null ? List.of() : request.getAttachmentIds();
        String content = request.getFeedback().trim();
        String fingerprint = DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(content, fileIds, request.getVersion())));
        var replay = leadSubmitterFeedbackMapper.findReplay(leadId, userId, request.getIdempotencyKey());
        if (replay != null) {
            if (!fingerprint.equals(replay.getRequestFingerprint())) throw exception(LEAD_FEEDBACK_IDEMPOTENCY_CONFLICT);
            return replay.getId();
        }
        requireActionable(lead);
        if (!Objects.equals(lead.getVersion(), request.getVersion())) throw exception(LEAD_FEEDBACK_VERSION_CONFLICT);
        if (fileIds.size() > MAX_FILES || new HashSet<>(fileIds).size() != fileIds.size())
            throw exception(LEAD_FEEDBACK_ATTACHMENT_INVALID);
        List<LeadSubmitterFeedbackAttachmentDO> attachments = fileIds.stream()
                .map(id -> requireTemporaryAttachment(id, leadId, userId)).toList();
        var row = new LeadSubmitterFeedbackDO();
        row.setLeadId(leadId);
        row.setSalesUserId(userId);
        var sales = adminUserApi.getUser(userId);
        row.setSalesNameSnapshot(sales == null ? null : sales.getNickname());
        row.setSubmitterNameSnapshot(lead.getProviderOwnerNameSnapshot());
        if (PROVIDER_OWNER_SYSTEM_USER.equals(lead.getProviderOwnerType())) {
            var recipient = adminUserApi.getUser(lead.getProviderOwnerId());
            if (recipient == null || !CommonStatusEnum.ENABLE.getStatus().equals(recipient.getStatus()))
                throw exception(LEAD_FEEDBACK_RECIPIENT_MISSING);
            row.setSubmitterSubjectType(ADMIN_SUBJECT);
            row.setSubmitterUserId(recipient.getId());
        } else if (PROVIDER_OWNER_PARTNER.equals(lead.getProviderOwnerType())) {
            var account = partnerAccountService.getByPartnerId(lead.getProviderOwnerId());
            if (account == null || !CommonStatusEnum.ENABLE.getStatus().equals(account.getStatus()))
                throw exception(LEAD_FEEDBACK_RECIPIENT_MISSING);
            partnerAccountService.requireContext(account.getId());
            row.setSubmitterSubjectType(PARTNER_SUBJECT);
            row.setPartnerAccountId(account.getId());
            row.setPartnerId(account.getPartnerId());
        } else throw exception(LEAD_FEEDBACK_RECIPIENT_MISSING);
        LocalDateTime now = LocalDateTime.now();
        row.setFeedback(content);
        row.setRequestVersion(request.getVersion());
        row.setIdempotencyKey(request.getIdempotencyKey());
        row.setRequestFingerprint(fingerprint);
        leadSubmitterFeedbackMapper.insert(row);
        for (int i = 0; i < attachments.size(); i++) {
            var attachment = attachments.get(i);
            int updated = attachmentMapper.update(null, new LambdaUpdateWrapper<LeadSubmitterFeedbackAttachmentDO>()
                    .eq(LeadSubmitterFeedbackAttachmentDO::getId, attachment.getId())
                    .isNull(LeadSubmitterFeedbackAttachmentDO::getFeedbackId)
                    .set(LeadSubmitterFeedbackAttachmentDO::getFeedbackId, row.getId())
                    .set(LeadSubmitterFeedbackAttachmentDO::getSort, i));
            if (updated != 1) throw exception(LEAD_FEEDBACK_ATTACHMENT_INVALID);
        }
        if (leadMapper.updateVersionAndTouchActivity(leadId, request.getVersion(), now) != 1)
            throw exception(LEAD_FEEDBACK_VERSION_CONFLICT);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("feedback.id", row.getId());
        context.put("feedback.summary", content.substring(0, Math.min(content.length(), 200)));
        context.put("feedback.recipientType", row.getSubmitterSubjectType());
        context.put("feedback.recipientId", row.getPartnerAccountId() == null ? row.getSubmitterUserId() : row.getPartnerAccountId());
        publisher.publish(SCENE, leadId, "lead-submitter-feedback:" + row.getId(), userId, now, context);
        return row.getId();
    }

    @ZsjosPermission(bizType = "lead-submitter-feedback", bizId = "#leadId", action = "create")
    @Transactional(rollbackFor = Exception.class)
    public LeadSubmitterFeedbackRespVO.Attachment upload(Long leadId, Long userId, MultipartFile upload) throws IOException {
        LeadDO lead = lockedLead(leadId);
        if (!Objects.equals(userId, lead.getOwnerUserId())) throw exception(LEAD_PERMISSION_DENIED);
        requireActionable(lead);
        if (upload.isEmpty() || upload.getSize() > MAX_FILE_SIZE || upload.getOriginalFilename() == null
                || upload.getOriginalFilename().length() > 255) throw exception(LEAD_FEEDBACK_ATTACHMENT_INVALID);
        byte[] content = upload.getBytes();
        String type = FileTypeUtils.getMineType(content, upload.getOriginalFilename());
        if (!FILE_TYPES.contains(type)) throw exception(LEAD_FEEDBACK_ATTACHMENT_INVALID);
        FileInfoRespDTO file = fileApi.createFileInfo(content, upload.getOriginalFilename(), directory(leadId, userId), type);
        var row = new LeadSubmitterFeedbackAttachmentDO();
        row.setLeadId(leadId); row.setFileId(file.getId()); row.setUploaderUserId(userId);
        row.setOriginalName(file.getName()); row.setContentType(file.getType()); row.setFileSize(file.getSize());
        row.setExpiresAt(LocalDateTime.now().plusHours(24)); row.setSort(0);
        attachmentMapper.insert(row);
        return new LeadSubmitterFeedbackRespVO.Attachment(file.getId(), file.getName(), file.getType(), file.getSize(), file.getUrl());
    }

    @ZsjosPermission(bizType = "lead-submitter-feedback", bizId = "#leadId", action = "read")
    public PageResult<LeadSubmitterFeedbackRespVO> page(Long leadId, Long userId, PageParam page) {
        LeadDO lead = leadMapper.selectById(leadId);
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        if (!permission.canRead(lead, userId)) throw exception(LEAD_PERMISSION_DENIED);
        String subject = Objects.equals(userId, lead.getOwnerUserId()) ? null : ADMIN_SUBJECT;
        return project(lead, leadSubmitterFeedbackMapper.page(leadId, page, subject, userId), userId, false);
    }

    @ZsjosPermission(bizType = "lead-submitter-feedback", bizId = "#leadId", action = "read-partner")
    public PageResult<LeadSubmitterFeedbackRespVO> pagePartner(Long leadId, Long accountId, PageParam page) {
        Long partnerId = partnerAccountService.requireContext(accountId).partnerId();
        LeadDO lead = leadMapper.selectById(leadId);
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        if (!PROVIDER_OWNER_PARTNER.equals(lead.getProviderOwnerType()) || !Objects.equals(partnerId, lead.getProviderOwnerId()))
            throw exception(LEAD_PERMISSION_DENIED);
        return project(lead, leadSubmitterFeedbackMapper.page(leadId, page, PARTNER_SUBJECT, accountId), accountId, true);
    }

    private PageResult<LeadSubmitterFeedbackRespVO> project(LeadDO lead, PageResult<LeadSubmitterFeedbackDO> page,
                                                          Long viewer, boolean partner) {
        boolean mask = !partner && !DISPATCH_SPECIFIED.equals(lead.getDispatchMode())
                && ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus()) && lead.getSourceUserId() != null
                && lead.getOwnerUserId() != null && !Objects.equals(lead.getSourceUserId(), lead.getOwnerUserId())
                && !identityPermission.canViewUnmaskedIdentity(viewer, lead);
        return new PageResult<>(page.getList().stream().map(row -> {
            var result = new LeadSubmitterFeedbackRespVO();
            result.setId(row.getId()); result.setFeedback(row.getFeedback()); result.setCreateTime(row.getCreateTime());
            result.setSalesName(mask && !Objects.equals(viewer, row.getSalesUserId())
                    ? maskName(row.getSalesNameSnapshot()) : row.getSalesNameSnapshot());
            result.setSubmitterName(mask && !Objects.equals(viewer, row.getSubmitterUserId())
                    ? maskName(row.getSubmitterNameSnapshot()) : row.getSubmitterNameSnapshot());
            result.setAttachments(attachmentMapper.listByFeedback(row.getId()).stream().map(file ->
                    new LeadSubmitterFeedbackRespVO.Attachment(file.getFileId(), file.getOriginalName(),
                            file.getContentType(), file.getFileSize(), fileApi.getFileInfo(file.getFileId()) == null
                            ? null : fileApi.presignGetUrl(file.getFileId(), 600))).toList());
            return result;
        }).toList(), page.getTotal());
    }

    private String maskName(String name) { return name == null ? null : DesensitizedUtil.chineseName(name); }
    private LeadDO lockedLead(Long id) {
        LeadDO lead = leadMapper.selectByIdForUpdate(id, TenantContextHolder.getRequiredTenantId());
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        return lead;
    }
    private void requireActionable(LeadDO lead) {
        if (lead.getStatus() == null || Set.of(STATUS_INVALID, STATUS_CLOSED, STATUS_WON).contains(lead.getStatus()))
            throw exception(LEAD_FEEDBACK_STATE_INVALID);
    }
    private String directory(Long leadId, Long userId) {
        return "zsjos/lead-feedback/" + TenantContextHolder.getRequiredTenantId() + "/" + leadId + "/" + userId;
    }
    private LeadSubmitterFeedbackAttachmentDO requireTemporaryAttachment(Long fileId, Long leadId, Long userId) {
        var row = attachmentMapper.findFile(fileId);
        if (row == null || row.getFeedbackId() != null || !Objects.equals(row.getLeadId(), leadId)
                || !Objects.equals(row.getUploaderUserId(), userId) || row.getExpiresAt() == null
                || !row.getExpiresAt().isAfter(LocalDateTime.now())) throw exception(LEAD_FEEDBACK_ATTACHMENT_INVALID);
        FileInfoRespDTO file = fileApi.getFileInfo(fileId);
        if (file == null || !String.valueOf(userId).equals(file.getCreator()) || file.getPath() == null
                || !file.getPath().startsWith(directory(leadId, userId) + "/")
                || file.getType() == null || !FILE_TYPES.contains(file.getType()) || file.getSize() == null || file.getSize() > MAX_FILE_SIZE)
            throw exception(LEAD_FEEDBACK_ATTACHMENT_INVALID);
        return row;
    }
}
