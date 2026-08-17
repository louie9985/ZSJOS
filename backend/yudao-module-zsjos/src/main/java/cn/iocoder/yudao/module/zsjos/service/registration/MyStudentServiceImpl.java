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

    @Override
    public PageResult<MyStudentRespVO> getMyPage(Long userId, MyStudentPageReqVO reqVO) {
        Map<Long, List<ServiceRelationDO>> groups = relationMapper.selectByOwnerUserId(userId).stream()
                .collect(Collectors.groupingBy(ServiceRelationDO::getPersonId, LinkedHashMap::new, Collectors.toList()));
        List<MyStudentRespVO> rows = groups.entrySet().stream().map(entry -> convert(entry.getKey(), entry.getValue()))
                .filter(row -> matches(row, reqVO.getKeyword()))
                .sorted(Comparator.comparing(MyStudentRespVO::getActivatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(MyStudentRespVO::getPersonId, Comparator.reverseOrder())).toList();
        int from = Math.min((reqVO.getPageNo() - 1) * reqVO.getPageSize(), rows.size());
        int to = Math.min(from + reqVO.getPageSize(), rows.size());
        return new PageResult<>(rows.subList(from, to), (long) rows.size());
    }

    @Override
    @ZsjosPermission(bizType = "student", bizId = "#personId", action = "read")
    public MyStudentRespVO getMyStudent(Long userId, Long personId) {
        List<ServiceRelationDO> relations = relationMapper.selectByOwnerAndPerson(userId, personId);
        if (relations.isEmpty()) throw exception(STUDENT_NOT_EXISTS);
        return convert(personId, relations);
    }

    private MyStudentRespVO convert(Long personId, List<ServiceRelationDO> relations) {
        PersonDO person = personMapper.selectById(personId);
        if (person == null) throw exception(STUDENT_NOT_EXISTS);
        LeadDO lead = leadMapper.selectByPersonId(personId);
        Set<Long> orderIds = relations.stream().map(ServiceRelationDO::getOrderId).collect(Collectors.toSet());
        Map<Long, SalesOrderDO> orders = orderMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(SalesOrderDO::getId, Function.identity()));
        Set<Long> itemIds = relations.stream().map(ServiceRelationDO::getOrderItemId).collect(Collectors.toSet());
        Map<Long, SalesOrderItemDO> items = orderItemMapper.selectBatchIds(itemIds).stream()
                .collect(Collectors.toMap(SalesOrderItemDO::getId, Function.identity()));
        MyStudentRespVO result = new MyStudentRespVO();
        result.setPersonId(personId); result.setLeadId(lead == null ? null : lead.getId());
        result.setLeadNo(lead == null ? null : lead.getLeadNo()); result.setName(person.getName());
        result.setMobile(person.getMobile()); result.setWechatId(person.getWechatId());
        result.setActivatedAt(relations.stream().map(ServiceRelationDO::getActivatedAt).max(Comparator.naturalOrder()).orElse(null));
        result.setServices(relations.stream().map(relation -> {
            MyStudentRespVO.ServiceVO row = new MyStudentRespVO.ServiceVO();
            row.setServiceRelationId(relation.getId()); row.setOrderId(relation.getOrderId()); row.setOrderItemId(relation.getOrderItemId());
            SalesOrderDO order = orders.get(relation.getOrderId()); SalesOrderItemDO item = items.get(relation.getOrderItemId());
            row.setOrderNo(order == null ? null : order.getOrderNo());
            populateCourseRights(row, item == null ? relation.getServiceSnapshot() : item.getProductSnapshot());
            row.setStatus(relation.getStatus()); row.setActivatedAt(relation.getActivatedAt()); return row;
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

    private boolean matches(MyStudentRespVO row, String keyword) {
        if (keyword == null || keyword.isBlank()) return true;
        String value = keyword.trim();
        return java.util.stream.Stream.of(row.getName(), row.getMobile(), row.getWechatId(), row.getLeadNo())
                .filter(Objects::nonNull).anyMatch(item -> item.contains(value));
    }
}
