package cn.iocoder.yudao.module.zsjos.service.pointsredemption;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.*;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.pointsredemption.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.pointsredemption.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.pointsredemption.*;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PointsRequestService {
    public static final String PERM = "zsjos:points-redemption:";
    @Resource private PointsRequestMapper requests;
    @Resource private PointsPackageMapper packages;
    @Resource private PointsApprovalPolicy approval;
    @Resource private PointsPlatformClient platform;
    @Resource private PointsCrypto crypto;
    @Resource private BpmProcessInstanceApi instances;
    @Resource private BpmProcessProgressApi progress;
    @Resource private AdminUserApi users;
    @Resource private PermissionApi permissions;
    @Resource private FileApi files;
    @Resource private cn.iocoder.yudao.module.zsjos.service.audit.BusinessAuditService audit;

    @Transactional(rollbackFor = Exception.class)
    public Long create(PointsCreateReqVO req, Long userId) {
        // Serializes duplicate submissions for this package before any process starts.
        var pack = packages.lock(req.getPackageId(), TenantContextHolder.getRequiredTenantId());
        var previous = requests.selectOne(new LambdaQueryWrapperX<PointsRequestDO>()
                .eq(PointsRequestDO::getApplicantUserId, userId).eq(PointsRequestDO::getSubmissionKey, req.getSubmissionKey()));
        if (previous != null) {
            var oldIds = JsonUtils.parseArray(previous.getAttachmentsJson(), PointsRequestRespVO.Attachment.class)
                    .stream().map(PointsRequestRespVO.Attachment::getId).toList();
            if (!Objects.equals(previous.getPackageId(), req.getPackageId())
                    || !Objects.equals(previous.getReason(), req.getReason().trim()) || !oldIds.equals(req.getAttachmentIds()))
                throw PointsErrors.invalid("重复提交标识对应的申请内容不同，请刷新后重试");
            return previous.getId();
        }
        if (pack == null || !Boolean.TRUE.equals(pack.getEnabled())) throw PointsErrors.invalid("积分套餐不存在或已停用");
        platform.requireConfigured(); crypto.validate();
        var definition = approval.validate(userId);
        List<PointsRequestRespVO.Attachment> attachments = new ArrayList<>();
        for (Long id : req.getAttachmentIds()) {
            var file = files.getFileInfo(id);
            if (file == null || file.getPath() == null || !file.getPath().startsWith("zsjos/points-redemption/" + userId + "/")
                    || !String.valueOf(userId).equals(file.getCreator())) throw PointsErrors.invalid("附件不存在或不属于当前申请人");
            var attachment = BeanUtils.toBean(file, PointsRequestRespVO.Attachment.class);
            attachment.setUrl(null); attachments.add(attachment);
        }
        PointsRequestDO row = new PointsRequestDO();
        row.setRequestNo("JF" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        row.setSubmissionKey(req.getSubmissionKey()); row.setApplicantUserId(userId);
        row.setApplicantName(users.getUser(userId).getNickname());
        row.setPackageId(pack.getId()); row.setPackageName(pack.getName());
        row.setCreditsAmount(pack.getCreditsAmount()); row.setValidDays(pack.getValidDays());
        row.setReason(req.getReason().trim()); row.setAttachmentsJson(JsonUtils.toJsonString(attachments));
        row.setApprovalStatus("pending"); row.setIssueStatus("waiting");
        row.setSubmittedAt(LocalDateTime.now()); row.setAttempts(0); row.setNotified(false);
        row.setOrderId("ZSJOS-POINTS-" + UUID.randomUUID());
        row.setProcessDefinitionId(definition.getId());
        row.setApproverIdsJson(JsonUtils.toJsonString(approval.approverIds(definition, userId)));
        row.setTaskKeysJson(JsonUtils.toJsonString(PointsApprovalPolicy.orderedTasks(definition).stream().map(t -> t.getKey()).toList()));
        row.setProcessInstanceId(UUID.randomUUID().toString());
        requests.insert(row);
        var process = new BpmProcessInstanceCreateReqDTO();
        process.setProcessDefinitionKey(PointsApprovalPolicy.PROCESS_KEY); process.setProcessDefinitionId(definition.getId());
        process.setPredefinedProcessInstanceId(row.getProcessInstanceId());
        process.setBusinessKey("points-redemption:" + row.getId());
        process.setVariables(Map.of("requestNo", row.getRequestNo(), "packageName", row.getPackageName(),
                "creditsAmount", row.getCreditsAmount(), "reason", row.getReason()));
        instances.createProcessInstance(userId, process);
        return row.getId();
    }

    public PageResult<PointsRequestRespVO> page(PageParam page, Long userId, boolean management) {
        if (management && !permissions.hasAnyPermissions(userId, PERM + "manage")) throw PointsErrors.denied();
        var query = new LambdaQueryWrapperX<PointsRequestDO>().eqIfPresent(PointsRequestDO::getApplicantUserId, management ? null : userId)
                .orderByDesc(PointsRequestDO::getId);
        var result = requests.selectPage(page, query);
        return new PageResult<>(result.getList().stream().map(r -> view(r, userId, false)).toList(), result.getTotal());
    }
    @ZsjosPermission(bizType="points-redemption", bizId="#id", action="read")
    public PointsRequestRespVO get(Long id, Long userId) { return view(require(id), userId, true); }

    @ZsjosPermission(bizType="points-redemption", bizId="#id", action="reveal")
    @ZsjosAudit(action="points-redemption.reveal", targetType="points-redemption")
    public String reveal(Long id) {
        var row = require(id);
        if (!"issued".equals(row.getIssueStatus()) || row.getCodeCiphertext() == null) throw PointsErrors.invalid("兑换码尚未生成");
        String value = crypto.decrypt(row.getCodeCiphertext());
        audit.record("points", "points-redemption.reveal", "points-redemption", String.valueOf(id),
                "ADMIN", Map.of("requestNo", row.getRequestNo()));
        return value;
    }
    @ZsjosPermission(bizType="points-redemption", bizId="#id", action="cancel")
    @Transactional(rollbackFor=Exception.class)
    public void cancel(Long id, Long userId) {
        var row = requests.lock(id, TenantContextHolder.getRequiredTenantId());
        if (row == null || !"pending".equals(row.getApprovalStatus())) throw PointsErrors.invalid("仅审批中的申请可取消");
        instances.cancelProcessInstanceByStartUser(userId, row.getProcessInstanceId(), "申请人取消积分兑换码申请");
    }
    @ZsjosPermission(bizType="points-redemption", bizId="#id", action="manage")
    @Transactional(rollbackFor=Exception.class)
    public void retry(Long id) {
        var row = requests.lock(id, TenantContextHolder.getRequiredTenantId());
        if (row == null || !"approved".equals(row.getApprovalStatus()) || !"failed".equals(row.getIssueStatus()))
            throw PointsErrors.invalid("仅审批通过且发码失败的申请可重试");
        platform.requireConfigured();
        row.setIssueStatus("queued"); row.setNextAttemptAt(LocalDateTime.now()); row.setAttempts(0);
        requests.updateById(row);
    }
    @Transactional(rollbackFor=Exception.class)
    public void processResult(String instanceId, Integer status) {
        if (!BpmProcessInstanceStatusEnum.isProcessEndStatus(status)) return;
        var found = requests.selectOne(new LambdaQueryWrapperX<PointsRequestDO>().eq(PointsRequestDO::getProcessInstanceId, instanceId));
        if (found == null) return;
        var row = requests.lock(found.getId(), TenantContextHolder.getRequiredTenantId());
        if (!"pending".equals(row.getApprovalStatus())) return;
        boolean approved = BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(status);
        row.setApprovalStatus(approved ? "approved" : BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(status) ? "rejected" : "cancelled");
        row.setIssueStatus(approved ? "queued" : "waiting");
        if (approved) row.setNextAttemptAt(LocalDateTime.now());
        requests.updateById(row);
    }
    public List<PointsPackageRespVO> packages(boolean management) {
        return packages.selectList(new LambdaQueryWrapperX<PointsPackageDO>()
                .eqIfPresent(PointsPackageDO::getEnabled, management ? null : true).orderByDesc(PointsPackageDO::getId))
                .stream().map(p -> BeanUtils.toBean(p, PointsPackageRespVO.class)).toList();
    }
    @Transactional(rollbackFor=Exception.class)
    public Long createPackage(PointsPackageSaveReqVO req) {
        if (req.getId() != null) throw PointsErrors.invalid("新套餐不能指定已有编号");
        var row = BeanUtils.toBean(req, PointsPackageDO.class);
        packages.insert(row);
        return row.getId();
    }
    @ZsjosPermission(bizType="points-package", bizId="#req.id", action="save")
    @Transactional(rollbackFor=Exception.class)
    public Long updatePackage(PointsPackageSaveReqVO req) {
        if (packages.lock(req.getId(), TenantContextHolder.getRequiredTenantId()) == null) throw PointsErrors.invalid("积分套餐不存在");
        // Explicit null clears a previously configured validity duration.
        packages.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PointsPackageDO>()
                .eq(PointsPackageDO::getId, req.getId()).set(PointsPackageDO::getName, req.getName())
                .set(PointsPackageDO::getCreditsAmount, req.getCreditsAmount()).set(PointsPackageDO::getEnabled, req.getEnabled())
                .set(PointsPackageDO::getValidDays, req.getValidDays()));
        return req.getId();
    }
    public FileInfoRespDTO upload(byte[] content, String name, String type, Long userId) {
        if (content.length == 0 || content.length > 20 * 1024 * 1024 || name == null
                || !name.toLowerCase(Locale.ROOT).matches(".*\\.(pdf|doc|docx|xls|xlsx|ppt|pptx|txt|png|jpg|jpeg|webp|zip)$"))
            throw PointsErrors.invalid("附件限20MB，支持常见办公文档、图片及ZIP");
        String safeName = name.replace('\\', '_').replace('/', '_');
        return files.createFileInfo(content, safeName, "zsjos/points-redemption/" + userId, type);
    }
    PointsRequestDO require(Long id) {
        var row = requests.selectById(id);
        if (row == null) throw PointsErrors.invalid("积分申请不存在");
        return row;
    }
    PointsRequestRespVO view(PointsRequestDO row, Long userId, boolean detail) {
        var vo = BeanUtils.toBean(row, PointsRequestRespVO.class);
        vo.setCanReveal(Objects.equals(row.getApplicantUserId(), userId)
                && permissions.hasAnyPermissions(userId, PERM + "reveal") && "issued".equals(row.getIssueStatus()));
        if (detail) {
            var attachments = JsonUtils.parseArray(row.getAttachmentsJson(), PointsRequestRespVO.Attachment.class);
            attachments.forEach(a -> a.setUrl(files.presignGetUrl(a.getId(), 300)));
            vo.setAttachments(attachments);
        }
        return vo;
    }
}
