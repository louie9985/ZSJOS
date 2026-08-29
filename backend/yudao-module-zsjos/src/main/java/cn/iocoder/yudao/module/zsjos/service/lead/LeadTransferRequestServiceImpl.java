package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.agingpool.LeadTransferRequestCreateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAgingPoolCycleDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadTransferRequestDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAgingPoolCycleMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadTransferRequestMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.STATUS_PENDING_APPROVAL;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
@Slf4j
public class LeadTransferRequestServiceImpl implements LeadTransferRequestService {
    public static final String PROCESS_DEFINITION_KEY = "zsjos_lead_transfer_request";
    private static final String TASK_DEFINITION_KEY = "ownerManagerReview";
    @Resource private LeadTransferRequestMapper requestMapper;
    @Resource private LeadAgingPoolCycleMapper cycleMapper;
    @Resource private LeadMapper leadMapper;
    @Resource private LeadAgingPoolService agingPoolService;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;
    @Resource private BpmProcessInstanceApi processInstanceApi;
    @Resource private LeadDispatchService dispatchService;
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper orderMapper;
    @Resource private LeadNotifyEventPublisher notifyEventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long cycleId, Long requesterUserId, LeadTransferRequestCreateReqVO request) {
        LeadAgingPoolCycleDO cycleSnapshot = cycleMapper.selectById(cycleId);
        if (cycleSnapshot == null) {
            throw exception(LEAD_AGING_POOL_STATE_INVALID);
        }
        LeadAgingPoolCycleDO cycle = cycleMapper.selectByIdForUpdate(cycleId,
                TenantContextHolder.getRequiredTenantId());
        if (cycle == null || !Objects.equals(cycle.getLeadId(), cycleSnapshot.getLeadId())) {
            throw exception(LEAD_AGING_POOL_STATE_INVALID);
        }
        Long leadId = cycle.getLeadId();
        LeadDO lead = leadMapper.selectByIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        if (!agingPoolService.canRead(cycle, requesterUserId)) throw exception(LEAD_PERMISSION_DENIED);
        if (!Objects.equals(cycle.getCollaboratorUserId(), requesterUserId)) {
            throw exception(LEAD_PERMISSION_DENIED);
        }
        LeadTransferRequestDO replay = requestMapper.selectByIdempotencyKey(request.getIdempotencyKey());
        if (replay != null) {
            if (!Objects.equals(replay.getLeadId(), leadId)
                    || !Objects.equals(replay.getRequestedOwnerUserId(), requesterUserId)) {
                throw exception(LEAD_AGING_POOL_IDEMPOTENCY_CONFLICT);
            }
            return replay.getId();
        }
        if (!Set.of(AGING_POOL_WAITING_ASSIGNMENT, AGING_POOL_ASSIGNED,
                AGING_POOL_DEAL_PENDING).contains(cycle.getStatus())) {
            throw exception(LEAD_AGING_POOL_STATE_INVALID);
        }
        if (Objects.equals(lead.getOwnerUserId(), requesterUserId)) throw exception(LEAD_AGING_POOL_STATE_INVALID);
        if (orderMapper.selectActiveByLeadId(leadId, List.of(STATUS_PENDING_APPROVAL)) != null
                || requestMapper.selectActiveByLeadId(leadId) != null) throw exception(LEAD_AGING_POOL_STATE_INVALID);
        AdminUserRespDTO owner = adminUserApi.getUser(lead.getOwnerUserId());
        AdminUserRespDTO requester = adminUserApi.getUser(requesterUserId);
        if (owner == null || requester == null || !Objects.equals(owner.getDeptId(), requester.getDeptId())) {
            throw exception(LEAD_AGING_POOL_SALES_INVALID);
        }
        DeptRespDTO dept = deptApi.getDept(owner.getDeptId());
        if (dept == null || dept.getLeaderUserId() == null) throw exception(LEAD_AGING_POOL_MANAGER_DENIED);
        LeadTransferRequestDO record = new LeadTransferRequestDO();
        record.setLeadId(leadId); record.setFromOwnerUserId(lead.getOwnerUserId());
        record.setRequestedOwnerUserId(requesterUserId); record.setOwnerDeptIdSnapshot(owner.getDeptId());
        record.setTransferReviewerUserId(dept.getLeaderUserId());
        record.setReason(request.getReason().trim()); record.setStatus("pending");
        record.setIdempotencyKey(request.getIdempotencyKey()); record.setSubmittedAt(LocalDateTime.now());
        try {
            requestMapper.insert(record);
        } catch (DuplicateKeyException duplicate) {
            LeadTransferRequestDO concurrentReplay = requestMapper.selectByIdempotencyKeyForUpdate(
                    request.getIdempotencyKey(), TenantContextHolder.getRequiredTenantId());
            if (concurrentReplay == null) throw duplicate;
            if (!Objects.equals(concurrentReplay.getLeadId(), leadId)
                    || !Objects.equals(concurrentReplay.getRequestedOwnerUserId(), requesterUserId)) {
                throw exception(LEAD_AGING_POOL_IDEMPOTENCY_CONFLICT);
            }
            return concurrentReplay.getId();
        }
        BpmProcessInstanceCreateReqDTO process = new BpmProcessInstanceCreateReqDTO();
        process.setProcessDefinitionKey(PROCESS_DEFINITION_KEY);
        process.setBusinessKey("lead-transfer:" + record.getId());
        Map<String, Object> processVariables = new LinkedHashMap<>();
        processVariables.put("requestId", record.getId());
        processVariables.put("leadId", leadId);
        processVariables.put("leadNo", lead.getLeadNo());
        processVariables.put("fromOwnerUserId", lead.getOwnerUserId());
        processVariables.put("requestedOwnerUserId", requesterUserId);
        process.setVariables(processVariables);
        process.setStartUserSelectAssignees(Map.of(TASK_DEFINITION_KEY, List.of(dept.getLeaderUserId())));
        try {
            record.setProcessInstanceId(processInstanceApi.createProcessInstance(requesterUserId, process));
        } catch (RuntimeException ex) {
            log.error("[create][leadId({}) requestId({}) processDefinitionKey({}) taskDefinitionKey({}) BPM process start failed]",
                    leadId, record.getId(), PROCESS_DEFINITION_KEY, TASK_DEFINITION_KEY, ex);
            throw exception(LEAD_TRANSFER_PROCESS_UNAVAILABLE);
        }
        requestMapper.updateById(record);
        notifyEventPublisher.publish(TRANSFER_REQUESTED, leadId, "lead-transfer-requested:" + record.getId(),
                requesterUserId, record.getSubmittedAt(), Map.of(
                        "transferReviewerUserId", record.getTransferReviewerUserId(),
                        "requesterUserId", requesterUserId, "ownerUserId", record.getFromOwnerUserId(),
                        "transfer.reason", record.getReason()));
        return record.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleProcessResult(String processInstanceId, Integer processStatus, String reason) {
        if (!BpmProcessInstanceStatusEnum.isProcessEndStatus(processStatus)) return;
        LeadTransferRequestDO request = requestMapper.selectByProcessInstanceIdForUpdate(
                processInstanceId, TenantContextHolder.getRequiredTenantId());
        if (request == null || !"pending".equals(request.getStatus())) return;
        LocalDateTime now = LocalDateTime.now();
        if (BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(processStatus)) {
            LeadDispatchService.TransferAttemptResult transferResult = orderMapper.selectActiveByLeadId(
                    request.getLeadId(), List.of(STATUS_PENDING_APPROVAL)) == null
                    ? dispatchService.tryAdminTransfer(request.getLeadId(), request.getFromOwnerUserId(),
                    request.getRequestedOwnerUserId(), request.getRequestedOwnerUserId(),
                    "同团队销售转派申请审批通过：" + request.getReason())
                    : LeadDispatchService.TransferAttemptResult.invalidated("客资已有活动订单");
            if (transferResult.transferred()) {
                request.setStatus("approved");
            } else {
                request.setStatus("invalidated");
                request.setResolvedAt(now); request.setResolutionReason(transferResult.reason());
                requestMapper.updateById(request);
                notifyEventPublisher.publish(TRANSFER_REQUEST_INVALIDATED, request.getLeadId(),
                        "lead-transfer-invalidated:" + request.getId(), request.getRequestedOwnerUserId(), now,
                        Map.of("requesterUserId", request.getRequestedOwnerUserId(),
                                "transfer.reason", request.getReason(), "transfer.resolutionReason", request.getResolutionReason()));
                return;
            }
        } else {
            request.setStatus(BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(processStatus)
                    ? "rejected" : "cancelled");
        }
        request.setResolvedAt(now); request.setResolutionReason(reason);
        requestMapper.updateById(request);
        String scene = "approved".equals(request.getStatus()) ? TRANSFER_REQUEST_APPROVED
                : "rejected".equals(request.getStatus()) ? TRANSFER_REQUEST_REJECTED : TRANSFER_REQUEST_INVALIDATED;
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("requesterUserId", request.getRequestedOwnerUserId());
        payload.put("previousOwnerUserId", request.getFromOwnerUserId());
        payload.put("newOwnerUserId", request.getRequestedOwnerUserId());
        payload.put("transfer.reason", request.getReason());
        payload.put("transfer.resolutionReason", request.getResolutionReason());
        notifyEventPublisher.publish(scene, request.getLeadId(), "lead-transfer-result:" + request.getId(),
                request.getRequestedOwnerUserId(), now, payload);
    }
}
