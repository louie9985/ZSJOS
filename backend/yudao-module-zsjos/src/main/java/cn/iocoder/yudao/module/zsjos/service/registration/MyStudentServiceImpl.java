package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MyStudentPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MyStudentRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderItemDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderItemMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassScopeService;
import cn.iocoder.yudao.module.zsjos.service.deliveryclass.DeliveryClassService;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.STUDENT_NOT_EXISTS;

@Service
@Slf4j
public class MyStudentServiceImpl implements MyStudentService {
    @Resource private ServiceRelationMapper relationMapper;
    @Resource private PersonMapper personMapper;
    @Resource private LeadMapper leadMapper;
    @Resource private SalesOrderMapper orderMapper;
    @Resource private SalesOrderItemMapper orderItemMapper;
    @Resource private AdvancedFilterService advancedFilterService;
    @Resource private AdminUserApi adminUserApi;
    @Resource private MediaAccountMapper mediaAccountMapper;
    @Resource private PermissionApi permissionApi;
    @Resource private DeliveryClassScopeService classScopeService;
    @Resource private DeliveryClassMapper deliveryClassMapper;
    @Resource private cn.iocoder.yudao.module.zsjos.service.common.BusinessReadScopeService readScopeService;

    @Override
    public PageResult<MyStudentRespVO> getMyPage(Long userId, MyStudentPageReqVO reqVO) {
        if (reqVO.getReadScope() != null || reqVO.getTargetUserId() != null) {
            return getExplicitReadPage(userId, reqVO, false);
        }
        List<Long> matchedIds = advancedFilterService.matchStudentPersonIds(reqVO.getAdvancedFilter(), userId);
        // Managed readers see their department subtree by service owner; everyone else stays self-only.
        if (hasManagedStudentReadPermission(userId)) {
            return getManagedPage(userId, reqVO, matchedIds);
        }
        PageResult<PersonDO> people = personMapper.selectMyStudentPage(reqVO, userId, matchedIds);
        List<Long> personIds = people.getList().stream().map(PersonDO::getId).toList();
        Map<Long, List<ServiceRelationDO>> groups = relationMapper
                .selectAssignedByUserAndPersonIds(userId, personIds, reqVO.getServiceStatus()).stream()
                .filter(relation -> reqVO.getClassId() == null || Objects.equals(relation.getClassId(), reqVO.getClassId()))
                .collect(Collectors.groupingBy(ServiceRelationDO::getPersonId, LinkedHashMap::new, Collectors.toList()));
        return new PageResult<>(people.getList().stream().map(person -> convert(userId, person.getId(), groups.get(person.getId())))
                .toList(), people.getTotal());
    }

    /**
     * Department-scoped student read. The visible owner set is resolved from the reader's persisted data
     * scope, so DEPT_AND_CHILD covers the whole subtree. Students follow their service owner live, which
     * also keeps pending-class students visible to the owner's department.
     */
    private PageResult<MyStudentRespVO> getManagedPage(Long userId, MyStudentPageReqVO reqVO, List<Long> matchedIds) {
        DeliveryClassScopeService.Scope scope = classScopeService.resolve(userId);
        Set<Long> ownerUserIds = resolveManagedOwnerIds(userId, scope);
        PageResult<PersonDO> people = scope.allDepartments()
                ? personMapper.selectAllStudentPage(reqVO, matchedIds)
                : personMapper.selectManagedStudentPage(reqVO, ownerUserIds, matchedIds);
        List<Long> personIds = people.getList().stream().map(PersonDO::getId).toList();
        List<ServiceRelationDO> relations = scope.allDepartments()
                ? relationMapper.selectOwnedByPersonIds(personIds, reqVO.getServiceStatus())
                : relationMapper.selectOwnedByOwnerIdsAndPersonIds(ownerUserIds, personIds, reqVO.getServiceStatus());
        Map<Long, List<ServiceRelationDO>> groups = relations.stream()
                .filter(relation -> reqVO.getClassId() == null || Objects.equals(relation.getClassId(), reqVO.getClassId()))
                .collect(Collectors.groupingBy(ServiceRelationDO::getPersonId, LinkedHashMap::new, Collectors.toList()));
        return new PageResult<>(people.getList().stream()
                .map(person -> convert(userId, person.getId(), groups.get(person.getId()))).toList(), people.getTotal());
    }

    @Override
    public PageResult<MyStudentRespVO> getMediaPage(Long userId, MyStudentPageReqVO reqVO) {
        if (MediaStudentReadScope.canReadAll(permissionApi, userId)
                && (reqVO.getReadScope() == null && reqVO.getTargetUserId() == null
                    || "ALL".equals(reqVO.getReadScope()) && reqVO.getTargetUserId() == null)) {
            return getAllMediaPage(userId, reqVO);
        }
        if (reqVO.getReadScope() != null || reqVO.getTargetUserId() != null) {
            return getExplicitReadPage(userId, reqVO, true);
        }
        PageResult<PersonDO> people = personMapper.selectMediaStudentPage(reqVO, userId);
        List<Long> personIds = people.getList().stream().map(PersonDO::getId).toList();
        Set<Long> participantPersonIds = new HashSet<>(mediaAccountMapper.selectParticipantStudentIds(userId, personIds));
        Map<Long, ServiceRelationDO> visibleRelations = new LinkedHashMap<>();
        relationMapper.selectActiveByPersonIds(personIds).stream()
                .filter(row -> Objects.equals(row.getContentDirectorUserId(), userId)
                        || Objects.equals(row.getCareerPlannerUserId(), userId)
                        || Objects.equals(row.getOperatorUserId(), userId)
                        || participantPersonIds.contains(row.getPersonId()))
                .forEach(row -> visibleRelations.put(row.getId(), row));
        Map<Long, List<ServiceRelationDO>> groups = visibleRelations.values().stream()
                .collect(Collectors.groupingBy(ServiceRelationDO::getPersonId, LinkedHashMap::new, Collectors.toList()));
        return new PageResult<>(people.getList().stream()
                .map(person -> convert(userId, person.getId(), groups.get(person.getId())))
                .toList(), people.getTotal());
    }

    @Override
    public PageResult<MyStudentRespVO> getDirectorPage(Long userId, MyStudentPageReqVO reqVO) {
        return getMediaPage(userId, reqVO);
    }

    @Override
    @ZsjosPermission(bizType = "student", bizId = "#personId", action = "read")
    public MyStudentRespVO getMyStudent(Long userId, Long personId) {
        List<ServiceRelationDO> relations = selectVisibleRelationsForPerson(userId, personId);
        if (relations.isEmpty()) throw exception(STUDENT_NOT_EXISTS);
        return convert(userId, personId, relations);
    }

    @Override
    public MyStudentRespVO getMediaStudent(Long userId, Long personId) {
        if (MediaStudentReadScope.canReadAll(permissionApi, userId)) {
            if (!personMapper.existsMediaStudent(personId)) throw exception(STUDENT_NOT_EXISTS);
            var relations = relationMapper.selectMediaReadByPersonIds(List.of(personId), null);
            return convert(userId, personId, relations);
        }
        Map<Long, ServiceRelationDO> visibleRelations = new LinkedHashMap<>();
        relationMapper.selectActiveByContentDirectorAndPerson(userId, personId)
                .forEach(row -> visibleRelations.put(row.getId(), row));
        relationMapper.selectAssignedByUserAndPersonIds(userId, List.of(personId), null)
                .forEach(row -> visibleRelations.put(row.getId(), row));
        List<ServiceRelationDO> relations = new ArrayList<>(visibleRelations.values());
        if (relations.isEmpty()) throw exception(STUDENT_NOT_EXISTS);
        return convert(userId, personId, relations);
    }

    @Override
    public MyStudentRespVO getDirectorStudent(Long userId, Long personId) {
        return getMediaStudent(userId, personId);
    }

    @Override
    @ZsjosPermission(bizType = "student-service", bizId = "#relationId", action = "read")
    public MyStudentRespVO getMyStudentByService(Long userId, Long relationId) {
        ServiceRelationDO relation = relationMapper.selectById(relationId);
        if (relation == null || (!permissionApi.hasTenantReadAllAccess(userId)
                && !Set.of("active", "paused", "completed").contains(relation.getStatus()))) {
            throw exception(STUDENT_NOT_EXISTS);
        }
        List<ServiceRelationDO> relations = selectVisibleRelationsForPerson(userId, relation.getPersonId());
        if (relations.stream().noneMatch(item -> Objects.equals(item.getId(), relationId))) {
            throw exception(STUDENT_NOT_EXISTS);
        }
        return convert(userId, relation.getPersonId(), relations);
    }

    private boolean hasManagedStudentReadPermission(Long userId) {
        return permissionApi.hasAnyPermissions(userId, "zsjos:delivery-class:query",
                DeliveryClassService.PERMISSION_QUERY_MANAGED);
    }

    private Set<Long> resolveManagedOwnerIds(Long userId, DeliveryClassScopeService.Scope scope) {
        Set<Long> ownerIds = new LinkedHashSet<>();
        ownerIds.add(userId);
        if (!scope.allDepartments() && !scope.deptIds().isEmpty()) {
            adminUserApi.getUserListByDeptIds(scope.deptIds()).stream()
                    .map(AdminUserRespDTO::getId).filter(Objects::nonNull).forEach(ownerIds::add);
        }
        return ownerIds;
    }

    private List<ServiceRelationDO> selectVisibleRelationsForPerson(Long userId, Long personId) {
        if (permissionApi.hasTenantReadAllAccess(userId)) {
            return relationMapper.selectTenantReadByPersonIds(List.of(personId), null);
        }
        // A visible student does not make every course visible: match the managed list's owner scope.
        if (hasManagedStudentReadPermission(userId)) {
            DeliveryClassScopeService.Scope scope = classScopeService.resolve(userId);
            return scope.allDepartments()
                    ? relationMapper.selectOwnedByPersonIds(List.of(personId), null)
                    : relationMapper.selectOwnedByOwnerIdsAndPersonIds(
                            resolveManagedOwnerIds(userId, scope), List.of(personId), null);
        }
        return selectAssignedRelationsForPerson(userId, personId);
    }

    private PageResult<MyStudentRespVO> getAllMediaPage(Long actorId, MyStudentPageReqVO req) {
        List<Long> matchedIds = advancedFilterService.matchStudentPersonIds(req.getAdvancedFilter(), actorId);
        PageResult<PersonDO> page = personMapper.selectAllMediaStudentPage(req, matchedIds);
        var ids = page.getList().stream().map(PersonDO::getId).toList();
        var groups = relationMapper.selectMediaReadByPersonIds(ids, req.getServiceStatus()).stream()
                .filter(row -> req.getClassId() == null || Objects.equals(row.getClassId(), req.getClassId()))
                .collect(Collectors.groupingBy(ServiceRelationDO::getPersonId));
        return new PageResult<>(page.getList().stream()
                .map(person -> convert(actorId, person.getId(), groups.get(person.getId()))).toList(), page.getTotal());
    }

    private PageResult<MyStudentRespVO> getExplicitReadPage(Long actorId, MyStudentPageReqVO req, boolean media) {
        Long subjectId = readScopeService.resolve(req.getReadScope(), req.getTargetUserId(), actorId);
        List<Long> matchedIds = advancedFilterService.matchStudentPersonIds(req.getAdvancedFilter(),
                subjectId == null ? actorId : subjectId);
        PageResult<PersonDO> page = subjectId == null ? personMapper.selectTenantReadStudentPage(req, matchedIds)
                : media ? personMapper.selectMediaStudentPage(req, subjectId, matchedIds)
                : personMapper.selectMyStudentPage(req, subjectId, matchedIds);
        List<Long> personIds = page.getList().stream().map(PersonDO::getId).toList();
        List<ServiceRelationDO> relations = subjectId == null
                ? relationMapper.selectTenantReadByPersonIds(personIds, req.getServiceStatus())
                : relationMapper.selectAssignedByUserAndPersonIds(subjectId, personIds, req.getServiceStatus());
        Map<Long, List<ServiceRelationDO>> groups = relations.stream()
                .filter(row -> req.getClassId() == null || Objects.equals(row.getClassId(), req.getClassId()))
                .collect(Collectors.groupingBy(ServiceRelationDO::getPersonId));
        return new PageResult<>(page.getList().stream()
                .map(person -> convert(actorId, person.getId(), groups.get(person.getId()))).toList(), page.getTotal());
    }

    private List<ServiceRelationDO> selectAssignedRelationsForPerson(Long userId, Long personId) {
        List<ServiceRelationDO> owned = relationMapper.selectByOwnerAndPersonIncludingHistory(userId, personId);
        Map<Long, ServiceRelationDO> result = new LinkedHashMap<>();
        owned.forEach(relation -> result.put(relation.getId(), relation));
        relationMapper.selectActiveByCollaboratorAndPerson(userId, personId).stream()
                .forEach(relation -> result.put(relation.getId(), relation));
        return new ArrayList<>(result.values());
    }

    private List<ServiceRelationDO> selectAssignedRelations(Long userId) {
        List<ServiceRelationDO> owned = relationMapper.selectByOwnerUserIdIncludingHistory(userId);
        Map<Long, ServiceRelationDO> result = new LinkedHashMap<>();
        owned.forEach(relation -> result.put(relation.getId(), relation));
        relationMapper.selectActiveByCollaborator(userId)
                .forEach(relation -> result.put(relation.getId(), relation));
        return new ArrayList<>(result.values());
    }

    private MyStudentRespVO convert(Long userId, Long personId, List<ServiceRelationDO> relations) {
        relations = relations == null ? List.of() : relations;
        PersonDO person = personMapper.selectById(personId);
        if (person == null) throw exception(STUDENT_NOT_EXISTS);
        Set<Long> orderIds = relations.stream().map(ServiceRelationDO::getOrderId).collect(Collectors.toSet());
        Map<Long, SalesOrderDO> orders = orderIds.isEmpty() ? Map.of() : orderMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(SalesOrderDO::getId, Function.identity()));
        Set<Long> itemIds = relations.stream().map(ServiceRelationDO::getOrderItemId).collect(Collectors.toSet());
        Map<Long, SalesOrderItemDO> items = itemIds.isEmpty() ? Map.of() : orderItemMapper.selectBatchIds(itemIds).stream()
                .collect(Collectors.toMap(SalesOrderItemDO::getId, Function.identity()));
        Set<Long> collaboratorIds = relations.stream()
                .flatMap(relation -> java.util.stream.Stream.of(relation.getOwnerUserId(), relation.getContentDirectorUserId(),
                        relation.getCareerPlannerUserId(), relation.getOperatorUserId()))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> collaborators = collaboratorIds.isEmpty() || adminUserApi == null ? Map.of() : Optional.ofNullable(adminUserApi.getUserMap(collaboratorIds)).orElseGet(Map::of);
        Set<Long> leadIds = orders.values().stream().map(SalesOrderDO::getLeadId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, LeadDO> leads = leadIds.isEmpty() ? Map.of() : leadMapper.selectBatchIds(leadIds).stream()
                .collect(Collectors.toMap(LeadDO::getId, Function.identity()));
        Set<Long> classIds = relations.stream().map(ServiceRelationDO::getClassId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, DeliveryClassDO> classes = classIds.isEmpty() || deliveryClassMapper == null ? Map.of()
                : deliveryClassMapper.selectBatchIds(classIds).stream()
                        .collect(Collectors.toMap(DeliveryClassDO::getId, Function.identity()));
        MyStudentRespVO result = new MyStudentRespVO();
        result.setPersonId(personId);
        result.setPersonNo(person.getPersonNo());
        Long relatedLeadId = relations.stream().map(relation -> orders.get(relation.getOrderId()))
                .filter(Objects::nonNull).map(SalesOrderDO::getLeadId).filter(Objects::nonNull).findFirst().orElse(null);
        Long ownedLeadId = relations.stream()
                .filter(relation -> Objects.equals(userId, relation.getOwnerUserId()) && "active".equals(relation.getStatus()))
                .map(relation -> orders.get(relation.getOrderId())).filter(Objects::nonNull)
                .map(SalesOrderDO::getLeadId).filter(Objects::nonNull).findFirst().orElse(null);
        LeadDO relatedLead = relatedLeadId == null ? null : leadMapper.selectById(relatedLeadId);
        result.setLeadId(ownedLeadId);
        result.setLeadNo(relatedLead == null ? null : relatedLead.getLeadNo()); result.setName(person.getName());
        result.setMobile(person.getMobile()); result.setWechatId(person.getWechatId());
        result.setActivatedAt(relations.stream().map(ServiceRelationDO::getActivatedAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null));
        result.setServices(relations.stream().map(relation -> {
            MyStudentRespVO.ServiceVO row = new MyStudentRespVO.ServiceVO();
            row.setServiceRelationId(relation.getId()); row.setOrderId(relation.getOrderId()); row.setOrderItemId(relation.getOrderItemId());
            SalesOrderDO order = orders.get(relation.getOrderId()); SalesOrderItemDO item = items.get(relation.getOrderItemId());
            LeadDO serviceLead = order == null || order.getLeadId() == null ? null : leads.get(order.getLeadId());
            row.setLeadId(serviceLead == null ? null : serviceLead.getId());
            row.setLeadNo(serviceLead == null ? null : serviceLead.getLeadNo());
            row.setOrderNo(order == null ? null : order.getOrderNo());
            row.setClassId(relation.getClassId());
            DeliveryClassDO deliveryClass = relation.getClassId() == null ? null : classes.get(relation.getClassId());
            row.setClassName(deliveryClass == null ? null : deliveryClass.getClassName());
            populateCourseRights(row, item == null ? relation.getServiceSnapshot() : item.getProductSnapshot());
            row.setStatus(relation.getStatus()); row.setActivatedAt(relation.getActivatedAt());
            row.setAcceptanceStatus(relation.getAcceptanceStatus()); row.setAcceptedAt(relation.getAcceptedAt());
            row.setVersion(relation.getVersion()); row.setOwner(Objects.equals(userId, relation.getOwnerUserId()));
            row.setOwnerUserId(relation.getOwnerUserId());
            AdminUserRespDTO owner = relation.getOwnerUserId() == null ? null : collaborators.get(relation.getOwnerUserId());
            row.setOwnerUserName(owner == null ? null : owner.getNickname());
            row.setContentDirectorUserId(relation.getContentDirectorUserId());
            AdminUserRespDTO director = relation.getContentDirectorUserId() == null ? null : collaborators.get(relation.getContentDirectorUserId());
            row.setContentDirectorUserName(director == null ? null : director.getNickname());
            row.setCareerPlannerUserId(relation.getCareerPlannerUserId());
            AdminUserRespDTO planner = relation.getCareerPlannerUserId() == null ? null : collaborators.get(relation.getCareerPlannerUserId());
            row.setCareerPlannerUserName(planner == null ? null : planner.getNickname());
            row.setOperatorUserId(relation.getOperatorUserId());
            AdminUserRespDTO operator = relation.getOperatorUserId() == null ? null : collaborators.get(relation.getOperatorUserId());
            row.setOperatorUserName(operator == null ? null : operator.getNickname());
            row.setDirectorStage(relation.getDirectorStage());
            row.setDirectorInterviewAt(relation.getDirectorInterviewAt());
            return row;
        }).toList());
        return result;
    }

    private void populateCourseRights(MyStudentRespVO.ServiceVO row, String productSnapshot) {
        row.setProductSnapshot(productSnapshot);
        if (StrUtil.isBlank(productSnapshot)) {
            row.setCourseName("历史课程信息缺失");
            return;
        }
        try {
            LeadProductSnapshot snapshot = JsonUtils.parseObject(productSnapshot, LeadProductSnapshot.class);
            if (snapshot == null) {
                row.setCourseName("历史课程信息缺失");
                return;
            }
            row.setCourseName(snapshot.name());
            row.setSkuName(snapshot.skuName());
            row.setCategoryPath(snapshot.categoryPath() == null ? List.of() : snapshot.categoryPath().stream()
                    .map(node -> node.name()).filter(StrUtil::isNotBlank).toList());
            row.setSpecs(snapshot.displaySpecs());
            if (StrUtil.isNotBlank(snapshot.selectedAttrValuesJson())) {
                Map<?, ?> values = JsonUtils.parseObject(snapshot.selectedAttrValuesJson(), Map.class);
                if (values != null) {
                    row.setAttributeValues(values.values().stream().filter(Objects::nonNull).map(String::valueOf)
                            .filter(StrUtil::isNotBlank).distinct().toList());
                }
            }
        } catch (RuntimeException exception) {
            // Historical snapshots remain readable even when an old payload cannot be normalized.
            log.warn("[populateCourseRights][serviceRelationId({}) product snapshot is invalid]", row.getServiceRelationId());
            row.setCourseName("历史课程信息缺失");
        }
    }

}
