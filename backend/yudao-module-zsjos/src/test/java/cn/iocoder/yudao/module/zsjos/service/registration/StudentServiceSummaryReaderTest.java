package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.StudentContactRecordMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.StudentInfoFormMapper;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderObjectPermissionService;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadObjectPermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentServiceSummaryReaderTest {
    @org.junit.jupiter.api.BeforeEach void initializeMetadata() {
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "student-summary-test");
        assistant.setCurrentNamespace("student-summary-test");
        for (var type : List.of(BusinessTaskDO.class, cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentContactRecordDO.class,
                cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormDO.class))
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, type);
    }
    @Mock cn.iocoder.yudao.module.zsjos.dal.mysql.registration.TeachingServiceEventMapper lifecycleEvents;
    @Test void closureReasonUsesCurrentServiceEventAndDoesNotLeakToRestoredServices() {
        var closed = relation(1); closed.setTeachingClosed(true);
        closed.setTerminationReason("订单取消原因");
        var restored = relation(2); restored.setTeachingClosed(false);
        var missing = relation(3); missing.setTeachingClosed(true);
        var event = new cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.TeachingServiceEventDO();
        event.setServiceRelationId(1L); event.setReason("学员申请关闭本课程");
        when(lifecycleEvents.latestClosures(List.of(1L, 3L))).thenReturn(List.of(event));
        var result = reader.read(7L, List.of(closed, restored, missing), LocalDateTime.now());
        assertEquals("学员申请关闭本课程", result.get(1L).getCloseReason());
        assertNull(result.get(2L).getCloseReason());
        assertNull(result.get(3L).getCloseReason());
        verify(lifecycleEvents).latestClosures(List.of(1L, 3L));
    }
    @InjectMocks StudentServiceSummaryReader reader;
    @Mock BusinessTaskMapper tasks; @Mock StudentContactRecordMapper contacts;
    @Mock DeliveryClassMapper classes; @Mock SalesOrderMapper orders; @Mock SalesOrderApprovalRoundMapper rounds;
    @Mock SalesOrderObjectPermissionService orderPermission; @Mock LeadMapper leads;
    @Mock LeadObjectPermissionService leadPermission; @Mock StudentInfoFormMapper forms;
    @Mock PermissionApi permission; @Mock AdminUserApi users;
    @Test void batchesEachAssociationAndKeepsTasksOnTheirService() {
        var now = LocalDateTime.of(2026,10,15,12,0);
        var a = relation(1); var b = relation(2);
        var task = new BusinessTaskDO(); task.setId(8L); task.setBizId(2L); task.setTaskType("student_contact"); task.setStatus("pending"); task.setDueAt(now);
        when(tasks.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(task));
        var result = reader.read(7L,List.of(a,b),now);
        assertNull(result.get(1L).getCurrentTask()); assertFalse(result.get(2L).getCurrentTask().getOverdue());
        assertEquals("absent",result.get(1L).getOrderHandoff().getAccess());
        verify(tasks,times(1)).selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        verify(contacts,times(1)).selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        verifyNoInteractions(forms, rounds, leads, leadPermission);
    }
    @Test void deniedOrderDoesNotExposeHandoffOrLoadForms() {
        var relation=relation(1);relation.setOrderId(4L); relation.setRegistrationCaseId(5L);
        var order=new SalesOrderDO();order.setId(4L);order.setLeadId(9L);order.setRemark("restricted");order.setCurrentApprovalRoundId(6L);
        when(orders.selectBatchIds(List.of(4L))).thenReturn(List.of(order));
        var summary=reader.read(7L,List.of(relation),LocalDateTime.now()).get(1L);
        assertEquals("denied",summary.getOrderHandoff().getAccess()); assertNull(summary.getOrderHandoff().getRemark());
        assertEquals("denied",summary.getInfoSubmission().getAccess()); assertNull(summary.getInfoSubmission().getStatus());
        verifyNoInteractions(forms, rounds, leads, orderPermission, leadPermission);
    }
    @Test void emptyPageDoesNotReadAssociations() {
        assertTrue(reader.read(7L,List.of(),LocalDateTime.now()).isEmpty());
        verifyNoInteractions(tasks,contacts,orders,forms,permission);
    }
    @Test void currentRegistrationFormOnlyMetadataIsSelected() {
        var relation=relation(1); relation.setOrderId(4L); relation.setRegistrationCaseId(5L);
        var order=new SalesOrderDO(); order.setId(4L); order.setLeadId(9L);
        when(orders.selectBatchIds(List.of(4L))).thenReturn(List.of(order));
        when(permission.hasAnyPermissions(7L, "zsjos:student-info-form:read")).thenReturn(true);
        doReturn(false).when(permission).hasAnyPermissions(7L, "zsjos:sales-order:query", "zsjos:sales-order:query-management", "zsjos:sales-order:query-team", "zsjos:sales-order:review", "zsjos:sales-order:supervisor-confirm", "zsjos:sales-order:create");
        var submitted=new cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormDO(); submitted.setId(10L);submitted.setRegistrationCaseId(5L);submitted.setStatus("SUBMITTED");submitted.setSubmittedAt(LocalDateTime.of(2026,10,1,12,0));
        when(forms.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenAnswer(invocation -> {
            var query=(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>)invocation.getArgument(0);
            assertFalse(query.getSqlSelect().contains("token")); assertFalse(query.getSqlSelect().contains("value"));
            return List.of(submitted);
        });
        var info=reader.read(7L,List.of(relation),LocalDateTime.now()).get(1L).getInfoSubmission();
        assertEquals("readable",info.getAccess());assertEquals("SUBMITTED",info.getStatus());assertEquals(submitted.getSubmittedAt(),info.getSubmittedAt());
    }
    @Test void orderObjectAccessNeverSubstitutesForFeaturePermission() {
        var order=new SalesOrderDO();order.setId(4L);
        assertTrue(reader.readableOrders(List.of(order),7L).isEmpty());verifyNoInteractions(orderPermission);
    }
    private ServiceRelationDO relation(long id) { var r=new ServiceRelationDO();r.setId(id);r.setPersonId(9L);return r; }
}
