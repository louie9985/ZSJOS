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
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadTransferRequestDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadTransferRequestMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.STATUS_PENDING_APPROVAL;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
@Slf4j
public class LeadTransferRequestServiceImpl implements LeadTransferRequestService {
    public static final String PROCESS_DEFINITION_KEY = "zsjos_lead_transfer_request";
    private static final String TASK_DEFINITION_KEY = "ownerManagerReview";
    @Resource private LeadTransferRequestMapper requestMapper;
    @Resource private LeadMapper leadMapper;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;
    @Resource private BpmProcessInstanceApi processInstanceApi;
    @Resource private LeadDispatchService dispatchService;
    @Resource private cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper orderMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(Long leadId, Long requesterUserId, LeadTransferRequestCreateReqVO request) {
        LeadTransferRequestDO replay = requestMapper.selectByIdempotencyKey(request.getIdempotencyKey());
        if (replay != null) return replay.getId();
        LeadDO lead = leadMapper.selectByIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
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
        record.setReason(request.getReason().trim()); record.setStatus("pending");
        record.setIdempotencyKey(request.getIdempotencyKey()); record.setSubmittedAt(LocalDateTime.now());
        requestMapper.insert(record);
        BpmProcessInstanceCreateReqDTO process = new BpmProcessInstanceCreateReqDTO();
        process.setProcessDefinitionKey(PROCESS_DEFINITION_KEY);
        process.setBusinessKey("lead-transfer:" + record.getId());
        process.setVariables(Map.of("requestId", record.getId(), "leadId", leadId,
                "fromOwnerUserId", lead.getOwnerUserId(), "requestedOwnerUserId", requesterUserId));
        process.setStartUserSelectAssignees(Map.of(TASK_DEFINITION_KEY, List.of(dept.getLeaderUserId())));
        try {
            record.setProcessInstanceId(processInstanceApi.createProcessInstance(requesterUserId, process));
        } catch (RuntimeException ex) {
            log.error("[create][leadId({}) requestId({}) processDefinitionKey({}) taskDefinitionKey({}) BPM process start failed]",
                    leadId, record.getId(), PROCESS_DEFINITION_KEY, TASK_DEFINITION_KEY, ex);
            throw exception(LEAD_TRANSFER_PROCESS_UNAVAILABLE);
        }
        requestMapper.updateById(record);
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
            dispatchService.adminTransfer(request.getLeadId(), request.getRequestedOwnerUserId(),
                    request.getRequestedOwnerUserId(), "同团队销售转派申请审批通过：" + request.getReason());
            request.setStatus("approved");
        } else {
            request.setStatus(BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(processStatus)
                    ? "rejected" : "cancelled");
        }
        request.setResolvedAt(now); request.setResolutionReason(reason);
        requestMapper.updateById(request);
    }
}
