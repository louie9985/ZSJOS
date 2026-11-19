package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadFollowUpRecordDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentContactRecordDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.SubordinateSalesDailyMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class SubordinateSalesDailyStatisticsTest {
    SubordinateSalesServiceImpl service = new SubordinateSalesServiceImpl();
    Map<Class<?>,Object> dependencies = new HashMap<>();
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        for (var field : SubordinateSalesServiceImpl.class.getDeclaredFields()) if (field.isAnnotationPresent(Resource.class))
            ReflectionTestUtils.setField(service,field.getName(),dependencies.computeIfAbsent(field.getType(),type -> mock(type)));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    @Test void listAndOverviewUseSameNewMetricsAndPreserveOldFields() {
        var user = new AdminUserRespDTO(); user.setId(20L); user.setStatus(0); user.setNickname("销售");
        when(((SubordinateSalesScope)dependencies.get(SubordinateSalesScope.class)).readable(10L)).thenReturn(List.of(user));
        var receiver = new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO(); receiver.setId(20L);
        when(((LeadAssignmentService)dependencies.get(LeadAssignmentService.class)).getEligibleReceivers()).thenReturn(List.of(receiver));
        when(((SalesDispatchStatusService)dependencies.get(SalesDispatchStatusService.class)).getStatus(20L))
                .thenReturn(new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.dispatch.SalesDispatchStatusRespVO()
                        .setEligible(true).setPresence("online").setMode("accepting"));
        var mapper = (SubordinateSalesDailyMapper)dependencies.get(SubordinateSalesDailyMapper.class);
        var created = new BusinessEventDO(); created.setAggregateId(5L); created.setOperatorUserId(20L);
        var follow = new LeadFollowUpRecordDO(); follow.setOperatorUserId(20L);
        var contact = new StudentContactRecordDO(); contact.setOperatorUserId(20L);
        when(mapper.selectNewIntentions(eq(1L),eq(List.of(20L)),any(),any())).thenReturn(List.of(created,created));
        when(mapper.selectOldIntentionFollowUps(eq(1L),eq(List.of(20L)),any(),any())).thenReturn(List.of(follow,follow));
        when(mapper.selectContactedStudents(eq(1L),eq(List.of(20L)),any(),any())).thenReturn(List.of(contact));
        var overview = service.getOverview(20L,10L);
        assertEquals(1L,overview.getTodayNewIntentionCount()); assertEquals(2L,overview.getTodayOldIntentionFollowUpRecordCount());
        assertEquals(1L,overview.getTodayStudentContactCount()); assertEquals(0L,overview.getTodayFollowUpRecordCount());
        assertEquals(0L,overview.getTodayFollowUpRemainingCount());
        assertTrue(overview.getEligible()); assertTrue(overview.getCanReceiveNewLeads());
        when(((cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService)dependencies.get(cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService.class)).matches(eq("subordinate_sales"),any(),any())).thenReturn(true);
        var page = service.getPage(new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate.SubordinateSalesPageReqVO(),10L);
        assertEquals(overview,page.getList().getFirst());
    }
    @Test void emptyScopeNeverIssuesUnboundedDailyQueries() {
        var page = service.getPage(new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate.SubordinateSalesPageReqVO(),10L);
        assertEquals(0L,page.getTotal()); verifyNoInteractions(dependencies.get(SubordinateSalesDailyMapper.class));
    }
}
