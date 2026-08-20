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
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
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
    @ZsjosPermission(bizType = "student", bizId = "#personId", action = "read")
    public MyStudentRespVO getMyStudent(Long userId, Long personId) {
        List<ServiceRelationDO> relations = selectAssignedRelationsForPerson(userId, personId);
        if (relations.isEmpty()) throw exception(STUDENT_NOT_EXISTS);
        return convert(userId, personId, relations);
    }

    private List<ServiceRelationDO> selectAssignedRelationsForPerson(Long userId, Long personId) {
        List<ServiceRelationDO> owned = relationMapper.selectActiveByOwnerAndPerson(userId, personId);
        Map<Long, ServiceRelationDO> result = new LinkedHashMap<>();
        owned.forEach(relation -> result.put(relation.getId(), relation));
        relationMapper.selectActiveByCollaborator(userId).stream()
                .filter(relation -> Objects.equals(relation.getPersonId(), personId))
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
            row.setOrderNo(order == null ? null : order.getOrderNo());
            populateCourseRights(row, item == null ? relation.getServiceSnapshot() : item.getProductSnapshot());
            row.setStatus(relation.getStatus()); row.setActivatedAt(relation.getActivatedAt());
            row.setAcceptanceStatus(relation.getAcceptanceStatus()); row.setAcceptedAt(relation.getAcceptedAt());
            row.setVersion(relation.getVersion()); row.setOwner(Objects.equals(userId, relation.getOwnerUserId()));
            row.setContentDirectorUserId(relation.getContentDirectorUserId());
            row.setCareerPlannerUserId(relation.getCareerPlannerUserId());
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
