package cn.iocoder.yudao.module.zsjos.service.cashback;

import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.service.lead.*;
import cn.iocoder.yudao.module.zsjos.service.order.*;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerOwnershipService;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

class CashbackBatchPermissionTest {
    @BeforeEach void tenant() { TenantContextHolder.setTenantId(1L); }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }
    static void inject(Object target, String field, Object value) { ReflectionTestUtils.setField(target,field,value); }
    static AdminUserRespDTO user(long id, Long dept) { var u=new AdminUserRespDTO();u.setId(id);u.setDeptId(dept);u.setStatus(0);return u; }
    static DeptRespDTO dept(long id,long leader) { var d=new DeptRespDTO();d.setId(id);d.setLeaderUserId(leader);return d; }

    @Test void identityBatchEqualsBothSingleChecksAndResolvesDepartmentOnce() {
        var service=new LeadObjectPermissionService(); var permissions=mock(PermissionApi.class);
        var security=mock(SecurityFrameworkService.class);var users=mock(AdminUserApi.class);var depts=mock(DeptApi.class);
        inject(service,"permissionApi",permissions);inject(service,"securityFrameworkService",security);inject(service,"adminUserApi",users);inject(service,"deptApi",depts);
        when(depts.getDeptListByLeaderUserId(7L)).thenReturn(List.of(dept(10,7)));
        when(depts.getChildDeptList(10L)).thenReturn(List.of(dept(11,8)));
        when(users.getUser(20L)).thenReturn(user(20,11L)); when(users.getUser(21L)).thenReturn(user(21,99L));
        when(users.getUserListByDeptIds(Set.of(10L,11L))).thenReturn(List.of(user(20,11L)));
        var rows=java.util.stream.LongStream.rangeClosed(1,1024).mapToObj(i -> new LeadDO().setId(i).setOwnerUserId(i%3==0?null:i%3==1?20L:21L)).toList();
        // Unmasked access is the stricter requirement, so the single-object oracle short-circuits there.
        Set<Long> expected=rows.stream().filter(l -> service.canViewUnmaskedIdentity(7L,l) && service.canReadDetail(l,7L)).map(LeadDO::getId).collect(Collectors.toSet());
        clearInvocations(users,depts,permissions,security);
        assertEquals(expected,service.filterUnmaskedIdentity(rows,7L));
        verify(users,never()).getUser(any());verify(users,times(1)).getUserListByDeptIds(anyCollection());
        verify(depts,times(1)).getDeptListByLeaderUserId(7L);
        when(permissions.hasTenantReadAllAccess(7L)).thenReturn(true);
        assertEquals(1024,service.filterUnmaskedIdentity(rows,7L).size());
        when(permissions.hasTenantReadAllAccess(7L)).thenReturn(false);
        when(users.getUserListByDeptIds(anyCollection())).thenReturn(List.of());
        assertTrue(service.filterUnmaskedIdentity(rows,7L).isEmpty(),"permission context must not survive a request");
    }

    @Test void detailBatchMatchesEveryExistingRelationshipBranch() {
        var service=spy(new LeadObjectPermissionService());var permissions=mock(PermissionApi.class);var security=mock(SecurityFrameworkService.class);
        var users=mock(AdminUserApi.class);var depts=mock(DeptApi.class);var aging=mock(LeadAgingPoolService.class);
        var sea=mock(LeadPublicSeaRecordMapper.class);var history=mock(ServiceRelationMapper.class);var media=mock(MediaAccountMapper.class);
        var batch=mock(LeadReadBatchMapper.class);var partner=mock(PartnerOwnershipService.class);var orders=mock(SalesOrderMapper.class);var orderAccess=mock(SalesOrderObjectPermissionService.class);
        inject(service,"permissionApi",permissions);inject(service,"securityFrameworkService",security);inject(service,"adminUserApi",users);inject(service,"deptApi",depts);
        inject(service,"leadAgingPoolService",aging);inject(service,"publicSeaRecordMapper",sea);inject(service,"serviceRelationMapper",history);
        inject(service,"mediaAccountMapper",media);inject(service,"readBatchMapper",batch);inject(service,"partnerOwnershipService",partner);
        inject(service,"salesOrderMapper",orders);inject(service,"salesOrderObjectPermissionService",orderAccess);
        when(depts.getDeptListByLeaderUserId(7L)).thenReturn(List.of(dept(10,7)));when(users.getUser(20L)).thenReturn(user(20,10L));
        doReturn(Set.of(20L)).when(service).getManagedUserIds(7L);
        when(security.hasPermission(PERMISSION_QUERY_SUBMITTED)).thenReturn(true);
        when(security.hasPermission("zsjos:lead:qualification:manage")).thenReturn(true);
        var rows=new ArrayList<LeadDO>();for(long i=1;i<=13;i++)rows.add(new LeadDO().setId(i).setOwnerUserId(99L));
        rows.get(0).setOwnerUserId(7L);rows.get(1).setOwnerUserId(20L);
        rows.get(2).setProviderOwnerType(PROVIDER_OWNER_SYSTEM_USER).setProviderOwnerId(7L);
        rows.get(3).setProviderOwnerType(PROVIDER_OWNER_SYSTEM_USER).setProviderOwnerId(20L);
        rows.get(4).setAssignmentStatus(ASSIGNMENT_RECYCLE_PENDING).setRecycleSourceOwnerUserId(7L);
        rows.get(5).setAssignmentStatus(ASSIGNMENT_RECYCLE_PENDING).setRecycleSourceOwnerUserId(20L);
        when(aging.canRead(7L,7L)).thenReturn(true);when(aging.filterReadableLeadIds(anyCollection(),eq(7L))).thenReturn(Set.of(7L));
        var record=new LeadPublicSeaRecordDO().setLeadId(8L).setCollaboratorUserId(7L);
        when(sea.selectByLeadId(8L)).thenReturn(record);when(sea.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(record));
        when(history.countActiveByParticipantAndLead(7L,9L,1L)).thenReturn(1L);when(media.countParticipantByLead(7L,10L,1L)).thenReturn(1L);
        when(batch.selectStudentReadableLeadIds(anyCollection(),eq(7L),eq(1L))).thenReturn(Set.of(9L,10L));
        rows.get(10).setPartnerId(30L);when(partner.canRead(7L,30L)).thenReturn(true);when(partner.filterReadablePartnerIds(eq(7L),anyCollection())).thenReturn(Set.of(30L));
        var order=new SalesOrderDO().setId(40L).setLeadId(12L);when(orders.selectByLeadId(12L)).thenReturn(List.of(order));
        when(orderAccess.canRead(order,7L)).thenReturn(true);when(orders.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(order));
        when(orderAccess.filterReadable(anyCollection(),eq(7L))).thenReturn(Set.of(40L));
        var expected=rows.stream().filter(l -> service.canReadDetail(l,7L)).map(LeadDO::getId).collect(Collectors.toSet());
        assertEquals(12,expected.size());clearInvocations(users,orders,sea,aging);
        assertEquals(expected,service.filterReadableDetails(rows,7L));
        verify(orders,never()).selectByLeadId(anyLong());verify(sea,never()).selectByLeadId(anyLong());verify(aging,never()).canRead(anyLong(),anyLong());
    }

    @Test void orderBatchMatchesOwnOwnerAgingSupervisorTeamAndApprovalBranches() {
        var service=spy(new SalesOrderObjectPermissionService());var leads=mock(LeadMapper.class);var aging=mock(LeadAgingPoolService.class);
        var supervisors=mock(SalesOrderSupervisorConfirmationMapper.class);inject(service,"leadMapper",leads);inject(service,"agingPoolService",aging);inject(service,"supervisorConfirmationMapper",supervisors);
        doReturn(false).when(service).isApprovalPoolMember(7L);doReturn(Set.of(20L)).when(service).teamUserIds(7L);
        var rows=new ArrayList<SalesOrderDO>();for(long i=1;i<=7;i++)rows.add(new SalesOrderDO().setId(i).setLeadId(i).setSubmitterUserId(99L));
        rows.get(0).setSubmitterUserId(7L);rows.get(1).setFormalSalesUserId(7L);rows.get(4).setCurrentApprovalRoundId(50L);rows.get(5).setSubmitterUserId(20L);
        var lead=new LeadDO().setId(3L).setOwnerUserId(7L);when(leads.selectById(3L)).thenReturn(lead);when(leads.selectBatchIds(anyCollection())).thenReturn(List.of(lead));
        when(aging.canRead(4L,7L)).thenReturn(true);when(aging.filterReadableLeadIds(anyCollection(),eq(7L))).thenReturn(Set.of(4L));
        var confirmation=new SalesOrderSupervisorConfirmationDO().setApprovalRoundId(50L).setSupervisorUserId(7L);
        when(supervisors.selectByRoundId(50L)).thenReturn(List.of(confirmation));when(supervisors.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(confirmation));
        var expected=rows.stream().filter(o -> service.canRead(o,7L)).map(SalesOrderDO::getId).collect(Collectors.toSet());assertEquals(6,expected.size());
        clearInvocations(leads,supervisors,aging);assertEquals(expected,service.filterReadable(rows,7L));
        verify(leads,never()).selectById(anyLong());verify(supervisors,never()).selectByRoundId(anyLong());
        doReturn(true).when(service).isApprovalPoolMember(7L);assertEquals(7,service.filterReadable(rows,7L).size());
    }

    @Test void financeOrderBatchKeepsPagePermissionsAndManagementScopeDistinct() {
        var service=spy(new SalesOrderObjectPermissionService());var permissions=mock(PermissionApi.class);inject(service,"permissionApi",permissions);
        var rows=List.of(new SalesOrderDO().setId(1L).setSubmitterUserId(7L),new SalesOrderDO().setId(2L).setSubmitterUserId(20L),new SalesOrderDO().setId(3L).setSubmitterUserId(99L));
        when(permissions.hasTenantReadAllAccess(7L)).thenReturn(true);
        assertTrue(service.filterFinanceReadable(rows,7L).isEmpty(),"global data access alone is not page permission");
        when(permissions.hasAnyPermissions(7L,"zsjos:sales-order:query-own")).thenReturn(true);
        assertEquals(Set.of(1L),service.filterFinanceReadable(rows,7L));
        when(permissions.hasAnyPermissions(7L,"zsjos:sales-order:query-management")).thenReturn(true);
        doReturn(new SalesOrderManagementScope(false,true,Set.of(),Set.of(20L))).when(service).resolveManagementScope(7L);
        assertEquals(Set.of(1L,2L),service.filterFinanceReadable(rows,7L));
        doReturn(new SalesOrderManagementScope(true,true,Set.of(),Set.of())).when(service).resolveManagementScope(7L);
        assertEquals(Set.of(1L,2L,3L),service.filterFinanceReadable(rows,7L));
    }

    @Test void agingBatchMatchesSingleReadsIncludingNewestCycleAndMissingOwner() {
        var service=new LeadAgingPoolServiceImpl();var mapper=mock(LeadAgingPoolCycleMapper.class);
        var users=mock(AdminUserApi.class);var depts=mock(DeptApi.class);var security=mock(SecurityFrameworkService.class);
        var assignments=mock(LeadAssignmentService.class);
        inject(service,"cycleMapper",mapper);inject(service,"adminUserApi",users);inject(service,"deptApi",depts);
        inject(service,"securityFrameworkService",security);inject(service,"assignmentService",assignments);
        var current=user(7,10L);var owner=user(20,10L);var managed=user(21,11L);var unrelated=user(22,99L);
        when(users.getUser(7L)).thenReturn(current);when(users.getUser(20L)).thenReturn(owner);
        when(users.getUser(21L)).thenReturn(managed);when(users.getUser(22L)).thenReturn(unrelated);
        when(users.getUserMap(anyCollection())).thenReturn(Map.of(7L,current,20L,owner,21L,managed,22L,unrelated));
        var eligible=new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO();eligible.setId(7L);
        when(assignments.getEligibleSalesUsers()).thenReturn(List.of(eligible));
        when(security.hasPermission(PERMISSION_AGING_POOL_MANAGE)).thenReturn(true);
        when(depts.getDept(11L)).thenReturn(dept(11,7));when(depts.getDeptListByLeaderUserId(7L)).thenReturn(List.of(dept(11,7)));
        var cycles=List.of(
                new LeadAgingPoolCycleDO().setId(8L).setLeadId(1L).setOriginalOwnerUserId(20L),
                new LeadAgingPoolCycleDO().setId(7L).setLeadId(2L).setOriginalOwnerUserId(21L),
                new LeadAgingPoolCycleDO().setId(6L).setLeadId(3L).setOriginalOwnerUserId(7L),
                new LeadAgingPoolCycleDO().setId(5L).setLeadId(4L).setOriginalOwnerUserId(22L).setCollaboratorUserId(7L),
                new LeadAgingPoolCycleDO().setId(4L).setLeadId(5L).setOriginalOwnerUserId(22L),
                new LeadAgingPoolCycleDO().setId(3L).setLeadId(6L).setOriginalOwnerUserId(999L),
                new LeadAgingPoolCycleDO().setId(2L).setLeadId(7L),
                new LeadAgingPoolCycleDO().setId(1L).setLeadId(5L).setOriginalOwnerUserId(7L));
        when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(cycles);
        Set<Long> seen=new HashSet<>();var newest=cycles.stream().filter(c -> seen.add(c.getLeadId())).toList();
        var expected=newest.stream().filter(c -> service.canRead(c,7L)).map(LeadAgingPoolCycleDO::getLeadId).collect(Collectors.toSet());
        assertEquals(Set.of(1L,2L,3L,4L),expected);
        clearInvocations(users,depts,assignments);assertEquals(expected,service.filterReadableLeadIds(seen,7L));
        verify(users,times(1)).getUser(7L);verify(users,never()).getUser(20L);
        verify(assignments,times(1)).getEligibleSalesUsers();verify(depts,never()).getDept(anyLong());
        when(security.hasPermission(PERMISSION_AGING_POOL_MANAGE_ALL)).thenReturn(true);
        assertEquals(seen,service.filterReadableLeadIds(seen,7L));
    }
}
