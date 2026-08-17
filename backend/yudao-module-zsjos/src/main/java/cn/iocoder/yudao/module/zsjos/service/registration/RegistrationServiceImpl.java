package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderItemDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderItemMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.*;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.module.zsjos.service.registration.RegistrationConstants.*;

@Service
public class RegistrationServiceImpl implements RegistrationService {
    @Resource private RegistrationCaseMapper caseMapper;
    @Resource private RegistrationCaseChecklistItemMapper caseItemMapper;
    @Resource private RegistrationChecklistTemplateMapper templateMapper;
    @Resource private RegistrationChecklistTemplateItemMapper templateItemMapper;
    @Resource private RegistrationItemMapper registrationItemMapper;
    @Resource private ServiceRelationMapper serviceRelationMapper;
    @Resource private RegistrationCommandMapper commandMapper;
    @Resource private SalesOrderMapper orderMapper;
    @Resource private SalesOrderItemMapper orderItemMapper;
    @Resource private PersonMapper personMapper;
    @Resource private LeadMapper leadMapper;
    @Resource private RoleApi roleApi;
    @Resource private PermissionApi permissionApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private RegistrationNotifyPublisher registrationNotifyPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long ensureCaseAfterRegistrationApproval(Long orderId, LocalDateTime approvedAt) {
        RegistrationCaseDO existing = caseMapper.selectByOrderId(orderId);
        if (existing != null) return existing.getId();
        RegistrationChecklistTemplateDO template = templateMapper.selectCurrent();
        if (template == null || template.getPublishedVersionId() == null) {
            throw exception(REGISTRATION_CHECKLIST_CONFIG_INVALID);
        }
        List<RegistrationChecklistTemplateItemDO> definitions = templateItemMapper
                .selectByVersionId(template.getPublishedVersionId()).stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled())).toList();
        if (definitions.stream().noneMatch(item -> ITEM_TYPE_STUDY_PLANNER.equals(item.getItemType()))) {
            throw exception(REGISTRATION_CHECKLIST_CONFIG_INVALID);
        }
        RegistrationCaseDO registrationCase = new RegistrationCaseDO();
        registrationCase.setOrderId(orderId); registrationCase.setStatus(STATUS_PENDING);
        registrationCase.setChecklistVersionId(template.getPublishedVersionId());
        registrationCase.setRegistrationApprovedAt(approvedAt); registrationCase.setVersion(0);
        try {
            caseMapper.insert(registrationCase);
        } catch (DuplicateKeyException duplicate) {
            RegistrationCaseDO raced = caseMapper.selectByOrderId(orderId);
            if (raced == null) throw duplicate;
            return raced.getId();
        }
        for (RegistrationChecklistTemplateItemDO definition : definitions) {
            RegistrationCaseChecklistItemDO item = new RegistrationCaseChecklistItemDO();
            item.setRegistrationCaseId(registrationCase.getId()); item.setTemplateItemId(definition.getId());
            item.setItemKey(definition.getItemKey()); item.setItemType(definition.getItemType());
            item.setTitleSnapshot(definition.getTitle()); item.setSort(definition.getSort());
            item.setChecked(false); item.setVersion(0); caseItemMapper.insert(item);
        }
        SalesOrderDO order = orderMapper.selectById(orderId);
        registrationNotifyPublisher.publishTaskCreated(registrationCase, order);
        return registrationCase.getId();
    }

    @Override
    public PageResult<RegistrationCaseRespVO> getPoolPage(PageParam pageParam, String status, String keyword) {
        List<Long> matchedOrderIds = orderMapper.selectIdsByKeyword(keyword);
        PageResult<RegistrationCaseDO> page = caseMapper.selectPoolPage(pageParam, status, matchedOrderIds);
        return new PageResult<>(page.getList().stream().map(item -> convert(item, false)).toList(), page.getTotal());
    }

    @Override
    @ZsjosPermission(bizType = "registration-case", bizId = "#caseId", action = "read")
    public RegistrationCaseRespVO getCase(Long caseId) {
        RegistrationCaseDO registrationCase = caseMapper.selectById(caseId);
        if (registrationCase == null) throw exception(REGISTRATION_CASE_NOT_EXISTS);
        return convert(registrationCase, true);
    }

    @Override
    public List<StudyPlannerSimpleRespVO> getStudyPlannerCandidates() {
        RoleRespDTO role = roleApi.getRoleByCode(STUDY_PLANNER_ROLE_CODE);
        if (role == null || !CommonStatusEnum.ENABLE.getStatus().equals(role.getStatus())) return List.of();
        Set<Long> userIds = permissionApi.getUserRoleIdListByRoleIds(List.of(role.getId()));
        return adminUserApi.getUserList(userIds).stream()
                .filter(user -> CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()))
                .sorted(Comparator.comparing(AdminUserRespDTO::getNickname).thenComparing(AdminUserRespDTO::getId))
                .map(user -> new StudyPlannerSimpleRespVO(user.getId(), user.getNickname())).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "registration-case", bizId = "#caseId", action = "update")
    public RegistrationCaseRespVO updateChecklistItem(Long caseId, Long itemId, Long userId, RegistrationChecklistItemUpdateReqVO reqVO) {
        if (!beginCommand(caseId, userId, "update-item", reqVO.getIdempotencyKey(),
                itemId + ":" + reqVO.getChecked())) return getCaseForUpdateResult(caseId);
        RegistrationCaseDO registrationCase = lockEditable(caseId, reqVO.getVersion());
        RegistrationCaseChecklistItemDO item = caseItemMapper.selectById(itemId);
        if (item == null || !Objects.equals(item.getRegistrationCaseId(), caseId)) throw exception(REGISTRATION_ITEM_NOT_EXISTS);
        if (ITEM_TYPE_STUDY_PLANNER.equals(item.getItemType())) throw exception(REGISTRATION_CHECKLIST_ITEM_FIXED);
        LocalDateTime now = LocalDateTime.now();
        item.setChecked(reqVO.getChecked());
        item.setCheckedByUserId(Boolean.TRUE.equals(reqVO.getChecked()) ? userId : null);
        item.setCheckedAt(Boolean.TRUE.equals(reqVO.getChecked()) ? now : null);
        item.setVersion(item.getVersion() + 1); caseItemMapper.updateById(item);
        touch(registrationCase);
        return convert(registrationCase, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "registration-case", bizId = "#caseId", action = "update")
    public RegistrationCaseRespVO updateStudyPlanner(Long caseId, Long userId, RegistrationPlannerUpdateReqVO reqVO) {
        if (!beginCommand(caseId, userId, "update-planner", reqVO.getIdempotencyKey(),
                String.valueOf(reqVO.getStudyPlannerUserId()))) return getCaseForUpdateResult(caseId);
        RegistrationCaseDO registrationCase = lockEditable(caseId, reqVO.getVersion());
        if (getStudyPlannerCandidates().stream().noneMatch(item -> Objects.equals(item.getId(), reqVO.getStudyPlannerUserId()))) {
            throw exception(REGISTRATION_STUDY_PLANNER_INVALID);
        }
        registrationCase.setStudyPlannerUserId(reqVO.getStudyPlannerUserId());
        LocalDateTime now = LocalDateTime.now();
        RegistrationCaseChecklistItemDO plannerItem = caseItemMapper.selectByCaseId(caseId).stream()
                .filter(item -> ITEM_TYPE_STUDY_PLANNER.equals(item.getItemType())).findFirst()
                .orElseThrow(() -> exception(REGISTRATION_CHECKLIST_CONFIG_INVALID));
        plannerItem.setChecked(true); plannerItem.setCheckedByUserId(userId); plannerItem.setCheckedAt(now);
        plannerItem.setVersion(plannerItem.getVersion() + 1); caseItemMapper.updateById(plannerItem);
        touch(registrationCase);
        return convert(registrationCase, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "registration-case", bizId = "#caseId", action = "complete")
    public void complete(Long caseId, Long userId, RegistrationVersionReqVO reqVO) {
        if (!beginCommand(caseId, userId, "complete", reqVO.getIdempotencyKey(), "complete")) return;
        RegistrationCaseDO registrationCase = lockEditable(caseId, reqVO.getVersion());
        SalesOrderDO order = orderMapper.selectById(registrationCase.getOrderId());
        if (order == null) throw exception(REGISTRATION_ORDER_INVALID);
        if (STATUS_PENDING_APPROVAL.equals(order.getStatus())) throw exception(REGISTRATION_FINANCE_PENDING);
        if (STATUS_REVISION_REQUIRED.equals(order.getStatus())) throw exception(REGISTRATION_FINANCE_REVISION_REQUIRED);
        if (!STATUS_EFFECTIVE.equals(order.getStatus())) throw exception(REGISTRATION_ORDER_NOT_EFFECTIVE);
        List<RegistrationCaseChecklistItemDO> items = caseItemMapper.selectByCaseId(caseId);
        if (items.isEmpty() || items.stream().anyMatch(item -> !Boolean.TRUE.equals(item.getChecked()))) {
            throw exception(REGISTRATION_CHECKLIST_INCOMPLETE);
        }
        Long plannerId = registrationCase.getStudyPlannerUserId();
        if (plannerId == null || getStudyPlannerCandidates().stream().noneMatch(item -> Objects.equals(item.getId(), plannerId))) {
            throw exception(REGISTRATION_STUDY_PLANNER_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        for (RegistrationCaseChecklistItemDO item : items) {
            RegistrationItemDO fact = new RegistrationItemDO();
            fact.setRegistrationCaseId(caseId); fact.setChecklistItemId(item.getId()); fact.setItemType(item.getItemType());
            fact.setItemLabelSnapshot(item.getTitleSnapshot()); fact.setOccurredAt(item.getCheckedAt());
            fact.setRecordedAt(now); fact.setRecordedByUserId(item.getCheckedByUserId()); registrationItemMapper.insert(fact);
        }
        for (SalesOrderItemDO orderItem : orderItemMapper.selectListByOrderId(order.getId())) {
            ServiceRelationDO relation = new ServiceRelationDO();
            relation.setPersonId(order.getPersonId()); relation.setOrderId(order.getId()); relation.setOrderItemId(orderItem.getId());
            relation.setRegistrationCaseId(caseId); relation.setStatus("active"); relation.setOwnerUserId(plannerId);
            relation.setServiceSnapshot(orderItem.getProductSnapshot()); relation.setActivatedAt(now); relation.setVersion(0);
            serviceRelationMapper.insert(relation);
        }
        PersonDO person = personMapper.selectByIdForUpdate(order.getPersonId(), TenantContextHolder.getRequiredTenantId());
        if (person != null) { person.setIdentityStatus("student"); person.setLastSeenAt(now); person.setVersion(person.getVersion() + 1); personMapper.updateById(person); }
        registrationCase.setStatus(STATUS_COMPLETED); registrationCase.setCompletedByUserId(userId);
        registrationCase.setCompletedAt(now); registrationCase.setVersion(registrationCase.getVersion() + 1); caseMapper.updateById(registrationCase);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelByOrderId(Long orderId, String reason, LocalDateTime now) {
        RegistrationCaseDO registrationCase = caseMapper.selectByOrderId(orderId);
        if (registrationCase == null || STATUS_COMPLETED.equals(registrationCase.getStatus()) || STATUS_CANCELLED.equals(registrationCase.getStatus())) return;
        RegistrationCaseDO locked = caseMapper.selectByIdForUpdate(registrationCase.getId(), TenantContextHolder.getRequiredTenantId());
        locked.setStatus(STATUS_CANCELLED); locked.setCancelledAt(now); locked.setCancelReason(reason);
        locked.setVersion(locked.getVersion() + 1); caseMapper.updateById(locked);
    }

    private RegistrationCaseDO lockEditable(Long caseId, Integer expectedVersion) {
        RegistrationCaseDO registrationCase = caseMapper.selectByIdForUpdate(caseId, TenantContextHolder.getRequiredTenantId());
        if (registrationCase == null) throw exception(REGISTRATION_CASE_NOT_EXISTS);
        if (!Set.of(STATUS_PENDING, STATUS_PROCESSING).contains(registrationCase.getStatus())) throw exception(REGISTRATION_STATE_INVALID);
        if (!Objects.equals(registrationCase.getVersion(), expectedVersion)) throw exception(REGISTRATION_VERSION_CONFLICT);
        return registrationCase;
    }

    private void touch(RegistrationCaseDO registrationCase) {
        registrationCase.setStatus(STATUS_PROCESSING); registrationCase.setVersion(registrationCase.getVersion() + 1);
        caseMapper.updateById(registrationCase);
    }

    private RegistrationCaseRespVO getCaseForUpdateResult(Long caseId) {
        RegistrationCaseDO registrationCase = caseMapper.selectById(caseId);
        if (registrationCase == null) throw exception(REGISTRATION_CASE_NOT_EXISTS);
        return convert(registrationCase, true);
    }

    private boolean beginCommand(Long caseId, Long userId, String commandType, String idempotencyKey,
                                 String requestFingerprint) {
        RegistrationCommandDO command = new RegistrationCommandDO();
        command.setRegistrationCaseId(caseId); command.setOperatorUserId(userId);
        command.setCommandType(commandType); command.setIdempotencyKey(idempotencyKey);
        command.setRequestFingerprint(requestFingerprint);
        try {
            commandMapper.insert(command);
            return true;
        } catch (DuplicateKeyException duplicate) {
            RegistrationCommandDO existing = commandMapper.selectByIdempotencyKey(idempotencyKey);
            if (existing != null && Objects.equals(existing.getRegistrationCaseId(), caseId)
                    && Objects.equals(existing.getCommandType(), commandType)
                    && Objects.equals(existing.getRequestFingerprint(), requestFingerprint)) return false;
            throw exception(REGISTRATION_IDEMPOTENCY_CONFLICT);
        }
    }

    private RegistrationCaseRespVO convert(RegistrationCaseDO registrationCase, boolean details) {
        SalesOrderDO order = orderMapper.selectById(registrationCase.getOrderId());
        RegistrationCaseRespVO result = new RegistrationCaseRespVO();
        result.setId(registrationCase.getId()); result.setOrderId(registrationCase.getOrderId()); result.setStatus(registrationCase.getStatus());
        result.setStatusLabel(registrationStatusLabel(registrationCase.getStatus()));
        result.setStudyPlannerUserId(registrationCase.getStudyPlannerUserId()); result.setRegistrationApprovedAt(registrationCase.getRegistrationApprovedAt());
        result.setCompletedAt(registrationCase.getCompletedAt()); result.setVersion(registrationCase.getVersion());
        if (order != null) {
            result.setOrderNo(order.getOrderNo()); result.setOrderStatus(order.getStatus()); result.setOrderStatusLabel(orderStatusLabel(order.getStatus())); result.setStudentName(order.getStudentName());
            result.setStudentMobile(order.getStudentMobile()); LeadDO lead = order.getLeadId() == null ? null : leadMapper.selectById(order.getLeadId());
            result.setLeadNo(lead == null ? null : lead.getLeadNo());
        }
        if (registrationCase.getStudyPlannerUserId() != null) {
            AdminUserRespDTO planner = adminUserApi.getUser(registrationCase.getStudyPlannerUserId());
            result.setStudyPlannerUserName(planner == null ? null : planner.getNickname());
        }
        if (details) {
            List<RegistrationCaseChecklistItemDO> items = caseItemMapper.selectByCaseId(registrationCase.getId());
            Set<Long> actorIds = items.stream().map(RegistrationCaseChecklistItemDO::getCheckedByUserId).filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, AdminUserRespDTO> actors = actorIds.isEmpty() ? Map.of() : adminUserApi.getUserMap(actorIds);
            result.setItems(items.stream().map(item -> {
                RegistrationCaseRespVO.ItemVO row = new RegistrationCaseRespVO.ItemVO();
                row.setId(item.getId()); row.setItemKey(item.getItemKey()); row.setItemType(item.getItemType());
                row.setTitle(item.getTitleSnapshot()); row.setSort(item.getSort()); row.setChecked(item.getChecked());
                row.setCheckedByUserId(item.getCheckedByUserId()); row.setCheckedAt(item.getCheckedAt());
                AdminUserRespDTO actor = item.getCheckedByUserId() == null ? null : actors.get(item.getCheckedByUserId());
                row.setCheckedByUserName(actor == null ? null : actor.getNickname()); return row;
            }).toList());
            applyCompletionState(result, registrationCase, order);
        }
        return result;
    }

    private void applyCompletionState(RegistrationCaseRespVO result, RegistrationCaseDO registrationCase, SalesOrderDO order) {
        String code = null;
        String reason = null;
        if (order == null) {
            code = COMPLETION_BLOCK_ORDER_NOT_EFFECTIVE; reason = "关联订单不存在，暂时无法完成报名履约";
        } else if (STATUS_PENDING_APPROVAL.equals(order.getStatus())) {
            code = COMPLETION_BLOCK_FINANCE_PENDING; reason = "财务审核通过后才能完成报名履约";
        } else if (STATUS_REVISION_REQUIRED.equals(order.getStatus())) {
            code = COMPLETION_BLOCK_FINANCE_REVISION_REQUIRED; reason = "财务审核未通过，订单补正并重新审核通过后才能完成报名履约";
        } else if (!STATUS_EFFECTIVE.equals(order.getStatus())) {
            code = COMPLETION_BLOCK_ORDER_NOT_EFFECTIVE; reason = "订单尚未生效，暂时无法完成报名履约";
        } else if (result.getItems() == null || result.getItems().isEmpty()
                || result.getItems().stream().anyMatch(item -> !Boolean.TRUE.equals(item.getChecked()))) {
            code = COMPLETION_BLOCK_CHECKLIST_INCOMPLETE; reason = "请先完成全部报名履约清单项";
        } else if (registrationCase.getStudyPlannerUserId() == null) {
            code = COMPLETION_BLOCK_PLANNER_REQUIRED; reason = "请先选择学习规划师";
        } else if (getStudyPlannerCandidates().stream().noneMatch(item -> Objects.equals(item.getId(), registrationCase.getStudyPlannerUserId()))) {
            code = COMPLETION_BLOCK_PLANNER_INVALID; reason = "当前学习规划师已失效，请重新选择";
        }
        result.setCompletable(code == null);
        result.setCompletionBlockCode(code);
        result.setCompletionBlockReason(reason);
    }

    private String registrationStatusLabel(String status) {
        return switch (status) {
            case STATUS_PENDING -> "待处理";
            case STATUS_PROCESSING -> "处理中";
            case STATUS_COMPLETED -> "已完成";
            case STATUS_CANCELLED -> "已取消";
            default -> "未知状态";
        };
    }

    private String orderStatusLabel(String status) {
        return switch (status) {
            case STATUS_PENDING_APPROVAL -> "待财务审核";
            case STATUS_REVISION_REQUIRED -> "财务驳回待补正";
            case STATUS_EFFECTIVE -> "已生效";
            case STATUS_SUPERSEDED -> "已被替代";
            case STATUS_TERMINATED -> "已终止";
            default -> "未知状态";
        };
    }
}
