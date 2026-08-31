package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadBatchActionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate.SubordinateBatchResultVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

/** Batch facade for the Lead management boundary. Each command remains the authority for its own rules. */
@Service
public class LeadBatchActionService {
    private static final Set<String> ACTIONS = Set.of(
            "transfer", "restore", "recycle", "release-claim-pool", "release-public-sea");
    private static final String QUALIFICATION_PERMISSION = "zsjos:lead:qualification:manage";

    @Resource private LeadMapper leadMapper;
    @Resource private SecurityFrameworkService securityFrameworkService;
    @Resource private LeadAssignmentService assignmentService;
    @Resource private LeadOwnerCommandService ownerCommandService;
    @Resource private SubordinateSalesCommandService supervisorCommandService;
    @Resource private SubordinateSalesCommandService commandLedger;

    public SubordinateBatchResultVO execute(String action, LeadBatchActionReqVO request, Long userId) {
        if (!ACTIONS.contains(action)) throw exception(SUBORDINATE_LEAD_STATE_INVALID);
        String reason = request.getReason().trim();
        LinkedHashSet<Long> ids = new LinkedHashSet<>(request.getLeadIds());
        if (ids.size() > 100) throw exception(SUBORDINATE_SALES_REASON_REQUIRED);
        validatePermission(action);
        validateTargets(action, request);

        String commandAction = "lead-batch-" + action;
        String fingerprint = SubordinateSalesCommandService.fingerprint(
                commandAction, ids, request.getTargetUserId(), request.getCollaboratorUserId(), reason);
        SubordinateBatchResultVO replay = commandLedger.beginBatch(commandAction, userId,
                request.getIdempotencyKey(), fingerprint);
        if (replay != null) return replay;

        Map<Long, LeadDO> leads = leadMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(LeadDO::getId, item -> item));
        List<SubordinateBatchResultVO.ItemVO> items = new ArrayList<>();
        for (Long leadId : ids) {
            LeadDO lead = leads.get(leadId);
            try {
                if (lead == null) throw exception(LEAD_NOT_EXISTS);
                executeOne(action, leadId, userId, request, reason,
                        request.getIdempotencyKey() + ":" + leadId);
                items.add(new SubordinateBatchResultVO.ItemVO(leadId, lead.getLeadNo(), true,
                        "SUCCESS", "操作成功"));
            } catch (ServiceException ex) {
                items.add(new SubordinateBatchResultVO.ItemVO(leadId, lead == null ? null : lead.getLeadNo(),
                        false, String.valueOf(ex.getCode()), ex.getMessage()));
            } catch (RuntimeException ex) {
                items.add(new SubordinateBatchResultVO.ItemVO(leadId, lead == null ? null : lead.getLeadNo(),
                        false, "INTERNAL_ERROR", "操作失败，请刷新后重试"));
            }
        }
        SubordinateBatchResultVO result = new SubordinateBatchResultVO();
        result.setItems(items);
        result.setSuccessCount((int) items.stream().filter(item -> Boolean.TRUE.equals(item.getSuccess())).count());
        result.setFailureCount(items.size() - result.getSuccessCount());
        commandLedger.completeBatch(userId, request.getIdempotencyKey(), result);
        return result;
    }

    private void validatePermission(String action) {
        boolean qualification = securityFrameworkService.hasPermission(QUALIFICATION_PERMISSION);
        boolean owner = securityFrameworkService.hasPermission(PERMISSION_OWNER_TRANSFER)
                || securityFrameworkService.hasPermission(PERMISSION_OWNER_RELEASE_PUBLIC_SEA);
        if (qualification || hasSupervisorPermission(action)) return;
        if ("transfer".equals(action) && owner && securityFrameworkService.hasPermission(PERMISSION_OWNER_TRANSFER)) return;
        if ("release-public-sea".equals(action) && owner
                && securityFrameworkService.hasPermission(PERMISSION_OWNER_RELEASE_PUBLIC_SEA)) return;
        throw exception(LEAD_PERMISSION_DENIED);
    }

    private boolean hasSupervisorPermission(String action) {
        return switch (action) {
            case "transfer" -> securityFrameworkService.hasPermission(PERMISSION_SUPERVISOR_TRANSFER);
            case "restore" -> securityFrameworkService.hasPermission(PERMISSION_SUPERVISOR_RESTORE);
            case "recycle" -> securityFrameworkService.hasPermission(PERMISSION_SUPERVISOR_RECYCLE);
            case "release-claim-pool" -> securityFrameworkService.hasPermission(PERMISSION_SUPERVISOR_RELEASE_CLAIM_POOL);
            case "release-public-sea" -> securityFrameworkService.hasPermission(PERMISSION_SUPERVISOR_RELEASE_PUBLIC_SEA);
            default -> false;
        };
    }

    private void validateTargets(String action, LeadBatchActionReqVO request) {
        if ("transfer".equals(action)) {
            if (request.getTargetUserId() == null || !isEligible(request.getTargetUserId())) {
                throw exception(SUBORDINATE_SALES_TARGET_INVALID);
            }
        }
        if ("release-public-sea".equals(action) && request.getCollaboratorUserId() != null
                && !isEligible(request.getCollaboratorUserId())) {
            throw exception(SUBORDINATE_SALES_TARGET_INVALID);
        }
    }

    private boolean isEligible(Long userId) {
        return assignmentService.getEligibleSalesUsers().stream()
                .anyMatch(item -> userId.equals(item.getId()));
    }

    private void executeOne(String action, Long leadId, Long userId, LeadBatchActionReqVO request,
                            String reason, String key) {
        boolean supervisor = securityFrameworkService.hasPermission(QUALIFICATION_PERMISSION)
                || hasSupervisorPermission(action);
        if (supervisor) {
            switch (action) {
                case "transfer" -> supervisorCommandService.transferOne(leadId, request.getTargetUserId(), userId, reason, key);
                case "restore" -> supervisorCommandService.restoreOne(leadId, userId, reason, key);
                case "recycle" -> supervisorCommandService.recycleOne(leadId, userId, reason, key);
                case "release-claim-pool" -> supervisorCommandService.releaseClaimPoolOne(leadId, userId, reason, key);
                case "release-public-sea" -> supervisorCommandService.releasePublicSeaOne(
                        leadId, request.getCollaboratorUserId(), userId, reason, key);
                default -> throw exception(SUBORDINATE_LEAD_STATE_INVALID);
            }
            return;
        }
        if ("transfer".equals(action)) {
            ownerCommandService.transfer(leadId, request.getTargetUserId(), userId, reason, key);
        } else if ("release-public-sea".equals(action)) {
            ownerCommandService.releaseToPublicSea(leadId, userId, reason, key);
        } else {
            throw exception(LEAD_PERMISSION_DENIED);
        }
    }
}
