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
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
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

    @Override
    public PageResult<MyStudentRespVO> getMyPage(Long userId, MyStudentPageReqVO reqVO) {
        Map<Long, List<ServiceRelationDO>> groups = selectAssignedRelations(userId).stream()
                .collect(Collectors.groupingBy(ServiceRelationDO::getPersonId, LinkedHashMap::new, Collectors.toList()));
        List<Long> matchedIds = advancedFilterService.matchStudentPersonIds(reqVO.getAdvancedFilter(), userId);
        PageResult<PersonDO> people = personMapper.selectStudentPage(reqVO, groups.keySet(), matchedIds);
        return new PageResult<>(people.getList().stream().map(person -> convert(userId, person.getId(), groups.get(person.getId())))
                .toList(), people.getTotal());
    }

    @Override
    public PageResult<MyStudentRespVO> getMediaPage(Long userId, MyStudentPageReqVO reqVO) {
        Map<Long, ServiceRelationDO> visibleRelations = new LinkedHashMap<>();
        relationMapper.selectActiveByContentDirector(userId).forEach(row -> visibleRelations.put(row.getId(), row));
        relationMapper.selectActiveByPersonIds(mediaAccountMapper.selectParticipantStudentIds(userId))
                .forEach(row -> visibleRelations.put(row.getId(), row));
        Map<Long, List<ServiceRelationDO>> groups = visibleRelations.values().stream()
                .collect(Collectors.groupingBy(ServiceRelationDO::getPersonId, LinkedHashMap::new, Collectors.toList()));
        PageResult<PersonDO> people = personMapper.selectStudentPage(reqVO, groups.keySet(), null);
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
        List<ServiceRelationDO> relations = selectAssignedRelationsForPerson(userId, personId);
        if (relations.isEmpty()) throw exception(STUDENT_NOT_EXISTS);
        return convert(userId, personId, relations);
    }

    @Override
    public MyStudentRespVO getMediaStudent(Long userId, Long personId) {
        Map<Long, ServiceRelationDO> visibleRelations = new LinkedHashMap<>();
        relationMapper.selectActiveByContentDirectorAndPerson(userId, personId)
                .forEach(row -> visibleRelations.put(row.getId(), row));
        if (!mediaAccountMapper.selectByParticipantAndStudent(userId, personId).isEmpty()) {
            relationMapper.selectActiveByPersonIds(List.of(personId))
                    .forEach(row -> visibleRelations.put(row.getId(), row));
        }
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
        if (relation == null || !"active".equals(relation.getStatus())) throw exception(STUDENT_NOT_EXISTS);
        List<ServiceRelationDO> relations = selectAssignedRelationsForPerson(userId, relation.getPersonId());
        if (relations.stream().noneMatch(item -> Objects.equals(item.getId(), relationId))) {
            throw exception(STUDENT_NOT_EXISTS);
        }
        return convert(userId, relation.getPersonId(), relations);
    }

    private List<ServiceRelationDO> selectAssignedRelationsForPerson(Long userId, Long personId) {
        List<ServiceRelationDO> owned = relationMapper.selectActiveByOwnerAndPerson(userId, personId);
        Map<Long, ServiceRelationDO> result = new LinkedHashMap<>();
        owned.forEach(relation -> result.put(relation.getId(), relation));
        relationMapper.selectActiveByCollaboratorAndPerson(userId, personId).stream()
                .forEach(relation -> result.put(relation.getId(), relation));
        return new ArrayList<>(result.values());
    }

    private List<ServiceRelationDO> selectAssignedRelations(Long userId) {
        List<ServiceRelationDO> owned = relationMapper.selectByOwnerUserId(userId);
        Map<Long, ServiceRelationDO> result = new LinkedHashMap<>();
        owned.forEach(relation -> result.put(relation.getId(), relation));
        relationMapper.selectActiveByCollaborator(userId)
                .forEach(relation -> result.put(relation.getId(), relation));
        return new ArrayList<>(result.values());
    }

    private MyStudentRespVO convert(Long userId, Long personId, List<ServiceRelationDO> relations) {
        PersonDO person = personMapper.selectById(personId);
        if (person == null) throw exception(STUDENT_NOT_EXISTS);
        Set<Long> orderIds = relations.stream().map(ServiceRelationDO::getOrderId).collect(Collectors.toSet());
        Map<Long, SalesOrderDO> orders = orderMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(SalesOrderDO::getId, Function.identity()));
        Set<Long> itemIds = relations.stream().map(ServiceRelationDO::getOrderItemId).collect(Collectors.toSet());
        Map<Long, SalesOrderItemDO> items = orderItemMapper.selectBatchIds(itemIds).stream()
                .collect(Collectors.toMap(SalesOrderItemDO::getId, Function.identity()));
        Set<Long> collaboratorIds = relations.stream()
                .flatMap(relation -> java.util.stream.Stream.of(relation.getContentDirectorUserId(), relation.getCareerPlannerUserId()))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, AdminUserRespDTO> collaborators = collaboratorIds.isEmpty() || adminUserApi == null ? Map.of() : Optional.ofNullable(adminUserApi.getUserMap(collaboratorIds)).orElseGet(Map::of);
        Set<Long> leadIds = orders.values().stream().map(SalesOrderDO::getLeadId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, LeadDO> leads = leadIds.isEmpty() ? Map.of() : leadMapper.selectBatchIds(leadIds).stream()
                .collect(Collectors.toMap(LeadDO::getId, Function.identity()));
        MyStudentRespVO result = new MyStudentRespVO();
        result.setPersonId(personId);
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
        result.setActivatedAt(relations.stream().map(ServiceRelationDO::getActivatedAt).max(Comparator.naturalOrder()).orElse(null));
        result.setServices(relations.stream().map(relation -> {
            MyStudentRespVO.ServiceVO row = new MyStudentRespVO.ServiceVO();
            row.setServiceRelationId(relation.getId()); row.setOrderId(relation.getOrderId()); row.setOrderItemId(relation.getOrderItemId());
            SalesOrderDO order = orders.get(relation.getOrderId()); SalesOrderItemDO item = items.get(relation.getOrderItemId());
            LeadDO serviceLead = order == null || order.getLeadId() == null ? null : leads.get(order.getLeadId());
            row.setLeadId(serviceLead == null ? null : serviceLead.getId());
            row.setLeadNo(serviceLead == null ? null : serviceLead.getLeadNo());
            row.setOrderNo(order == null ? null : order.getOrderNo());
            populateCourseRights(row, item == null ? relation.getServiceSnapshot() : item.getProductSnapshot());
            row.setStatus(relation.getStatus()); row.setActivatedAt(relation.getActivatedAt());
            row.setAcceptanceStatus(relation.getAcceptanceStatus()); row.setAcceptedAt(relation.getAcceptedAt());
            row.setVersion(relation.getVersion()); row.setOwner(Objects.equals(userId, relation.getOwnerUserId()));
            row.setContentDirectorUserId(relation.getContentDirectorUserId());
            AdminUserRespDTO director = relation.getContentDirectorUserId() == null ? null : collaborators.get(relation.getContentDirectorUserId());
            row.setContentDirectorUserName(director == null ? null : director.getNickname());
            row.setCareerPlannerUserId(relation.getCareerPlannerUserId());
            AdminUserRespDTO planner = relation.getCareerPlannerUserId() == null ? null : collaborators.get(relation.getCareerPlannerUserId());
            row.setCareerPlannerUserName(planner == null ? null : planner.getNickname());
            return row;
        }).toList());
        return result;
    }

    private void populateCourseRights(MyStudentRespVO.ServiceVO row, String productSnapshot) {
        row.setProductSnapshot(productSnapshot);
        if (StrUtil.isBlank(productSnapshot)) return;
        try {
            LeadProductSnapshot snapshot = JsonUtils.parseObject(productSnapshot, LeadProductSnapshot.class);
            if (snapshot == null) return;
            row.setCourseName(snapshot.name());
            row.setSkuName(snapshot.skuName());
            row.setCategoryPath(snapshot.categoryPath() == null ? List.of() : snapshot.categoryPath().stream()
                    .map(node -> node.name()).filter(StrUtil::isNotBlank).toList());
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
        }
    }

}
