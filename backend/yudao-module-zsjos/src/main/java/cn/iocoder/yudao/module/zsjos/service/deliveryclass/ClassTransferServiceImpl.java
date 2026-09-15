package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.ClassTransferRequestDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.ClassTransferRequestMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class ClassTransferServiceImpl implements ClassTransferService {
    public static final String PROCESS_DEFINITION_KEY = "zsjos_class_transfer";
    public static final String REVIEW_TASK_KEY = "originalSupervisorReview";
    @Resource private ClassTransferRequestMapper mapper;
    @Resource private ServiceRelationMapper relationMapper;
    @Resource private DeliveryClassMapper classMapper;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;
    @Resource private PermissionApi permissionApi;
    @Resource private BpmProcessInstanceApi processInstanceApi;
    @Resource private DeliveryClassServiceImpl deliveryClassService;

    @Override
    @ZsjosAudit(action = "class-transfer.create", targetType = "service-relation")
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "student-service", bizId = "#relationId", action = "class-transfer")
    public Long create(Long relationId, ClassTransferCreateReqVO req, Long userId) {
        ServiceRelationDO relation = relationMapper.selectByIdForUpdate(relationId,
                TenantContextHolder.getRequiredTenantId());
        if (relation == null || !List.of("active", "paused", "completed").contains(relation.getStatus())
                || !Objects.equals(relation.getVersion(), req.getVersion())) throw exception(CLASS_TRANSFER_INVALID);
        DeliveryClassDO source = classMapper.selectById(relation.getClassId());
        DeliveryClassDO target = classMapper.selectByIdForUpdate(req.getTargetClassId(), relation.getTenantId());
        if (source == null || Boolean.TRUE.equals(source.getSystemClass()) || target == null
                || Boolean.TRUE.equals(target.getSystemClass()) || !"SERVING".equals(target.getStatus())
                || Objects.equals(source.getId(), target.getId())
                || !Objects.equals(source.getCategoryId(), target.getCategoryId())) throw exception(CLASS_TRANSFER_INVALID);
        deliveryClassService.validateHomeroom(target.getHomeroomUserId());
        if (mapper.selectPendingByRelation(relationId) != null) throw exception(CLASS_TRANSFER_ACTIVE);
        AdminUserRespDTO applicant = adminUserApi.getUser(userId);
        DeptRespDTO dept = applicant == null || applicant.getDeptId() == null ? null : deptApi.getDept(applicant.getDeptId());
        Long reviewerId = dept == null ? null : dept.getLeaderUserId();
        AdminUserRespDTO reviewer = reviewerId == null ? null : adminUserApi.getUser(reviewerId);
        if (reviewer == null || Objects.equals(reviewerId, userId)
                || !Objects.equals(reviewer.getStatus(), CommonStatusEnum.ENABLE.getStatus())
                || !permissionApi.hasAnyPermissions(reviewerId, "bpm:task:update")) {
            throw exception(CLASS_TRANSFER_SUPERVISOR_INVALID);
        }
        ClassTransferRequestDO request = new ClassTransferRequestDO();
        request.setServiceRelationId(relationId); request.setServiceRelationVersion(relation.getVersion());
        request.setFromClassId(source.getId()); request.setFromClassNoSnapshot(source.getClassNo());
        request.setFromClassNameSnapshot(source.getClassName()); request.setTargetClassId(target.getId());
        request.setTargetClassNoSnapshot(target.getClassNo()); request.setTargetClassNameSnapshot(target.getClassName());
        request.setFromHomeroomUserId(source.getHomeroomUserId());
        request.setFromHomeroomUserNameSnapshot(source.getHomeroomUserNameSnapshot());
        request.setTargetHomeroomUserId(target.getHomeroomUserId());
        request.setTargetHomeroomUserNameSnapshot(target.getHomeroomUserNameSnapshot());
        request.setApplicantUserId(userId); request.setReviewerUserId(reviewerId);
        request.setReason(req.getReason().trim()); request.setStatus("pending");
        request.setSubmittedAt(LocalDateTime.now()); request.setVersion(0); mapper.insert(request);
        BpmProcessInstanceCreateReqDTO process = new BpmProcessInstanceCreateReqDTO();
        process.setProcessDefinitionKey(PROCESS_DEFINITION_KEY);
        process.setBusinessKey("class-transfer:" + request.getId());
        process.setVariables(Map.of("classTransferRequestId", request.getId(), "serviceRelationId", relationId,
                "fromClassId", source.getId(), "targetClassId", target.getId(), "reviewerUserId", reviewerId));
        process.setStartUserSelectAssignees(Map.of(REVIEW_TASK_KEY, List.of(reviewerId)));
        try { request.setProcessInstanceId(processInstanceApi.createProcessInstance(userId, process)); }
        catch (RuntimeException unavailable) { throw exception(CLASS_TRANSFER_PROCESS_UNAVAILABLE); }
        mapper.updateById(request);
        return request.getId();
    }

    @Override
    public PageResult<ClassTransferRespVO> getMyPage(ClassTransferPageReqVO req, Long userId) {
        PageResult<ClassTransferRequestDO> page = mapper.selectMyPage(req, userId);
        return new PageResult<>(page.getList().stream().map(this::toVO).toList(), page.getTotal());
    }

    @Override
    public ClassTransferRespVO get(Long id, Long userId) {
        ClassTransferRequestDO request = mapper.selectById(id);
        if (request == null) throw exception(CLASS_TRANSFER_NOT_EXISTS);
        if (!Objects.equals(request.getApplicantUserId(), userId)) throw exception(DELIVERY_CLASS_PERMISSION_DENIED);
        return toVO(request);
    }

    @Override
    @ZsjosAudit(action = "class-transfer.process-result", targetType = "class-transfer")
    @Transactional(rollbackFor = Exception.class)
    public void handleProcessResult(String processInstanceId, Integer processStatus, String reason) {
        if (!BpmProcessInstanceStatusEnum.isProcessEndStatus(processStatus)) return;
        ClassTransferRequestDO found = mapper.selectByProcessInstanceId(processInstanceId);
        if (found == null) return;
        ClassTransferRequestDO request = mapper.selectByIdForUpdate(found.getId(), found.getTenantId());
        if (request == null || !"pending".equals(request.getStatus())) return;
        LocalDateTime now = LocalDateTime.now();
        if (BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(processStatus)) {
            ServiceRelationDO relation = relationMapper.selectByIdForUpdate(request.getServiceRelationId(), request.getTenantId());
            DeliveryClassDO source = classMapper.selectById(request.getFromClassId());
            DeliveryClassDO target = classMapper.selectByIdForUpdate(request.getTargetClassId(), request.getTenantId());
            AdminUserRespDTO targetOwner = target == null || target.getHomeroomUserId() == null ? null
                    : adminUserApi.getUser(target.getHomeroomUserId());
            boolean targetOwnerEligible = targetOwner != null
                    && Objects.equals(targetOwner.getStatus(), CommonStatusEnum.ENABLE.getStatus())
                    && permissionApi.hasAnyPermissions(targetOwner.getId(), DeliveryClassService.PERMISSION_QUERY_MY)
                    && permissionApi.hasAnyPermissions(targetOwner.getId(), "zsjos:student:query-my");
            boolean stale = relation == null || !List.of("active", "paused", "completed").contains(relation.getStatus())
                    || !Objects.equals(relation.getClassId(), request.getFromClassId())
                    || !Objects.equals(relation.getOwnerUserId(), request.getFromHomeroomUserId())
                    || !Objects.equals(relation.getVersion(), request.getServiceRelationVersion())
                    || source == null || Boolean.TRUE.equals(source.getSystemClass())
                    || target == null || !"SERVING".equals(target.getStatus()) || Boolean.TRUE.equals(target.getSystemClass())
                    || !Objects.equals(source.getCategoryId(), target.getCategoryId())
                    || !Objects.equals(target.getHomeroomUserId(), request.getTargetHomeroomUserId())
                    || !targetOwnerEligible;
            if (stale) {
                finish(request, "invalidated", "班级或服务归属已发生变化", now);
                return;
            }
            deliveryClassService.transferApproved(relation, target,
                    "class-transfer-approved:" + request.getId(), request.getReason());
            finish(request, "approved", reason, now);
        } else if (BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(processStatus)) {
            finish(request, "rejected", reason, now);
        } else {
            finish(request, "cancelled", reason, now);
        }
    }

    private void finish(ClassTransferRequestDO request, String status, String reason, LocalDateTime now) {
        request.setStatus(status); request.setResolutionReason(reason); request.setFinishedAt(now);
        request.setVersion(request.getVersion() + 1); mapper.updateById(request);
    }

    private ClassTransferRespVO toVO(ClassTransferRequestDO row) {
        ClassTransferRespVO vo = BeanUtils.toBean(row, ClassTransferRespVO.class);
        vo.setFromClassNo(row.getFromClassNoSnapshot()); vo.setFromClassName(row.getFromClassNameSnapshot());
        vo.setTargetClassNo(row.getTargetClassNoSnapshot()); vo.setTargetClassName(row.getTargetClassNameSnapshot());
        vo.setFromHomeroomUserName(row.getFromHomeroomUserNameSnapshot());
        vo.setTargetHomeroomUserName(row.getTargetHomeroomUserNameSnapshot());
        return vo;
    }
}
