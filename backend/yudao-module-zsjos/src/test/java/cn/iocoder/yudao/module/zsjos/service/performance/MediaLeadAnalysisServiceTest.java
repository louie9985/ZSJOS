package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.MediaLeadVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.performance.MediaLeadOrgDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.performance.MediaLeadTargetDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaLeadAnalysisServiceTest {
    @InjectMocks private MediaLeadAnalysisService service;
    @Mock private MediaLeadAccess access;
    @Mock private MediaLeadTargetMapper targets;
    @Mock private MediaLeadOrgMapper orgs;
    @Mock private MediaLeadRevisionMapper revisions;
    @Mock private MediaLeadFactMapper facts;
    @Mock private MediaLeadQueryMapper queries;
    private static final LocalDate MONTH = LocalDate.of(2026, 9, 1);

    private AdminUserRespDTO user(long id) {
        var user = new AdminUserRespDTO(); user.setId(id); user.setDeptId(10L); user.setNickname("成员" + id); return user;
    }
    private MediaLeadOrgDO org(long id, long center, String kind) {
        var org = new MediaLeadOrgDO(); org.setDeptId(id); org.setCenterId(center); org.setKind(kind); return org;
    }
    private MediaLeadTargetDO target(String type, long id, int count) {
        var row = new MediaLeadTargetDO(); row.setScopeType(type); row.setScopeId(id);
        row.setTargetCount(count); row.setManual(true); row.setVersion(0); return row;
    }
    private void organizationFixture(List<MediaLeadTargetDO> saved) {
        when(access.has(MediaLeadAccess.TARGET_QUERY)).thenReturn(true);
        when(access.mediaUsers()).thenReturn(List.of(user(1), user(2)));
        when(access.organizations()).thenReturn(List.of(org(20, 20, "CENTER"), org(10, 20, "DEPT")));
        when(access.deptAllowed(anyLong())).thenReturn(true);
        when(access.dept(anyLong())).thenAnswer(call -> {
            var dept = new DeptRespDTO(); dept.setName("组织" + call.getArgument(0)); return dept;
        });
        when(targets.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(saved);
    }

    @Test void cancelMistakenCenterKeepsConfigurationRowAndAllowsReselect() {
        var row = org(10, 10, "CENTER"); row.setId(21L); row.setVersion(2);
        when(access.has(MediaLeadAccess.TARGET_CONFIGURE)).thenReturn(true);
        when(access.writeDeptAllowed(10L)).thenReturn(true);
        when(orgs.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(row);
        service.unsetOrganization(new MediaLeadVO.OrgUnset(10L, 2));
        assertEquals("DEPT", row.getKind());
        assertEquals(3, row.getVersion());
        verify(orgs).updateById(row);

        var dept = new DeptRespDTO(); dept.setStatus(0);
        when(access.dept(10L)).thenReturn(dept);
        service.saveOrganization(new MediaLeadVO.OrgEdit(10L, 10L, "CENTER", null));
        assertEquals("CENTER", row.getKind());
        assertEquals(4, row.getVersion());
        verify(orgs, times(2)).updateById(row);
    }

    @Test void cancelCenterRequiresWritableDepartmentAndCurrentVersion() {
        var row = org(10, 10, "CENTER"); row.setId(21L); row.setVersion(2);
        when(access.has(MediaLeadAccess.TARGET_CONFIGURE)).thenReturn(true);
        assertThrows(RuntimeException.class, () -> service.unsetOrganization(new MediaLeadVO.OrgUnset(10L, 2)));
        verifyNoInteractions(orgs);

        when(access.writeDeptAllowed(10L)).thenReturn(true);
        when(orgs.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(row);
        assertThrows(RuntimeException.class, () -> service.unsetOrganization(new MediaLeadVO.OrgUnset(10L, 1)));
        verify(orgs, never()).updateById(any(MediaLeadOrgDO.class));
    }

    @Test void departmentAndCenterSumConfiguredUsersEvenWithoutLeads() {
        organizationFixture(List.of(target("USER", 1, 5), target("USER", 2, 7)));
        var rows = service.listTargets(MONTH);
        assertEquals(4, rows.size());
        assertEquals(12, rows.stream().filter(x -> "DEPT".equals(x.scopeType())).findFirst().orElseThrow().targetCount());
        assertEquals(12, rows.stream().filter(x -> "CENTER".equals(x.scopeType())).findFirst().orElseThrow().targetCount());
        assertTrue(rows.stream().filter(x -> "DEPT".equals(x.scopeType())).noneMatch(MediaLeadVO.Target::manual));
    }

    @Test void manualDepartmentOverridesAutomaticCenterSum() {
        organizationFixture(List.of(target("USER", 1, 5), target("USER", 2, 7), target("DEPT", 10, 20)));
        var rows = service.listTargets(MONTH);
        assertEquals(20, rows.stream().filter(x -> "CENTER".equals(x.scopeType())).findFirst().orElseThrow().targetCount());
    }

    @Test void partialIndividualTargetsDoNotPresentIncompleteDepartmentOrCenterGoal() {
        organizationFixture(List.of(target("USER", 1, 5)));
        var rows = service.listTargets(MONTH);
        assertNull(rows.stream().filter(x -> "DEPT".equals(x.scopeType())).findFirst().orElseThrow().targetCount());
        assertNull(rows.stream().filter(x -> "CENTER".equals(x.scopeType())).findFirst().orElseThrow().targetCount());
    }

    @Test void centerIncludesDirectEmployeesAndSkipsEmptyDepartments() {
        when(access.has(MediaLeadAccess.TARGET_QUERY)).thenReturn(true);
        var direct = user(1); direct.setDeptId(20L);
        when(access.mediaUsers()).thenReturn(List.of(direct, user(2)));
        when(access.organizations()).thenReturn(List.of(org(20, 20, "CENTER"), org(10, 20, "DEPT"), org(11, 20, "DEPT")));
        when(access.deptAllowed(anyLong())).thenReturn(true);
        when(targets.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(target("USER", 1, 5), target("USER", 2, 7)));
        var rows = service.listTargets(MONTH);
        assertEquals(12, rows.stream().filter(x -> "CENTER".equals(x.scopeType())).findFirst().orElseThrow().targetCount());
        assertNull(rows.stream().filter(x -> "DEPT".equals(x.scopeType()) && x.scopeId() == 11L)
                .findFirst().orElseThrow().targetCount());
    }

    @Test void centerQueryIncludesSystemDescendantsAndDirectCenterMembers() {
        var today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        var direct = user(1); direct.setDeptId(20L);
        when(access.mediaUsers()).thenReturn(List.of(direct));
        when(access.organizations()).thenReturn(List.of(org(20, 20, "CENTER"), org(10, 20, "DEPT"), org(11, 20, "DEPT")));
        when(facts.leads(1L, "CENTER", 20L, List.of(20L, 10L, 11L))).thenReturn(List.of());
        when(facts.firstOrders(1L, "CENTER", 20L, List.of(20L, 10L, 11L))).thenReturn(List.of());
        when(targets.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(target("USER", 1, 5)));
        TenantContextHolder.setTenantId(1L);
        try {
            var result = service.overview(new MediaLeadVO.Query("CENTER", 20L, today, today));
            assertEquals(5, result.target().targetCount());
            assertEquals(1L, result.members().getFirst().userId());
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test void membersExcludeDisabledHistoricalContributors() {
        var today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        var active = new MediaLeadFact();
        active.setId(101L); active.setUserId(1L); active.setUserName("启用成员");
        active.setSubmittedAt(today.minusDays(1).atTime(10, 0)); active.setStatus("valid");
        var disabled = new MediaLeadFact();
        disabled.setId(102L); disabled.setUserId(2L); disabled.setUserName("停用成员");
        disabled.setSubmittedAt(today.minusDays(1).atTime(11, 0)); disabled.setStatus("valid");
        when(facts.leads(1L, "DEPT", 10L, List.of())).thenReturn(List.of(active, disabled));
        when(facts.firstOrders(1L, "DEPT", 10L, List.of())).thenReturn(List.of());
        when(access.mediaUsers()).thenReturn(List.of(user(1)));
        when(access.organizations()).thenReturn(List.of(org(10, 20, "DEPT")));
        when(targets.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of());
        TenantContextHolder.setTenantId(1L);
        try {
            var result = service.overview(new MediaLeadVO.Query("DEPT", 10L, today, today));
            assertEquals(List.of(1L), result.members().stream().map(MediaLeadVO.Member::userId).toList());
            assertEquals(2, result.periods().stream().filter(x -> "yesterday".equals(x.key())).findFirst().orElseThrow().total());
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test void teamUsesSubmissionBatchWhileMemberConversionUsesEffectiveDate() {
        var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        var today = now.toLocalDate();
        var previous = new MediaLeadFact();
        previous.setId(101L); previous.setUserId(1L); previous.setUserName("成员1");
        previous.setDeptId(10L); previous.setSubmittedAt(today.minusDays(1).atTime(12, 0));
        previous.setStatus("valid");
        var current = new MediaLeadFact();
        current.setId(102L); current.setUserId(1L); current.setUserName("成员1");
        current.setDeptId(10L); current.setSubmittedAt(now.minusSeconds(30));
        current.setStatus("invalid");
        var order = new MediaLeadOrderFact();
        order.setLeadId(101L); order.setEffectiveAt(now.minusSeconds(10));
        when(facts.leads(1L, "DEPT", 10L, List.of())).thenReturn(List.of(previous, current));
        when(facts.firstOrders(1L, "DEPT", 10L, List.of())).thenReturn(List.of(order));
        when(access.mediaUsers()).thenReturn(List.of(user(1)));
        when(access.organizations()).thenReturn(List.of(org(10, 20, "DEPT")));
        when(targets.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(target("USER", 1, 2)));
        TenantContextHolder.setTenantId(1L);
        try {
            var result = service.overview(new MediaLeadVO.Query("DEPT", 10L, today.minusDays(1), today));
            var yesterday = result.periods().stream().filter(x -> "yesterday".equals(x.key())).findFirst().orElseThrow();
            var todayStats = result.periods().stream().filter(x -> "today".equals(x.key())).findFirst().orElseThrow();
            assertEquals(1, yesterday.total());
            assertEquals(1, yesterday.converted());
            assertEquals(1, todayStats.total());
            assertEquals(0, todayStats.converted());
            assertEquals(2, result.funnel().submitted());
            assertEquals(1, result.funnel().valid());
            assertEquals(1, result.funnel().converted());
            var priorDay = service.overview(new MediaLeadVO.Query("DEPT", 10L, today.minusDays(1), today.minusDays(1)));
            assertEquals(1, priorDay.funnel().submitted());
            assertEquals(1, priorDay.funnel().valid());
            assertEquals(1, priorDay.funnel().converted());
            assertEquals(1, priorDay.calendar().size());
            var member = result.members().getFirst();
            assertEquals(1, member.yesterday());
            assertEquals(0, member.yesterdayConverted());
            assertEquals(1, member.today());
            assertEquals(1, member.todayConverted());
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test void detailDoesNotShowFutureFirstOrderAsConverted() {
        var now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        var lead = new MediaLeadFact();
        lead.setId(101L); lead.setLeadNo("KZ202609280001"); lead.setStatus("valid");
        lead.setSubmittedAt(now.minusDays(1));
        var futureOrder = new MediaLeadOrderFact();
        futureOrder.setLeadId(101L); futureOrder.setEffectiveAt(now.plusDays(1));
        when(facts.leads(1L, "DEPT", 10L, List.of())).thenReturn(List.of(lead));
        when(facts.firstOrders(1L, "DEPT", 10L, List.of())).thenReturn(List.of(futureOrder));
        TenantContextHolder.setTenantId(1L);
        try {
            var result = service.details(new MediaLeadVO.Query("DEPT", 10L, now.minusDays(1).toLocalDate(), now.toLocalDate()));
            assertEquals(1, result.size());
            assertFalse(result.getFirst().converted());
            assertNull(result.getFirst().orderEffectiveAt());
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test void mixedUnauthorizedBatchNeverBeginsPersistence() {
        when(access.has(MediaLeadAccess.TARGET_UPDATE)).thenReturn(true);
        doThrow(MediaLeadAccess.denied()).when(access).authorizeTargetWrite("USER", 2L);
        var first = new MediaLeadVO.TargetEdit("USER", 1L, MONTH, 5, null, "月度设置", false);
        var second = new MediaLeadVO.TargetEdit("USER", 2L, MONTH, 7, null, "月度设置", false);
        assertThrows(RuntimeException.class, () -> service.saveTargets(List.of(first, second)));
        verifyNoInteractions(targets, revisions);
    }

    @Test void pagePermissionRequiredBeforeReadingTargetRows() {
        assertThrows(RuntimeException.class, () -> service.listTargets(MONTH));
        verifyNoInteractions(targets);
    }

    @Test void detailPageBoundsAndEmptyPagesAvoidHistoryMaterialization() {
        TenantContextHolder.setTenantId(991L);
        try {
            var request=new MediaLeadVO.DetailPageQuery();request.setScopeType("USER");request.setScopeId(1L);
            request.setStart(LocalDate.now());request.setEnd(LocalDate.now());request.setPageNo(3);request.setPageSize(20);
            when(access.has(MediaLeadAccess.DETAIL)).thenReturn(true);
            when(queries.countDetails(eq(991L),eq("USER"),eq(1L),anyList(),any(),any(),any())).thenReturn(25L);
            var result=service.detailPage(request);assertEquals(25L,result.getTotal());assertTrue(result.getList().isEmpty());
            verify(queries,never()).pageDetails(any(),any(),any(),anyList(),any(),any(),any(),anyLong(),anyInt());
            verifyNoInteractions(facts);
            request.setPageSize(101);assertThrows(RuntimeException.class,()->service.detailPage(request));
            request.setPageSize(-1);assertThrows(RuntimeException.class,()->service.detailPage(request));
            request.setPageSize(20);request.setEnd(request.getStart().plusDays(367));assertThrows(RuntimeException.class,()->service.detailPage(request));
        } finally {TenantContextHolder.clear();}
    }
    @Test void detailPageDeniedBeforeQuery() {
        var request=new MediaLeadVO.DetailPageQuery();request.setScopeType("USER");request.setScopeId(1L);
        assertThrows(RuntimeException.class,()->service.detailPage(request));verifyNoInteractions(queries,facts);
    }

    @Test void indexedCalendarMatchesPreviousDailyScanIncludingEmptyAndHistoricalDays() {
        var start=LocalDate.of(2026,1,1);var end=start.plusDays(90);
        var rows=new java.util.ArrayList<MediaLeadFact>();
        for(int i=0;i<20000;i++) {
            var row=new MediaLeadFact();row.setSubmittedAt(start.plusDays(i%120-10).atTime(i%24,0));
            row.setStatus(List.of("valid","converted","won","invalid","pending").get(i%5));rows.add(row);
        }
        var expected=start.datesUntil(end.plusDays(1)).map(day->{
            var own=rows.stream().filter(x->x.getSubmittedAt().toLocalDate().equals(day)).toList();
            long valid=own.stream().filter(x->List.of("valid","converted","won").contains(x.getStatus())).count();
            long invalid=own.stream().filter(x->"invalid".equals(x.getStatus())).count();
            return new MediaLeadVO.CalendarDay(day,own.size(),valid,invalid,own.size()-valid-invalid);
        }).toList();
        List<MediaLeadVO.CalendarDay> actual=org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"calendar",rows,start,end);
        assertEquals(expected,actual);
        List<MediaLeadVO.CalendarDay> empty=org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"calendar",rows,end,start);
        assertTrue(empty.isEmpty());
    }
}
