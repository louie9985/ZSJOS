package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.LeadCalendarController;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.calendar.LeadCalendarQueryReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup.LeadFollowUpRespVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadCalendarMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.PERMISSION_DETAIL_FOLLOW_UP_READ;

@ExtendWith(MockitoExtension.class)
class LeadCalendarServiceTest {
    @InjectMocks LeadCalendarService service;
    @Mock LeadCalendarMapper mapper;
    @Mock LeadManagementService managementService;
    @Mock LeadFollowUpService followUpService;
    @Mock LeadObjectPermissionService objectPermissionService;
    @Mock SecurityFrameworkService security;
    @Mock DictDataApi dictDataApi;
    final LocalDate start = LocalDate.of(2026, 9, 22);
    @BeforeEach void before() { TenantContextHolder.setTenantId(91L); }
    @AfterEach void after() { TenantContextHolder.clear(); }
    LeadCalendarQueryReqVO query() { var q = new LeadCalendarQueryReqVO(); q.setStart(start); q.setEnd(start.plusDays(1)); return q; }
    LeadCalendarMapper.DueLead row(long id, int hour, String category) {
        var row = new LeadCalendarMapper.DueLead(); row.setId(id); row.setCategory(category); row.setDeadline(start.atTime(hour, 0)); return row;
    }
    void rows(LeadCalendarMapper.DueLead... rows) { when(mapper.selectDueLeads(91L, 7L, start.atStartOfDay(), start.plusDays(1).atStartOfDay())).thenReturn(List.of(rows)); }
    @Test void usesAuthenticatedUserAndTenantForCounts() { rows(row(1, 9, "A"), row(2, 10, "S")); var days = service.days(query(), 7L); assertEquals(2, days.getFirst().count()); assertEquals(start, days.getFirst().date()); verifyNoInteractions(managementService, followUpService); }
    @Test void emptyMonthHasNoInventedCounts() { rows(); assertTrue(service.days(query(), 7L).isEmpty()); }
    @Test void deadlineSortHasStableTieBreakerAndReverse() { var rows = new ArrayList<>(List.of(row(3, 9, "S"), row(1, 9, "A"), row(2, 10, "B"))); rows.sort(LeadCalendarService.comparator("deadline", "asc", Map.of())); assertEquals(List.of(1L, 3L, 2L), rows.stream().map(LeadCalendarMapper.DueLead::getId).toList()); rows.sort(LeadCalendarService.comparator("deadline", "desc", Map.of())); assertEquals(2L, rows.getFirst().getId()); }
    @Test void categoriesUseConfiguredPriorityAndDeadlineWithinCategory() { var rows = new ArrayList<>(List.of(row(1, 8, null), row(2, 7, "A"), row(3, 10, "S"), row(4, 9, "S"))); rows.sort(LeadCalendarService.comparator("category", "asc", Map.of("S", -1, "A", 1))); assertEquals(List.of(4L, 3L, 2L, 1L), rows.stream().map(LeadCalendarMapper.DueLead::getId).toList()); }
    @Test void existingVerboseSCategoryIsPrioritizedWithoutMatchingOtherWords() { assertTrue(LeadCalendarService.isPriorityCategory("S类【重点客户-待成交】", null)); assertTrue(LeadCalendarService.isPriorityCategory("custom", "S级")); assertFalse(LeadCalendarService.isPriorityCategory("stage", "销售类")); }
    @Test void pageOnlyHydratesSelectedRowsAndHidesHistoryWithoutPermission() { rows(row(1, 9, "S"), row(2, 8, "A")); var q=query(); q.setPageSize(1); q.setPageNo(2); var lead=new LeadManagementRespVO(); lead.setId(1L); when(managementService.getLead(1L,7L)).thenReturn(lead); var page=service.cards(q,7L); assertEquals(2,page.getTotal()); assertEquals(1L,page.getList().getFirst().lead().getId()); assertFalse(page.getList().getFirst().canReadFollowUp()); verifyNoInteractions(followUpService,objectPermissionService); verify(managementService,never()).getLead(2L,7L); }
    @Test void historyRequiresFeatureAndObjectChecks() { rows(row(1,9,"A")); when(security.hasPermission(PERMISSION_DETAIL_FOLLOW_UP_READ)).thenReturn(true); when(managementService.getLead(1L,7L)).thenReturn(new LeadManagementRespVO()); var record=new LeadFollowUpRespVO(); record.setRemark("snapshot"); when(followUpService.getPage(1L,1,1,7L)).thenReturn(new PageResult<>(List.of(record),1L)); assertEquals("snapshot",service.cards(query(),7L).getList().getFirst().lastFollowUp().getRemark()); var order=inOrder(objectPermissionService,followUpService); order.verify(objectPermissionService).check(1L,"follow-up-read"); order.verify(followUpService).getPage(1L,1,1,7L); }
    @Test void lostObjectAccessDoesNotReturnCard() { rows(row(1,9,"A")); when(managementService.getLead(1L,7L)).thenThrow(new IllegalStateException("denied")); assertThrows(IllegalStateException.class,()->service.cards(query(),7L)); verifyNoInteractions(followUpService); }
    @Test void boundsRejectEmptyReversedAndOversizedRanges() { var q=query(); assertTrue(q.isValidRange()); q.setEnd(start); assertFalse(q.isValidRange()); q.setEnd(start.minusDays(1)); assertFalse(q.isValidRange()); q.setEnd(start.plusDays(43)); assertFalse(q.isValidRange()); q.setEnd(start.plusDays(42)); assertTrue(q.isValidRange()); }
    @Test void bothEndpointsRequireCalendarAndLeadFeaturePermissions() throws Exception {
        var context=new StandardEvaluationContext(); context.setBeanResolver((c,name)->security);
        for(String method:List.of("days","cards")) {
            var expression=new SpelExpressionParser().parseExpression(LeadCalendarController.class.getMethod(method,LeadCalendarQueryReqVO.class).getAnnotation(PreAuthorize.class).value());
            for(boolean calendar:List.of(false,true)) for(boolean lead:List.of(false,true)) {
                lenient().when(security.hasPermission("zsjos:lead-follow-up-calendar:query")).thenReturn(calendar);
                lenient().when(security.hasPermission("zsjos:lead:query")).thenReturn(lead);
                assertEquals(calendar && lead,expression.getValue(context,Boolean.class));
            }
        }
    }
}
