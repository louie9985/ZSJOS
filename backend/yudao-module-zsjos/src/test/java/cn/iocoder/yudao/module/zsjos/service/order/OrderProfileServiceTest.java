package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.service.lead.PersonIdentityWriteService;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderProfileServiceTest {
    @InjectMocks OrderProfileService service;
    @Mock PersonMapper people;
    @Mock LeadMapper leads;
    @Mock SalesOrderMapper orders;
    @Mock RepurchaseCustomerService customers;
    @Mock PersonIdentityWriteService writer;
    @Mock BusinessEventMapper events;
    PersonDO person;
    SalesOrderDO order;
    OrderProfileUpdateReqVO choice;
    MockedStatic<EncryptTypeHandler> crypto;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        person = new PersonDO().setId(10L).setName("旧姓名").setMobile("13800000001").setWechatId("old-demo");person.setTenantId(1L);
        order = new SalesOrderDO();order.setId(30L);order.setPersonId(10L);order.setStudentName("新姓名");order.setStudentMobile("13800000002");order.setStudentWechatId("new-demo");
        choice = new OrderProfileUpdateReqVO();choice.setFingerprint(OrderProfileService.snapshot(person).getFingerprint());choice.setFields(List.of("name","mobile","wechatId"));
        lenient().when(people.selectByIdForUpdate(10L,1L)).thenReturn(person);
        crypto=mockStatic(EncryptTypeHandler.class);crypto.when(()->EncryptTypeHandler.encrypt(anyString())).thenReturn("encrypted-audit");
    }
    @AfterEach void cleanup(){ crypto.close();TenantContextHolder.clear(); }
    @Test void updatesChosenFieldsWithPrivateAuditAndNoLeadWrite(){
        choice.setFields(List.of("name"));service.apply(order,choice,99L);
        verify(writer).update(10L,"新姓名","13800000001","old-demo");verifyNoInteractions(leads);
        var event=ArgumentCaptor.forClass(BusinessEventDO.class);verify(events).insert(event.capture());
        assertTrue(event.getValue().getRelatedObjectRefs().contains("encrypted-audit"));
        assertFalse(event.getValue().getRelatedObjectRefs().contains("13800000001"));
    }
    @Test void keepExistingDoesNotWrite(){choice.setFields(List.of());service.apply(order,choice,99L);verifyNoInteractions(writer,events,leads);}
    @Test void emptyFieldsAreCompleted(){person.setWechatId(null);choice.setFingerprint(OrderProfileService.snapshot(person).getFingerprint());choice.setFields(List.of());service.apply(order,choice,99L);verify(writer).update(10L,"旧姓名","13800000001","new-demo");}
    @Test void staleConfirmationFailsEvenIfKeepingAll(){person.setName("另一个修改");choice.setFields(List.of());assertThrows(RuntimeException.class,()->service.apply(order,choice,99L));verifyNoInteractions(writer,events);}
    @Test void foreignTenantCannotBeUpdated(){person.setTenantId(2L);assertThrows(RuntimeException.class,()->service.apply(order,choice,99L));verifyNoInteractions(writer,events);}
    @Test void contactConflictStopsAudit(){doThrow(new IllegalStateException("occupied")).when(writer).update(anyLong(),anyString(),anyString(),anyString());assertThrows(RuntimeException.class,()->service.apply(order,choice,99L));verifyNoInteractions(events);}
    @Test void repurchaseUsesSameSelectivePolicy(){order.setOrderType("repurchase");service.apply(order,choice,99L);verify(writer).update(10L,"新姓名","13800000002","new-demo");}
    @Test void newCustomerMustStillMatch(){choice.setFingerprint("new");choice.setFields(List.of());assertThrows(RuntimeException.class,()->service.apply(order,choice,99L));person.setName(order.getStudentName()).setMobile(order.getStudentMobile()).setWechatId(order.getStudentWechatId());service.apply(order,choice,99L);verifyNoInteractions(writer,events);}
    @Test void customerFailureDoesNotExposeProfile(){var response=new RepurchaseCustomerCheckRespVO();response.setCanRepurchase(false);when(customers.checkCustomer(eq(99L),any())).thenReturn(response);assertThrows(RuntimeException.class,()->service.forCustomer(99L,new RepurchaseCustomerCheckReqVO()));verify(people,never()).selectById(anyLong());}
    @Test void objectReadersRetainCommandPermissionAnnotations() throws Exception {
        assertEquals("enter-deal",OrderProfileService.class.getMethod("forLead",Long.class).getAnnotation(ZsjosPermission.class).action());
        assertEquals("repurchase",OrderProfileService.class.getMethod("forStudent",Long.class).getAnnotation(ZsjosPermission.class).action());
        assertEquals("revise",OrderProfileService.class.getMethod("forOrder",Long.class).getAnnotation(ZsjosPermission.class).action());
    }
}
