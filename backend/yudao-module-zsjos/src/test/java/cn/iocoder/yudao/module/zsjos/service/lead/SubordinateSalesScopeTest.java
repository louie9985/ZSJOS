package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.dept.dto.PostRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentHistoryMapper;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SubordinateSalesScopeTest {
    SubordinateSalesScope scope = new SubordinateSalesScope();
    AdminUserApi users = mock(AdminUserApi.class);
    PostApi posts = mock(PostApi.class);
    PermissionApi permissions = mock(PermissionApi.class);
    LeadObjectPermissionService managers = mock(LeadObjectPermissionService.class);
    LeadAssignmentHistoryMapper history = mock(LeadAssignmentHistoryMapper.class);
    @BeforeEach void setup() {
        var ownership = new LeadTeamTransferPolicy();
        ReflectionTestUtils.setField(ownership,"historyMapper",history);
        ReflectionTestUtils.setField(scope,"users",users); ReflectionTestUtils.setField(scope,"posts",posts);
        ReflectionTestUtils.setField(scope,"permissions",permissions); ReflectionTestUtils.setField(scope,"managers",managers);
        ReflectionTestUtils.setField(scope,"ownership",ownership);
        var post = new PostRespDTO(); post.setId(5L); when(posts.getPostByCode("sales_specialist")).thenReturn(post);
    }
    AdminUserRespDTO user(long id,int status,long post) {
        var u = new AdminUserRespDTO(); u.setId(id); u.setStatus(status); u.setPostIds(Set.of(post)); return u;
    }
    @Test void managedReadKeepsDisabledSalesAndExcludesOtherPosts() {
        when(managers.getManagedUserIds(10L)).thenReturn(Set.of(20L,30L,40L));
        when(users.getUserList(Set.of(20L,30L,40L))).thenReturn(List.of(user(20,0,5),user(30,1,5),user(40,0,7)));
        assertEquals(List.of(20L,30L),scope.readable(10L).stream().map(AdminUserRespDTO::getId).toList());
    }
    @Test void tenantWideReadsStillExcludeNonSalesAndDoNotGrantManagement() {
        when(permissions.hasTenantReadAllAccess(10L)).thenReturn(true);
        when(users.getUserListByStatus(0)).thenReturn(List.of(user(20,0,5),user(40,0,7)));
        when(users.getUserListByStatus(1)).thenReturn(List.of(user(30,1,5)));
        assertEquals(List.of(20L,30L),scope.readable(10L).stream().map(AdminUserRespDTO::getId).toList());
        var lead = new LeadDO().setId(1L).setOwnerUserId(20L).setOwnerIdentity("sales").setAssignmentStatus("owned");
        assertThrows(ServiceException.class, () -> scope.requireManagedLead(lead,10L));
    }
    @Test void missingSalesPostFailsClosed() {
        when(posts.getPostByCode("sales_specialist")).thenReturn(null);
        when(managers.getManagedUserIds(10L)).thenReturn(Set.of(20L));
        when(users.getUserList(Set.of(20L))).thenReturn(List.of(user(20,0,5)));
        assertTrue(scope.readable(10L).isEmpty());
    }
    @Test void educationIdentityIsRejectedEvenForDualPostMember() {
        var lead = new LeadDO().setId(1L).setOwnerUserId(20L).setOwnerIdentity("education").setAssignmentStatus("owned");
        assertThrows(ServiceException.class, () -> scope.requireManagedLead(lead,10L));
        verifyNoInteractions(users,managers);
    }
    @Test void recycleRequiresLatestSameCycleSalesEvidence() {
        var lead = new LeadDO().setId(1L).setRecycleSourceOwnerUserId(20L).setAssignmentStatus("recycle_pending");
        var recycle = new LeadAssignmentHistoryDO(); recycle.setActionType("recycle"); recycle.setFromOwnerUserId(20L); recycle.setOwnerIdentitySnapshot("sales");
        when(history.selectByLeadId(1L)).thenReturn(List.of(recycle));
        assertTrue(scope.salesLead(lead));
        recycle.setOwnerIdentitySnapshot("education"); assertFalse(scope.salesLead(lead));
        recycle.setOwnerIdentitySnapshot(null); assertThrows(ServiceException.class, () -> scope.salesLead(lead));
        var accept = new LeadAssignmentHistoryDO(); accept.setActionType("accept"); accept.setToOwnerUserId(20L); accept.setOwnerIdentitySnapshot("sales");
        when(history.selectByLeadId(1L)).thenReturn(List.of(recycle,accept)); assertTrue(scope.salesLead(lead));
        recycle.setActionType("transfer"); assertThrows(ServiceException.class, () -> scope.salesLead(lead));
    }
}
