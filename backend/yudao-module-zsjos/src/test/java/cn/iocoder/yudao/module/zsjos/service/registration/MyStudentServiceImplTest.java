package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ZsjosProductCategoryPathNodeVO;
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
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyStudentServiceImplTest {

    @InjectMocks private MyStudentServiceImpl service;
    @Mock private ServiceRelationMapper relationMapper;
    @Mock private PersonMapper personMapper;
    @Mock private LeadMapper leadMapper;
    @Mock private SalesOrderMapper orderMapper;
    @Mock private SalesOrderItemMapper orderItemMapper;

    @Test
    void getMyStudentReturnsStructuredCourseRights() {
        ServiceRelationDO relation = new ServiceRelationDO();
        relation.setId(1L); relation.setPersonId(2L); relation.setOrderId(3L); relation.setOrderItemId(4L);
        relation.setOwnerUserId(8L); relation.setStatus("active");
        relation.setActivatedAt(LocalDateTime.of(2026, 8, 17, 12, 0));
        PersonDO person = new PersonDO(); person.setId(2L); person.setName("测试学员");
        LeadDO lead = new LeadDO(); lead.setId(5L); lead.setLeadNo("KZ202608170001");
        SalesOrderDO order = new SalesOrderDO(); order.setId(3L); order.setLeadId(5L); order.setOrderNo("OD202608170001");
        SalesOrderItemDO item = new SalesOrderItemDO(); item.setId(4L); item.setOrderId(3L);
        LeadProductSnapshot snapshot = new LeadProductSnapshot("course-1", "营养课程", 10L, "基础课程",
                List.of(new ZsjosProductCategoryPathNodeVO(7L, "中医营养学"),
                        new ZsjosProductCategoryPathNodeVO(10L, "基础课程")),
                7L, "中医营养学", 10L, "基础课程", "sku-1", "营养课程 - 线上班",
                "{\"delivery\":\"线上\",\"period\":\"一天\"}", BigDecimal.ZERO, false, false);
        item.setProductSnapshot(JsonUtils.toJsonString(snapshot));

        when(relationMapper.selectActiveByOwnerAndPerson(8L, 2L)).thenReturn(List.of(relation));
        when(relationMapper.selectActiveByCollaboratorAndPerson(8L, 2L)).thenReturn(List.of());
        when(personMapper.selectById(2L)).thenReturn(person);
        when(leadMapper.selectById(5L)).thenReturn(lead);
        when(orderMapper.selectBatchIds(Set.of(3L))).thenReturn(List.of(order));
        when(orderItemMapper.selectBatchIds(Set.of(4L))).thenReturn(List.of(item));

        MyStudentRespVO student = service.getMyStudent(8L, 2L);
        MyStudentRespVO.ServiceVO result = student.getServices().getFirst();

        assertEquals(5L, student.getLeadId());
        assertEquals("营养课程", result.getCourseName());
        assertEquals("营养课程 - 线上班", result.getSkuName());
        assertEquals(List.of("中医营养学", "基础课程"), result.getCategoryPath());
        assertEquals(List.of("线上", "一天"), result.getAttributeValues());
    }

    @Test
    void getMyStudentIncludesServiceRelationCollaboratorAssignment() {
        ServiceRelationDO relation = new ServiceRelationDO();
        relation.setId(11L); relation.setPersonId(12L); relation.setOrderId(13L); relation.setOrderItemId(14L);
        relation.setRegistrationCaseId(15L); relation.setStatus("active"); relation.setActivatedAt(LocalDateTime.now());
        relation.setContentDirectorUserId(18L); relation.setAcceptanceStatus("accepted"); relation.setVersion(3);
        PersonDO person = new PersonDO(); person.setId(12L); person.setName("编导学员");
        SalesOrderDO order = new SalesOrderDO(); order.setId(13L); order.setOrderNo("OD202608180001");
        SalesOrderItemDO item = new SalesOrderItemDO(); item.setId(14L); item.setOrderId(13L);

        when(relationMapper.selectActiveByOwnerAndPerson(18L, 12L)).thenReturn(List.of());
        when(relationMapper.selectActiveByCollaboratorAndPerson(18L, 12L)).thenReturn(List.of(relation));
        when(personMapper.selectById(12L)).thenReturn(person);
        when(orderMapper.selectBatchIds(Set.of(13L))).thenReturn(List.of(order));
        when(orderItemMapper.selectBatchIds(Set.of(14L))).thenReturn(List.of(item));

        MyStudentRespVO result = service.getMyStudent(18L, 12L);

        assertEquals("编导学员", result.getName());
        assertEquals(1, result.getServices().size());
        assertEquals("accepted", result.getServices().getFirst().getAcceptanceStatus());
        assertEquals(false, result.getServices().getFirst().getOwner());
        assertEquals(18L, result.getServices().getFirst().getContentDirectorUserId());
    }
}
