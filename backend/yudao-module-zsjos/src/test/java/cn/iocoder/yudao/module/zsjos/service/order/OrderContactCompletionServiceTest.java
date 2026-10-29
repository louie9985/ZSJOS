package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.service.lead.PersonIdentityWriteService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class OrderContactCompletionServiceTest {
    @InjectMocks OrderContactCompletionService service;
    @Mock PersonMapper people;
    @Mock LeadMapper leads;
    @Mock PersonIdentityWriteService writer;
    @Mock BusinessEventMapper events;
    PersonDO person;
    LeadDO lead;
    SalesOrderDO order;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        person=new PersonDO().setId(10L).setName("测试客户").setMobile("13800138000");person.setTenantId(1L);
        lead=new LeadDO();lead.setId(20L);lead.setPersonId(10L);lead.setTenantId(1L);lead.setSubmittedMobile("13800138000");
        order=new SalesOrderDO();order.setId(30L);order.setPersonId(10L);order.setStudentMobile("13800138000");order.setStudentWechatId("new-wx");
        lenient().when(people.selectByIdForUpdate(10L,1L)).thenReturn(person);
        lenient().when(leads.selectByIdForUpdate(20L,1L)).thenReturn(lead);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    @Test void completesOnlyExplicitLeadAndPersonWithPrivateAudit() {
        service.complete(order,20L,99L);
        verify(writer).update(10L,"测试客户","13800138000","new-wx");
        assertEquals("new-wx",lead.getSubmittedWechatId());
        var event=ArgumentCaptor.forClass(BusinessEventDO.class);verify(events).insert(event.capture());
        assertFalse(event.getValue().getRelatedObjectRefs().contains("new-wx"));
        assertFalse(event.getValue().getRelatedObjectRefs().contains("13800138000"));
    }
    @Test void personOnlyDoesNotLookUpOtherLeads() {
        service.complete(order,null,99L);verifyNoInteractions(leads);
        verify(writer).update(10L,"测试客户","13800138000","new-wx");
    }
    @Test void repurchasePreservesSnapshotWithoutChangingIdentityOrClaims() {
        order.setOrderType("repurchase");
        service.complete(order,20L,99L);
        assertEquals("new-wx", order.getStudentWechatId());
        assertNull(person.getWechatId());
        assertNull(lead.getSubmittedWechatId());
        verifyNoInteractions(writer,events);
        verify(leads,never()).updateById(any(LeadDO.class));
    }
    @Test void existingContactsAreNeverOverwrittenAndReplayDoesNothing() {
        person.setWechatId("existing");lead.setSubmittedWechatId("existing");
        service.complete(order,20L,99L);service.complete(order,20L,99L);
        verifyNoInteractions(writer,events);verify(leads,never()).updateById(any(LeadDO.class));
    }
    @Test void completionRereadsCurrentValuesBeforeFilling() {
        when(writer.update(anyLong(),anyString(),anyString(),anyString())).thenAnswer(a->{person.setWechatId(a.getArgument(3));return person;});
        service.complete(order,20L,99L);order.setId(31L);order.setStudentWechatId("second-wx");
        service.complete(order,20L,98L);
        assertEquals("new-wx",person.getWechatId());assertEquals("new-wx",lead.getSubmittedWechatId());
        verify(writer,times(1)).update(anyLong(),anyString(),anyString(),anyString());
    }
    @Test void leadCompletionCannotConflictWithKnownPersonContact() {
        person.setWechatId("existing");
        assertThrows(RuntimeException.class,()->service.complete(order,20L,99L));verifyNoInteractions(writer,events);
    }
    @Test void occupiedContactStopsBeforeLeadAndAuditWrite() {
        when(writer.update(anyLong(),anyString(),anyString(),anyString())).thenThrow(new IllegalStateException("contact occupied"));
        assertThrows(IllegalStateException.class,()->service.complete(order,20L,99L));
        verify(leads,never()).updateById(any(LeadDO.class));verifyNoInteractions(events);
    }
    @Test void mismatchedOrForeignLeadCannotBeMutated() {
        lead.setPersonId(11L);assertThrows(RuntimeException.class,()->service.complete(order,20L,99L));
        lead.setPersonId(10L);lead.setTenantId(2L);assertThrows(RuntimeException.class,()->service.complete(order,20L,99L));
        verifyNoInteractions(people,writer,events);
    }
    @Test void blankContactsCannotBeCompleted() {
        order.setStudentWechatId("  ");assertThrows(RuntimeException.class,()->service.complete(order,null,99L));verifyNoInteractions(writer,events);
    }
}
