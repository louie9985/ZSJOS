package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadSubmissionIdentityService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class RepurchaseCustomerServiceTest {
    @InjectMocks RepurchaseCustomerService service;
    @Mock PersonMapper personMapper;
    @Mock LeadMapper leadMapper;
    @Mock SalesOrderMapper orderMapper;
    @Mock PermissionApi permissionApi;
    @Mock LeadSubmissionIdentityService identityService;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        lenient().when(permissionApi.hasAnyPermissions(20L, "zsjos:sales-order:create")).thenReturn(true);
    }
    @AfterEach void clean() { TenantContextHolder.clear(); }
    private RepurchaseCustomerCheckReqVO req() {
        var r = new RepurchaseCustomerCheckReqVO(); r.setCustomerName("测试学员"); r.setCustomerMobile("13800138000"); return r;
    }
    private PersonDO person() { var p = new PersonDO().setId(10L).setName("测试学员").setMobile("13800138000"); p.setTenantId(1L); return p; }
    private void match() { when(personMapper.selectDuplicateCandidates("13800138000", null)).thenReturn(List.of(person())); }
    @Test void noMatchHasNoWrites() {
        var r = service.checkCustomer(20L, req()); assertTrue(r.isCanRepurchase()); assertEquals("NO_MATCH",r.getMatchStatus());
        verifyNoInteractions(leadMapper, orderMapper);
    }
    @Test void blankContactDoesNotQuery() { var r=req();r.setCustomerMobile(" ");assertFalse(service.checkCustomer(20L,r).isCanRepurchase());verifyNoInteractions(personMapper); }
    @Test void existingWonCustomerIgnoresOriginalOwnerAndMasksContact() {
        match(); var lead = new LeadDO(); lead.setOwnerUserId(77L);
        lenient().when(leadMapper.selectLatestByPersonId(10L)).thenReturn(lead);lenient().when(orderMapper.hasEffectiveOrder(10L)).thenReturn(true);
        var r=service.checkCustomer(20L,req());assertTrue(r.isCanRepurchase());assertEquals(10L,r.getPersonId());assertEquals("138****8000",r.getMaskedMobile());
    }
    @Test void existingLeadWithoutEffectiveOrderAllowsForm() {
        match();lenient().when(leadMapper.selectLatestByPersonId(10L)).thenReturn(new LeadDO());
        var r=service.checkCustomer(20L,req());
        assertTrue(r.isCanRepurchase());assertEquals("EXISTING_CUSTOMER",r.getMatchStatus());assertEquals(10L,r.getPersonId());
        verify(orderMapper, never()).hasEffectiveOrder(anyLong());
    }
    @Test void activeOrderStopsBeforeForm() {
        match();when(orderMapper.selectActiveRepurchaseByPersonId(eq(10L),any())).thenReturn(new SalesOrderDO());
        assertFalse(service.checkCustomer(20L,req()).isCanRepurchase());
    }
    @Test void multipleIdentityDoesNotExposeCandidates() {
        when(personMapper.selectDuplicateCandidates("13800138000",null)).thenReturn(List.of(person(),person().setId(11L)));
        var r=service.checkCustomer(20L,req());assertEquals("MULTIPLE_MATCH",r.getMatchStatus());assertNull(r.getCustomerName());
    }
    @Test void nameMismatchDoesNotExposeStoredIdentity() {
        match();var r=req();r.setCustomerName("其他姓名");var result=service.checkCustomer(20L,r);
        assertEquals("IDENTITY_CONFLICT",result.getMatchStatus());assertNull(result.getPersonId());
    }
    @Test void addedWrongWechatIsRejected() {
        when(personMapper.selectDuplicateCandidates("13800138000","wrong")).thenReturn(List.of(person()));
        var r=req();r.setCustomerWechatId("wrong");assertFalse(service.checkCustomer(20L,r).isCanRepurchase());
    }
    @Test void foreignTenantIsNeitherDisclosedNorAuthorized() {
        var p=person();p.setTenantId(2L);when(personMapper.selectDuplicateCandidates("13800138000",null)).thenReturn(List.of(p));
        when(personMapper.selectById(10L)).thenReturn(p);
        assertEquals("NO_MATCH",service.checkCustomer(20L,req()).getMatchStatus());assertFalse(service.hasPermission(10L,"create",20L));
    }
    @Test void missingFeaturePermissionRejectsBeforeLookup() {
        when(permissionApi.hasAnyPermissions(20L,"zsjos:sales-order:create")).thenReturn(false);
        assertThrows(RuntimeException.class,()->service.checkCustomer(20L,req()));verifyNoInteractions(personMapper);
    }
    @Test void objectPermissionNeverGrantsReadOrEdit() { assertFalse(service.hasPermission(10L,"read",20L));assertFalse(service.hasPermission(10L,"update",20L)); }
    @Test void lockedIdentityIsRevalidated() {
        when(personMapper.selectByIdForUpdate(10L,1L)).thenReturn(person().setName("已变更"));
        var r=new SalesOrderRepurchaseReqVO();r.setCustomerName("测试学员");r.setCustomerMobile("13800138000");
        assertThrows(RuntimeException.class,()->service.requireIdentity(10L,r));
    }
}
