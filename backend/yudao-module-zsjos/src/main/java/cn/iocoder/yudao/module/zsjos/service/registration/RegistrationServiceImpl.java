package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.hutool.core.io.FileUtil;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.infra.framework.file.core.utils.FileTypeUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.dept.dto.PostRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderItemDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderItemMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.StudentContactExtensionMapper;
import cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactConstants;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.module.zsjos.service.registration.RegistrationConstants.*;

@Service
@Slf4j
public class RegistrationServiceImpl implements RegistrationService {
    private static final Map<String, String> ATTACHMENT_MIME_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp",
            "pdf", "application/pdf", "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xls", "application/vnd.ms-excel",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    @Resource private RegistrationCaseMapper caseMapper;
    @Resource private RegistrationCaseChecklistItemMapper caseItemMapper;
    @Resource private RegistrationChecklistTemplateMapper templateMapper;
    @Resource private RegistrationChecklistTemplateItemMapper templateItemMapper;
    @Resource private RegistrationRouteOptionMapper routeOptionMapper;
    @Resource private RegistrationCaseRouteMapper caseRouteMapper;
    @Resource private RegistrationItemAttachmentMapper attachmentMapper;
    @Resource private RegistrationItemMapper registrationItemMapper;
    @Resource private ServiceRelationMapper serviceRelationMapper;
    @Resource private RegistrationCommandMapper commandMapper;
    @Resource private SalesOrderMapper orderMapper;
    @Resource private SalesOrderItemMapper orderItemMapper;
    @Resource private PersonMapper personMapper;
    @Resource private LeadMapper leadMapper;
    @Resource private LeadAssignmentRelationMapper userRelationMapper;
    @Resource private RoleApi roleApi;
    @Resource private PermissionApi permissionApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;
    @Resource private PostApi postApi;
    @Resource private FileApi fileApi;
    @Resource private RegistrationNotifyPublisher registrationNotifyPublisher;
    @Resource private AdvancedFilterService advancedFilterService;
    @Resource private BusinessTaskMapper businessTaskMapper;
    @Resource private StudentContactExtensionMapper studentContactExtensionMapper;
    @Resource private BpmProcessInstanceApi processInstanceApi;

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
        List<RegistrationRouteOptionDO> routeDefinitions = routeOptionMapper.selectByVersionId(template.getPublishedVersionId()).stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled())).toList();
        if (definitions.stream().noneMatch(item -> ITEM_TYPE_STUDY_PLANNER.equals(item.getItemType())) || routeDefinitions.isEmpty()) {
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
            item.setAttachmentRequired(definition.getAttachmentRequired());
            item.setChecked(false); item.setVersion(0); caseItemMapper.insert(item);
        }
        for (RegistrationRouteOptionDO definition : routeDefinitions) {
            RegistrationCaseRouteDO route = new RegistrationCaseRouteDO();
            route.setRegistrationCaseId(registrationCase.getId()); route.setRouteOptionId(definition.getId());
            route.setOptionKey(definition.getOptionKey()); route.setDepartmentId(definition.getDepartmentId());
            route.setDepartmentNameSnapshot(definition.getDepartmentNameSnapshot()); route.setAssigneeType(definition.getAssigneeType());
            route.setSelected(false); route.setSort(definition.getSort()); route.setVersion(0);
            caseRouteMapper.insert(route);
        }
        SalesOrderDO order = orderMapper.selectById(orderId);
        registrationNotifyPublisher.publishTaskCreated(registrationCase, order);
        return registrationCase.getId();
    }

    @Override
    public PageResult<RegistrationCaseRespVO> getPoolPage(RegistrationPoolPageReqVO pageParam) {
        List<Long> matchedOrderIds = orderMapper.selectIdsByKeyword(pageParam.getKeyword());
        List<Long> matchedCaseIds = advancedFilterService.matchRegistrationCaseIds(pageParam.getAdvancedFilter());
        PageResult<RegistrationCaseDO> page = caseMapper.selectPoolPage(pageParam, matchedOrderIds, matchedCaseIds);
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
    public List<StudyPlannerSimpleRespVO> getStudyPlannerCandidates(Long userId) {
        Set<Long> userIds = new LinkedHashSet<>();
        for (String scene : List.of(RELATION_REGISTRATION_MANAGER_PLANNER, RELATION_REGISTRATION_SPECIALIST_PLANNER)) {
            userRelationMapper.selectListBySourceUserIds(scene, List.of(userId)).stream()
                    .filter(relation -> CommonStatusEnum.ENABLE.getStatus().equals(relation.getStatus()))
                    .map(LeadAssignmentRelationDO::getTargetUserId).forEach(userIds::add);
        }
        return adminUserApi.getUserList(userIds).stream()
                .filter(user -> CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()))
                .sorted(Comparator.comparing(AdminUserRespDTO::getNickname).thenComparing(AdminUserRespDTO::getId))
                .map(user -> new StudyPlannerSimpleRespVO(user.getId(), user.getNickname())).toList();
    }

    @Override
    @ZsjosPermission(bizType = "registration-case", bizId = "#caseId", action = "read")
    public List<StudyPlannerSimpleRespVO> getRouteCandidates(Long caseId, Long routeId, Long userId) {
        RegistrationCaseRouteDO route = caseRouteMapper.selectById(routeId);
        if (route == null || !Objects.equals(route.getRegistrationCaseId(), caseId)) {
            throw exception(REGISTRATION_ROUTE_INVALID);
        }
        return resolveRouteCandidates(route, userId);
    }

    private List<StudyPlannerSimpleRespVO> resolveRouteCandidates(RegistrationCaseRouteDO route, Long userId) {
        if (!ASSIGNEE_STUDY_PLANNER.equals(route.getAssigneeType())) return List.of();
        return getStudyPlannerCandidates(userId);
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
        if (ITEM_TYPE_STUDY_PLANNER.equals(item.getItemType()) || ITEM_TYPE_ATTACHMENT.equals(item.getItemType())) {
            throw exception(REGISTRATION_CHECKLIST_ITEM_FIXED);
        }
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
    public RegistrationCaseRespVO updateRoutes(Long caseId, Long userId, RegistrationRoutesUpdateReqVO reqVO) {
        String fingerprint = reqVO.getRoutes().stream().sorted(Comparator.comparing(RegistrationRoutesUpdateReqVO.RouteReqVO::getRouteId))
                .map(item -> item.getRouteId() + ":" + item.getSelected() + ":" + item.getAssigneeUserId())
                .collect(Collectors.joining(","));
        if (!beginCommand(caseId, userId, "update-routes", reqVO.getIdempotencyKey(), fingerprint)) {
            return getCaseForUpdateResult(caseId);
        }
        RegistrationCaseDO registrationCase = lockEditable(caseId, reqVO.getVersion());
        List<RegistrationCaseRouteDO> routes = caseRouteMapper.selectByCaseId(caseId);
        Map<Long, RegistrationRoutesUpdateReqVO.RouteReqVO> requested = reqVO.getRoutes().stream()
                .collect(Collectors.toMap(RegistrationRoutesUpdateReqVO.RouteReqVO::getRouteId, Function.identity()));
        if (routes.isEmpty() || requested.size() != routes.size()
                || routes.stream().anyMatch(route -> !requested.containsKey(route.getId()))
                || requested.values().stream().noneMatch(item -> Boolean.TRUE.equals(item.getSelected()))) {
            throw exception(REGISTRATION_ROUTE_INVALID);
        }
        LocalDateTime now = LocalDateTime.now();
        Long previousPlannerId = registrationCase.getStudyPlannerUserId();
        Long plannerId = null;
        for (RegistrationCaseRouteDO route : routes) {
            RegistrationRoutesUpdateReqVO.RouteReqVO value = requested.get(route.getId());
            if (Boolean.TRUE.equals(value.getSelected())) {
                if (!ASSIGNEE_STUDY_PLANNER.equals(route.getAssigneeType()) || value.getAssigneeUserId() == null
                        || resolveRouteCandidates(route, userId).stream()
                        .noneMatch(candidate -> Objects.equals(candidate.getId(), value.getAssigneeUserId()))) {
                    throw exception(REGISTRATION_ROUTE_ASSIGNEE_INVALID);
                }
                AdminUserRespDTO assignee = adminUserApi.getUser(value.getAssigneeUserId());
                route.setSelected(true); route.setAssigneeUserId(value.getAssigneeUserId());
                route.setAssigneeNameSnapshot(assignee == null ? null : assignee.getNickname());
                if (ASSIGNEE_STUDY_PLANNER.equals(route.getAssigneeType())) plannerId = value.getAssigneeUserId();
            } else {
                route.setSelected(false); route.setAssigneeUserId(null); route.setAssigneeNameSnapshot(null);
            }
            route.setVersion(route.getVersion() + 1); caseRouteMapper.updateById(route);
        }
        registrationCase.setStudyPlannerUserId(plannerId);
        RegistrationCaseChecklistItemDO plannerItem = caseItemMapper.selectByCaseId(caseId).stream()
                .filter(item -> ITEM_TYPE_STUDY_PLANNER.equals(item.getItemType())).findFirst()
                .orElseThrow(() -> exception(REGISTRATION_CHECKLIST_CONFIG_INVALID));
        plannerItem.setChecked(plannerId != null);
        plannerItem.setCheckedByUserId(plannerId != null ? userId : null);
        plannerItem.setCheckedAt(plannerId != null ? now : null);
        plannerItem.setVersion(plannerItem.getVersion() + 1); caseItemMapper.updateById(plannerItem);
        touch(registrationCase);
        if (plannerId != null && !Objects.equals(previousPlannerId, plannerId)) {
            SalesOrderDO order = orderMapper.selectById(registrationCase.getOrderId());
            registrationNotifyPublisher.publishPlannerAssigned(registrationCase, order, resolveLeadNo(order), plannerId);
        }
        return convert(registrationCase, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "registration-case", bizId = "#caseId", action = "update")
    public RegistrationAttachmentUploadRespVO uploadAttachment(Long caseId, Long itemId, Long userId,
                                                                 Integer version, String idempotencyKey,
                                                                 MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename();
        if (!beginCommand(caseId, userId, "upload-attachment", idempotencyKey,
                itemId + ":" + originalName + ":" + file.getSize())) {
            RegistrationCommandDO replay = commandMapper.selectByIdempotencyKey(idempotencyKey);
            RegistrationItemAttachmentDO existing = replay == null || replay.getResultAttachmentId() == null
                    ? null : attachmentMapper.selectById(replay.getResultAttachmentId());
            if (existing == null || !Objects.equals(existing.getRegistrationCaseId(), caseId)
                    || !Objects.equals(existing.getChecklistItemId(), itemId)) {
                throw exception(REGISTRATION_IDEMPOTENCY_RESULT_INVALID);
            }
            RegistrationCaseDO current = caseMapper.selectById(caseId);
            if (current == null) throw exception(REGISTRATION_IDEMPOTENCY_RESULT_INVALID);
            return toAttachmentUpload(existing, current.getVersion());
        }
        RegistrationCaseDO registrationCase = lockEditable(caseId, version);
        RegistrationCaseChecklistItemDO item = requireAttachmentItem(caseId, itemId);
        if (attachmentMapper.selectByItemId(itemId).size() >= MAX_ATTACHMENTS_PER_ITEM) {
            throw exception(REGISTRATION_ATTACHMENT_LIMIT);
        }
        byte[] content = file.getBytes();
        String type = FileTypeUtils.getMineType(content, originalName);
        if (file.isEmpty() || file.getSize() > MAX_ATTACHMENT_SIZE || !validAttachmentType(originalName, type)) {
            throw exception(REGISTRATION_ATTACHMENT_INVALID);
        }
        FileInfoRespDTO stored = fileApi.createFileInfo(content, originalName,
                "zsjos/registration/" + caseId + "/" + itemId, type);
        if (stored == null || stored.getId() == null) throw exception(REGISTRATION_ATTACHMENT_INVALID);
        try {
            RegistrationItemAttachmentDO attachment = new RegistrationItemAttachmentDO();
            attachment.setRegistrationCaseId(caseId); attachment.setChecklistItemId(itemId);
            attachment.setInfraFileId(stored.getId()); attachment.setFileUrl(stored.getUrl());
            attachment.setOriginalName(stored.getName()); attachment.setContentType(stored.getType());
            attachment.setFileSize(stored.getSize()); attachment.setUploadedByUserId(userId);
            attachment.setUploadedAt(LocalDateTime.now()); attachmentMapper.insert(attachment);
            markAttachmentItem(item, userId, true); touch(registrationCase);
            RegistrationCommandDO command = commandMapper.selectByIdempotencyKey(idempotencyKey);
            if (command == null) throw exception(REGISTRATION_IDEMPOTENCY_RESULT_INVALID);
            command.setResultAttachmentId(attachment.getId());
            if (commandMapper.updateById(command) != 1) {
                throw exception(REGISTRATION_IDEMPOTENCY_RESULT_INVALID);
            }
            return toAttachmentUpload(attachment, registrationCase.getVersion());
        } catch (RuntimeException failure) {
            try {
                fileApi.deleteFileIfExists(stored.getId());
            } catch (Exception compensationFailure) {
                failure.addSuppressed(compensationFailure);
            }
            throw failure;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "registration-case", bizId = "#caseId", action = "update")
    public RegistrationCaseRespVO deleteAttachment(Long caseId, Long itemId, Long attachmentId, Long userId,
                                                     RegistrationAttachmentDeleteReqVO reqVO) {
        if (!beginCommand(caseId, userId, "delete-attachment", reqVO.getIdempotencyKey(),
                itemId + ":" + attachmentId)) return getCaseForUpdateResult(caseId);
        RegistrationCaseDO registrationCase = lockEditable(caseId, reqVO.getVersion());
        RegistrationCaseChecklistItemDO item = requireAttachmentItem(caseId, itemId);
        RegistrationItemAttachmentDO attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !Objects.equals(attachment.getRegistrationCaseId(), caseId)
                || !Objects.equals(attachment.getChecklistItemId(), itemId)) {
            throw exception(REGISTRATION_ATTACHMENT_INVALID);
        }
        attachmentMapper.deleteById(attachmentId);
        boolean hasAttachments = !attachmentMapper.selectByItemId(itemId).isEmpty();
        markAttachmentItem(item, userId, hasAttachments); touch(registrationCase);
        return convert(registrationCase, true);
    }

    private RegistrationCaseChecklistItemDO requireAttachmentItem(Long caseId, Long itemId) {
        RegistrationCaseChecklistItemDO item = caseItemMapper.selectById(itemId);
        if (item == null || !Objects.equals(item.getRegistrationCaseId(), caseId)
                || !ITEM_TYPE_ATTACHMENT.equals(item.getItemType())) {
            throw exception(REGISTRATION_ATTACHMENT_INVALID);
        }
        return item;
    }

    private void markAttachmentItem(RegistrationCaseChecklistItemDO item, Long userId, boolean checked) {
        item.setChecked(checked); item.setCheckedByUserId(checked ? userId : null);
        item.setCheckedAt(checked ? LocalDateTime.now() : null); item.setVersion(item.getVersion() + 1);
        caseItemMapper.updateById(item);
    }

    private boolean validAttachmentType(String name, String type) {
        String extension = Optional.ofNullable(FileUtil.extName(name)).orElse("").toLowerCase(Locale.ROOT);
        return Objects.equals(ATTACHMENT_MIME_BY_EXTENSION.get(extension), type);
    }

    private RegistrationAttachmentUploadRespVO toAttachmentUpload(RegistrationItemAttachmentDO attachment, Integer version) {
        return new RegistrationAttachmentUploadRespVO(attachment.getId(), attachment.getInfraFileId(),
                attachment.getFileUrl(), attachment.getOriginalName(), attachment.getContentType(), attachment.getFileSize(), version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "registration-case", bizId = "#caseId", action = "update")
    public RegistrationCaseRespVO updateStudyPlanner(Long caseId, Long userId, RegistrationPlannerUpdateReqVO reqVO) {
        if (!beginCommand(caseId, userId, "update-planner", reqVO.getIdempotencyKey(),
                String.valueOf(reqVO.getStudyPlannerUserId()))) return getCaseForUpdateResult(caseId);
        RegistrationCaseDO registrationCase = lockEditable(caseId, reqVO.getVersion());
        Long previousPlannerId = registrationCase.getStudyPlannerUserId();
        RegistrationCaseRouteDO plannerRoute = caseRouteMapper.selectByCaseId(caseId).stream()
                .filter(route -> ASSIGNEE_STUDY_PLANNER.equals(route.getAssigneeType())).findFirst()
                .orElseThrow(() -> exception(REGISTRATION_ROUTE_INVALID));
        if (Objects.equals(previousPlannerId, reqVO.getStudyPlannerUserId())) return convert(registrationCase, true);
        if (resolveRouteCandidates(plannerRoute, userId).stream()
                .noneMatch(item -> Objects.equals(item.getId(), reqVO.getStudyPlannerUserId()))) {
            throw exception(REGISTRATION_STUDY_PLANNER_INVALID);
        }
        registrationCase.setStudyPlannerUserId(reqVO.getStudyPlannerUserId());
        LocalDateTime now = LocalDateTime.now();
        AdminUserRespDTO planner = adminUserApi.getUser(reqVO.getStudyPlannerUserId());
        plannerRoute.setSelected(true);
        plannerRoute.setAssigneeUserId(reqVO.getStudyPlannerUserId());
        plannerRoute.setAssigneeNameSnapshot(planner == null ? null : planner.getNickname());
        plannerRoute.setVersion(plannerRoute.getVersion() + 1);
        caseRouteMapper.updateById(plannerRoute);
        RegistrationCaseChecklistItemDO plannerItem = caseItemMapper.selectByCaseId(caseId).stream()
                .filter(item -> ITEM_TYPE_STUDY_PLANNER.equals(item.getItemType())).findFirst()
                .orElseThrow(() -> exception(REGISTRATION_CHECKLIST_CONFIG_INVALID));
        plannerItem.setChecked(true); plannerItem.setCheckedByUserId(userId); plannerItem.setCheckedAt(now);
        plannerItem.setVersion(plannerItem.getVersion() + 1); caseItemMapper.updateById(plannerItem);
        touch(registrationCase);
        SalesOrderDO order = orderMapper.selectById(registrationCase.getOrderId());
        registrationNotifyPublisher.publishPlannerAssigned(registrationCase, order, resolveLeadNo(order),
                reqVO.getStudyPlannerUserId());
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
        if (items.isEmpty() || items.stream().anyMatch(item -> ITEM_TYPE_CHECKBOX.equals(item.getItemType())
                && !Boolean.TRUE.equals(item.getChecked()))) {
            throw exception(REGISTRATION_CHECKLIST_INCOMPLETE);
        }
        Map<Long, List<RegistrationItemAttachmentDO>> attachments = attachmentMapper.selectByItemIds(
                        items.stream().map(RegistrationCaseChecklistItemDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(RegistrationItemAttachmentDO::getChecklistItemId));
        if (items.stream().anyMatch(item -> ITEM_TYPE_ATTACHMENT.equals(item.getItemType())
                && Boolean.TRUE.equals(item.getAttachmentRequired())
                && attachments.getOrDefault(item.getId(), List.of()).isEmpty())) {
            throw exception(REGISTRATION_ATTACHMENT_REQUIRED);
        }
        List<RegistrationCaseRouteDO> routes = caseRouteMapper.selectByCaseId(caseId);
        List<RegistrationCaseRouteDO> selectedRoutes = routes.stream()
                .filter(route -> Boolean.TRUE.equals(route.getSelected())).toList();
        if (selectedRoutes.size() != 1 || !ASSIGNEE_STUDY_PLANNER.equals(selectedRoutes.get(0).getAssigneeType())) {
            throw exception(REGISTRATION_ROUTE_INVALID);
        }
        if (selectedRoutes.stream().anyMatch(route -> route.getAssigneeUserId() == null
                || resolveRouteCandidates(route, userId).stream().noneMatch(candidate -> Objects.equals(candidate.getId(), route.getAssigneeUserId())))) {
            throw exception(REGISTRATION_ROUTE_ASSIGNEE_INVALID);
        }
        Long plannerId = selectedRoutes.stream().filter(route -> ASSIGNEE_STUDY_PLANNER.equals(route.getAssigneeType()))
                .map(RegistrationCaseRouteDO::getAssigneeUserId).findFirst().orElse(null);
        Long serviceOwnerId = plannerId;
        LocalDateTime now = LocalDateTime.now();
        for (RegistrationCaseChecklistItemDO item : items) {
            if (ITEM_TYPE_STUDY_PLANNER.equals(item.getItemType()) && !Boolean.TRUE.equals(item.getChecked())) {
                continue;
            }
            if (ITEM_TYPE_ATTACHMENT.equals(item.getItemType())
                    && attachments.getOrDefault(item.getId(), List.of()).isEmpty()) {
                continue;
            }
            RegistrationItemDO fact = new RegistrationItemDO();
            fact.setRegistrationCaseId(caseId); fact.setChecklistItemId(item.getId()); fact.setItemType(item.getItemType());
            fact.setItemLabelSnapshot(item.getTitleSnapshot()); fact.setOccurredAt(item.getCheckedAt());
            fact.setRecordedAt(now); fact.setRecordedByUserId(item.getCheckedByUserId()); registrationItemMapper.insert(fact);
        }
        for (SalesOrderItemDO orderItem : orderItemMapper.selectListByOrderId(order.getId())) {
            ServiceRelationDO relation = new ServiceRelationDO();
            relation.setPersonId(order.getPersonId()); relation.setOrderId(order.getId()); relation.setOrderItemId(orderItem.getId());
            relation.setRegistrationCaseId(caseId); relation.setStatus("active"); relation.setOwnerUserId(serviceOwnerId);
            relation.setAcceptanceStatus("pending"); relation.setServiceSnapshot(orderItem.getProductSnapshot());
            relation.setActivatedAt(now); relation.setVersion(0);
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
        if (registrationCase == null) return;
        List<cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentContactExtensionDO> bpmCancellations = new ArrayList<>();
        if (!STATUS_COMPLETED.equals(registrationCase.getStatus()) && !STATUS_CANCELLED.equals(registrationCase.getStatus())) {
            RegistrationCaseDO locked = caseMapper.selectByIdForUpdate(registrationCase.getId(), TenantContextHolder.getRequiredTenantId());
            if (locked != null && !STATUS_COMPLETED.equals(locked.getStatus())
                    && !STATUS_CANCELLED.equals(locked.getStatus())) {
                locked.setStatus(STATUS_CANCELLED); locked.setCancelledAt(now); locked.setCancelReason(reason);
                locked.setVersion(locked.getVersion() + 1); caseMapper.updateById(locked);
            }
        }
        serviceRelationMapper.selectList(new LambdaQueryWrapperX<ServiceRelationDO>()
                .eq(ServiceRelationDO::getOrderId, orderId)
                .eq(ServiceRelationDO::getStatus, "active")).forEach(relation -> {
            ServiceRelationDO lockedRelation = serviceRelationMapper.selectByIdForUpdate(
                    relation.getId(), TenantContextHolder.getRequiredTenantId());
            if (lockedRelation == null || !"active".equals(lockedRelation.getStatus())) return;
            String terminationReason = reason == null || reason.isBlank() ? "订单已取消" : reason.trim();
            if (serviceRelationMapper.cancelActive(lockedRelation.getId(), lockedRelation.getVersion(), now,
                    terminationReason) != 1) throw exception(STUDENT_SERVICE_VERSION_CONFLICT);
            for (String type : List.of(StudentContactConstants.TYPE_FIRST_CONTACT, StudentContactConstants.TYPE_STUDY_PLAN,
                    StudentContactConstants.TYPE_CONTACT, StudentContactConstants.TYPE_ASSISTANCE)) {
                businessTaskMapper.cancelPending(type, lockedRelation.getId(), null, now, "服务关系已取消");
            }
            studentContactExtensionMapper.selectList(new LambdaQueryWrapperX<cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentContactExtensionDO>()
                    .eq(cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentContactExtensionDO::getServiceRelationId, lockedRelation.getId())
                    .eq(cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentContactExtensionDO::getStatus, "pending"))
                    .forEach(extension -> {
                        if (studentContactExtensionMapper.transitionPending(extension.getId(), extension.getVersion(),
                                "cancelled", "服务关系已取消", null, now) == 1
                                && extension.getProcessInstanceId() != null) bpmCancellations.add(extension);
                    });
        });
        if (!bpmCancellations.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    bpmCancellations.forEach(extension -> {
                        try { processInstanceApi.cancelProcessInstanceByStartUser(extension.getApplicantUserId(),
                                extension.getProcessInstanceId(), "服务关系已取消"); }
                        catch (RuntimeException ex) {
                            log.error("[cancelByOrderId][extensionId({}) BPM cancellation failed after commit]",
                                    extension.getId(), ex);
                        }
                    });
                }
            });
        }
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
            List<RegistrationItemAttachmentDO> attachmentRows = attachmentMapper.selectByItemIds(
                    items.stream().map(RegistrationCaseChecklistItemDO::getId).toList());
            Map<Long, List<RegistrationItemAttachmentDO>> attachmentsByItem = attachmentRows.stream()
                    .collect(Collectors.groupingBy(RegistrationItemAttachmentDO::getChecklistItemId));
            Set<Long> actorIds = items.stream().map(RegistrationCaseChecklistItemDO::getCheckedByUserId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            attachmentRows.stream().map(RegistrationItemAttachmentDO::getUploadedByUserId)
                    .filter(Objects::nonNull).forEach(actorIds::add);
            Map<Long, AdminUserRespDTO> actors = actorIds.isEmpty() ? Map.of() : adminUserApi.getUserMap(actorIds);
            result.setItems(items.stream().map(item -> {
                RegistrationCaseRespVO.ItemVO row = new RegistrationCaseRespVO.ItemVO();
                row.setId(item.getId()); row.setItemKey(item.getItemKey()); row.setItemType(item.getItemType());
                row.setTitle(item.getTitleSnapshot()); row.setSort(item.getSort()); row.setChecked(item.getChecked());
                row.setAttachmentRequired(item.getAttachmentRequired());
                row.setCheckedByUserId(item.getCheckedByUserId()); row.setCheckedAt(item.getCheckedAt());
                AdminUserRespDTO actor = item.getCheckedByUserId() == null ? null : actors.get(item.getCheckedByUserId());
                row.setCheckedByUserName(actor == null ? null : actor.getNickname());
                row.setAttachments(attachmentsByItem.getOrDefault(item.getId(), List.of()).stream().map(attachment -> {
                    RegistrationCaseRespVO.AttachmentVO attachmentVO = new RegistrationCaseRespVO.AttachmentVO();
                    attachmentVO.setId(attachment.getId()); attachmentVO.setInfraFileId(attachment.getInfraFileId());
                    attachmentVO.setFileUrl(attachment.getFileUrl()); attachmentVO.setOriginalName(attachment.getOriginalName());
                    attachmentVO.setContentType(attachment.getContentType()); attachmentVO.setFileSize(attachment.getFileSize());
                    attachmentVO.setUploadedByUserId(attachment.getUploadedByUserId()); attachmentVO.setUploadedAt(attachment.getUploadedAt());
                    AdminUserRespDTO uploader = attachment.getUploadedByUserId() == null ? null : actors.get(attachment.getUploadedByUserId());
                    attachmentVO.setUploadedByUserName(uploader == null ? null : uploader.getNickname());
                    return attachmentVO;
                }).toList());
                return row;
            }).toList());
            List<RegistrationCaseRouteDO> routes = caseRouteMapper.selectByCaseId(registrationCase.getId());
            result.setRoutes(routes.stream().map(route -> {
                RegistrationCaseRespVO.RouteVO row = new RegistrationCaseRespVO.RouteVO();
                row.setId(route.getId()); row.setOptionKey(route.getOptionKey()); row.setDepartmentId(route.getDepartmentId());
                row.setDepartmentName(route.getDepartmentNameSnapshot()); row.setAssigneeType(route.getAssigneeType());
                row.setAssigneeTypeLabel(ASSIGNEE_STUDY_PLANNER.equals(route.getAssigneeType()) ? "学习规划师" : "编导");
                row.setSelected(route.getSelected()); row.setAssigneeUserId(route.getAssigneeUserId());
                row.setAssigneeUserName(route.getAssigneeNameSnapshot()); row.setSort(route.getSort());
                return row;
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
                || result.getItems().stream().anyMatch(item -> ITEM_TYPE_CHECKBOX.equals(item.getItemType())
                && !Boolean.TRUE.equals(item.getChecked()))) {
            code = COMPLETION_BLOCK_CHECKLIST_INCOMPLETE; reason = "请先完成全部报名履约清单项";
        } else if (result.getItems().stream().anyMatch(item -> ITEM_TYPE_ATTACHMENT.equals(item.getItemType())
                && Boolean.TRUE.equals(item.getAttachmentRequired())
                && (item.getAttachments() == null || item.getAttachments().isEmpty()))) {
            code = COMPLETION_BLOCK_ATTACHMENT_REQUIRED; reason = "请先上传所有必传附件";
        } else if (result.getRoutes() == null || result.getRoutes().stream()
                .noneMatch(item -> Boolean.TRUE.equals(item.getSelected())
                        && ASSIGNEE_STUDY_PLANNER.equals(item.getAssigneeType()))) {
            code = COMPLETION_BLOCK_PLANNER_REQUIRED; reason = "请先分配学习规划师";
        } else if (result.getRoutes().stream().filter(item -> Boolean.TRUE.equals(item.getSelected()))
                .anyMatch(route -> !ASSIGNEE_STUDY_PLANNER.equals(route.getAssigneeType())
                        || route.getAssigneeUserId() == null || caseRouteMapper.selectById(route.getId()) == null)) {
            code = COMPLETION_BLOCK_PLANNER_INVALID; reason = "学习规划师分配无效，请重新选择";
        }
        result.setCompletable(code == null);
        result.setCompletionBlockCode(code);
        result.setCompletionBlockReason(reason);
    }

    private String resolveLeadNo(SalesOrderDO order) {
        if (order == null || order.getLeadId() == null) return "";
        LeadDO lead = leadMapper.selectById(order.getLeadId());
        return lead == null || lead.getLeadNo() == null ? "" : lead.getLeadNo();
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
