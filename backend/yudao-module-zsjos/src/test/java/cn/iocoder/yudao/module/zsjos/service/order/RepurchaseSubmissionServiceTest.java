package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.service.lead.PersonIdentityWriteService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class RepurchaseSubmissionServiceTest {
    @InjectMocks RepurchaseSubmissionService service;
    @Mock RepurchaseCustomerService customers;
    @Mock PersonMapper personMapper;
    @Mock PersonIdentityWriteService identityWriter;
    @Mock SalesOrderService orders;
    @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);}
    @AfterEach void clean(){TenantContextHolder.clear();}
    private PersonDO person(){var p=new PersonDO().setId(10L);p.setTenantId(1L);return p;}
    private SalesOrderRepurchaseReqVO req(){var r=new SalesOrderRepurchaseReqVO();r.setCustomerName("测试");r.setCustomerMobile("13800138000");return r;}
    @Test void matchedCustomerDoesNotCreatePersonAndUsesProxiedOrderService(){
        when(personMapper.selectDuplicateCandidates("13800138000",null)).thenReturn(List.of(person()));
        var r=req();when(orders.createMatchedRepurchase(10L,20L,r)).thenReturn(99L);
        assertEquals(99L,service.submit(20L,r));verify(customers).requireIdentity(10L,r);verifyNoInteractions(identityWriter);
    }
    @Test void noMatchUsesExistingIdentityReservation(){
        when(identityWriter.resolveOrCreate("测试","13800138000",null,"active")).thenReturn(person());
        var r=req();service.submit(20L,r);verify(customers).requireIdentity(10L,r);verify(orders).createMatchedRepurchase(10L,20L,r);
    }
    @Test void stalePreflightTargetCannotSwitchCustomer(){
        when(personMapper.selectDuplicateCandidates("13800138000",null)).thenReturn(List.of(person()));var r=req();r.setExpectedPersonId(11L);
        assertThrows(RuntimeException.class,()->service.submit(20L,r));verifyNoInteractions(identityWriter,orders);
    }
    @Test void identityConflictStopsBeforeOrder(){
        when(personMapper.selectDuplicateCandidates("13800138000",null)).thenReturn(List.of(person(),person().setId(11L)));
        assertThrows(RuntimeException.class,()->service.submit(20L,req()));verifyNoInteractions(identityWriter,orders);
    }
    @Test void blankContactFailsWithoutIdentityWrite(){var r=req();r.setCustomerMobile(" ");assertThrows(RuntimeException.class,()->service.submit(20L,r));verifyNoInteractions(identityWriter,orders,personMapper);}
}
