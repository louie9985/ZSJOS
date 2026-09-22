package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.dept.dto.PostRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate.SubordinateBatchResultVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate.SubordinateBatchLeadActionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate.SubordinateSalesRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate.SubordinateTaskPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;
import java.lang.reflect.Method;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.SUBORDINATE_LEAD_OWNER_CHANGED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubordinateSalesServiceImplTest {
    @InjectMocks private SubordinateSalesServiceImpl service;
    @Mock private LeadObjectPermissionService permissionService;
    @Mock private LeadAssignmentService assignmentService;
    @Mock private SubordinateSalesCommandService commandService;
    @Mock private SalesDispatchStatusService dispatchStatusService;
    @Mock private LeadMapper leadMapper;
    @Mock private BusinessTaskMapper taskMapper;
    @Mock private AdminUserApi adminUserApi;
    @Mock private PostApi postApi;
    @Mock private cn.iocoder.yudao.module.system.api.permission.PermissionApi permissionApi;

    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper orderMapper;
    @Mock private cn.iocoder.yudao.module.system.api.dict.DictDataApi dictDataApi;
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentHistoryMapper assignmentHistoryMapper;
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadFollowUpRecordMapper followUpRecordMapper;
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityFollowUpRecordMapper opportunityFollowUpRecordMapper;
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper eventMapper;

    @Test
    void dailyMetricsDeduplicateLeadsAndKeepCurrentUnqualifiedWithoutAgeLimit() {
        var user = subordinate(20L, 0, 5L);
        var post = new PostRespDTO(); post.setId(5L);
        when(permissionService.getManagedUserIds(10L)).thenReturn(Set.of(20L));
        when(postApi.getPostByCode("sales_specialist")).thenReturn(post);
        when(adminUserApi.getUserList(Set.of(20L))).thenReturn(List.of(user));
        var dispatch = new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.dispatch.SalesDispatchStatusRespVO();
        dispatch.setPresence("online"); dispatch.setMode("accepting");
        when(dispatchStatusService.getStatus(20L)).thenReturn(dispatch);
        var start = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).atStartOfDay();
        var end = start.plusDays(1);
        var pending = task(1L, "pending", start);
        var duplicate = task(1L, "pending", start.plusHours(1));
        var completed = task(2L, "completed", start.plusHours(2));
        var cancelled = task(3L, "cancelled", start.plusHours(3));
        when(taskMapper.selectByAssigneeIds(List.of(20L))).thenReturn(List.of(pending, duplicate, completed,
                cancelled, task(4L, "pending", end), task(5L, "pending", start.minusSeconds(1))));
        var fresh = new LeadDO(); fresh.setId(1L); fresh.setOwnerUserId(20L); fresh.setStatus("submitted");
        fresh.setQualificationDeadlineAt(end.plusDays(3));
        var old = new LeadDO(); old.setId(2L); old.setOwnerUserId(20L); old.setStatus("submitted");
        old.setQualificationDeadlineAt(start.minusDays(3));
        var valid = new LeadDO(); valid.setId(3L); valid.setOwnerUserId(20L); valid.setStatus("valid");
        when(leadMapper.selectByOwnerUserIds(List.of(20L))).thenReturn(List.of(fresh, old, valid));
        when(assignmentHistoryMapper.selectTodayByUserIds(List.of(20L), start, end)).thenReturn(List.of(
                assignment(1L, "dispatch"), assignment(1L, "dispatch"), assignment(1L, "timeout"),
                assignment(2L, "accept"), assignment(2L, "claim"), assignment(3L, "claim")));
        var event = new cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO();
        event.setOperatorUserId(20L); event.setAggregateId(99L);
        when(eventMapper.selectTodayByUserIds(List.of(20L), start, end)).thenReturn(List.of(event, event));
        var follow = new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadFollowUpRecordDO(); follow.setOperatorUserId(20L);
        var opportunityFollow = new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityFollowUpRecordDO(); opportunityFollow.setOperatorUserId(20L);
        when(followUpRecordMapper.selectTodayByUserIds(List.of(20L), start, end)).thenReturn(List.of(follow));
        when(opportunityFollowUpRecordMapper.selectTodayByUserIds(List.of(20L), start, end)).thenReturn(List.of(opportunityFollow));
        var order = new cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO();
        order.setSubmitterUserId(20L); order.setEffectiveAt(start); order.setTotalAmount(new java.math.BigDecimal("125.50"));
        var previous = new cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO();
        previous.setSubmitterUserId(20L); previous.setEffectiveAt(start.minusSeconds(1)); previous.setTotalAmount(java.math.BigDecimal.TEN);
        when(orderMapper.selectEffectiveBySubmitterIds(List.of(20L))).thenReturn(List.of(order, previous));
        var row = service.getOverview(20L, 10L);
        assertEquals(2L, row.getTodayPendingCount());
        assertEquals(1L, row.getTodayFollowUpRemainingCount());
        assertEquals(2L, row.getTodayFollowUpTotalCount());
        assertEquals("incomplete", row.getTodayFollowUpStatus());
        assertEquals(2L, row.getPendingQualificationCount());
        assertEquals(1L, row.getTodayAssignedCount());
        assertEquals(1L, row.getTodayMissedCount());
        assertEquals(2L, row.getTodayReceivedCount());
        assertEquals(1L, row.getTodayQualifiedCount());
        assertEquals(2L, row.getTodayFollowUpRecordCount());
        assertEquals(new java.math.BigDecimal("125.50"), row.getTodayOrderAmount());
        assertEquals(new java.math.BigDecimal("135.50"), row.getEffectiveOrderAmount());
        pending.setStatus("completed"); duplicate.setStatus("completed");
        assertEquals("completed", service.getOverview(20L, 10L).getTodayFollowUpStatus());
        when(taskMapper.selectByAssigneeIds(List.of(20L))).thenReturn(List.of());
        row = service.getOverview(20L, 10L);
        assertEquals(0L, row.getTodayFollowUpTotalCount());
        assertEquals("completed", row.getTodayFollowUpStatus());
    }

    @Test
    void overviewDeniedBeforeDailyQueries() {
        when(permissionService.getManagedUserIds(10L)).thenReturn(Set.of());
        assertThrows(ServiceException.class, () -> service.getOverview(20L, 10L));
        org.mockito.Mockito.verifyNoInteractions(assignmentHistoryMapper, eventMapper, followUpRecordMapper, opportunityFollowUpRecordMapper);
    }

    private static cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO task(Long leadId, String status, LocalDateTime dueAt) {
        var task = new cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO();
        task.setBizType("lead"); task.setBizId(leadId); task.setTaskType("lead_first_follow_up");
        task.setAssigneeId(20L); task.setStatus(status); task.setDueAt(dueAt); return task;
    }

    private static cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO assignment(Long leadId, String action) {
        var item = new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAssignmentHistoryDO();
        item.setLeadId(leadId); item.setActionType(action); item.setCandidateUserId(20L); return item;
    }

    @Test
    void administratorReadsDisabledSalesWithoutGainingBulkPauseAuthority() {
        when(permissionApi.hasTenantReadAllAccess(10L)).thenReturn(true);
        PostRespDTO post = new PostRespDTO(); post.setId(5L);
        when(postApi.getPostByCode("sales_specialist")).thenReturn(post);
        when(adminUserApi.getUserListByPostIds(Set.of(5L))).thenReturn(List.of(subordinate(30L, 1, 5L)));
        when(taskMapper.selectMyPending(30L)).thenReturn(List.of());
        assertEquals(0L, service.getTaskPage(30L, new SubordinateTaskPageReqVO(), 10L).getTotal());
        assertEquals(0, service.pauseAllDispatch(10L).getTotalCount());
        verify(dispatchStatusService, never()).pausePreferenceByManager(org.mockito.ArgumentMatchers.anyLong());
        verify(adminUserApi, never()).updateUserStatus(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void subordinateSalesProjectsSystemAvatar() throws Exception {
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(20L); user.setNickname("销售甲"); user.setUsername("sales-a");
        user.setAvatar("https://example.com/sales-a.png"); user.setStatus(0);
        when(dispatchStatusService.getStatus(20L)).thenReturn(new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.dispatch.SalesDispatchStatusRespVO()
                .setPresence("offline").setMode("paused"));
        Method buildRow = SubordinateSalesServiceImpl.class.getDeclaredMethod("buildRow", AdminUserRespDTO.class,
                List.class, List.class, List.class, List.class, boolean.class,
                LocalDateTime.class, LocalDateTime.class, LocalDateTime.class);
        buildRow.setAccessible(true);
        LocalDateTime now = LocalDateTime.now();

        SubordinateSalesRespVO result = (SubordinateSalesRespVO) buildRow.invoke(service, user,
                List.of(), List.of(), List.of(), List.of(), false, now, now.plusDays(1), now);

        assertEquals("https://example.com/sales-a.png", result.getAvatar());
    }

    @Test
    void batchTransferReturnsPartialSuccessPerLead() {
        when(permissionService.getManagedUserIds(10L)).thenReturn(Set.of(20L, 30L));
        when(assignmentService.getEligibleSalesUsers()).thenReturn(List.of(sales(30L)));
        when(leadMapper.selectBatchIds(Set.of(1L, 2L))).thenReturn(List.of(
                new LeadDO().setId(1L).setLeadNo("KZ202608141200000001"),
                new LeadDO().setId(2L).setLeadNo("KZ202608141200000002")));
        doAnswer(invocation -> {
            if (Long.valueOf(2L).equals(invocation.getArgument(0))) {
                throw new ServiceException(SUBORDINATE_LEAD_OWNER_CHANGED);
            }
            return null;
        }).when(commandService).transferOne(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(30L), org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq("团队调整"), org.mockito.ArgumentMatchers.anyString());
        SubordinateBatchLeadActionReqVO request = new SubordinateBatchLeadActionReqVO();
        request.setLeadIds(List.of(1L, 2L)); request.setTargetUserId(30L); request.setReason("  团队调整  ");
        request.setIdempotencyKey("batch-transfer-test");

        SubordinateBatchResultVO result = service.batchLeadAction("transfer", request, 10L);

        assertEquals(1, result.getSuccessCount(), result.getItems().toString());
        assertEquals(1, result.getFailureCount());
        assertEquals("SUCCESS", result.getItems().get(0).getCode());
        assertEquals("KZ202608141200000001", result.getItems().get(0).getLeadNo());
        assertEquals(String.valueOf(SUBORDINATE_LEAD_OWNER_CHANGED.getCode()), result.getItems().get(1).getCode());
        assertEquals("KZ202608141200000002", result.getItems().get(1).getLeadNo());
        verify(commandService).transferOne(1L, 30L, 10L, "团队调整", "batch-transfer-test:1");
        verify(commandService).transferOne(2L, 30L, 10L, "团队调整", "batch-transfer-test:2");
    }

    @Test
    void batchTransferRejectsBlankReasonBeforeMutation() {
        SubordinateBatchLeadActionReqVO request = new SubordinateBatchLeadActionReqVO();
        request.setLeadIds(List.of(1L)); request.setTargetUserId(30L); request.setReason("   ");
        request.setIdempotencyKey("blank-reason-test");
        assertThrows(ServiceException.class, () -> service.batchLeadAction("transfer", request, 10L));
    }

    @Test
    void emptyPendingTaskPageDoesNotQueryLeadsWithEmptyIds() {
        when(permissionService.getManagedUserIds(10L)).thenReturn(Set.of(20L));
        cn.iocoder.yudao.module.system.api.dept.dto.PostRespDTO salesPost =
                new cn.iocoder.yudao.module.system.api.dept.dto.PostRespDTO();
        salesPost.setId(5L);
        AdminUserRespDTO subordinate = new AdminUserRespDTO();
        subordinate.setId(20L); subordinate.setPostIds(Set.of(5L));
        when(postApi.getPostByCode("sales_specialist")).thenReturn(salesPost);
        when(adminUserApi.getUserList(Set.of(20L))).thenReturn(List.of(subordinate));
        when(taskMapper.selectMyPending(20L)).thenReturn(List.of());
        SubordinateTaskPageReqVO request = new SubordinateTaskPageReqVO();

        assertEquals(0L, service.getTaskPage(20L, request, 10L).getTotal());
        verify(leadMapper, never()).selectBatchIds(org.mockito.ArgumentMatchers.anyCollection());
    }

    @Test
    void pauseAllDispatchIncludesDisabledSalesAndAuditsOnlyChanges() {
        PostRespDTO salesPost = new PostRespDTO();
        salesPost.setId(5L);
        AdminUserRespDTO enabled = subordinate(20L, 0, 5L);
        AdminUserRespDTO disabled = subordinate(30L, 1, 5L);
        when(permissionService.getManagedUserIds(10L)).thenReturn(Set.of(20L, 30L, 40L));
        when(postApi.getPostByCode("sales_specialist")).thenReturn(salesPost);
        when(adminUserApi.getUserList(Set.of(20L, 30L, 40L))).thenReturn(List.of(enabled, disabled));
        when(dispatchStatusService.pausePreferenceByManager(20L)).thenReturn(true);
        when(dispatchStatusService.pausePreferenceByManager(30L)).thenReturn(false);

        var result = service.pauseAllDispatch(10L);

        assertEquals(2, result.getTotalCount());
        assertEquals(1, result.getChangedCount());
        assertEquals(1, result.getAlreadyPausedCount());
        verify(dispatchStatusService).pausePreferenceByManager(20L);
        verify(dispatchStatusService).pausePreferenceByManager(30L);
        verify(commandService).addAudit("dispatch_mode_bulk_pause", 10L, 20L, null,
                "accepting", "paused", "主管一键下班");
        verify(commandService, never()).addAudit("dispatch_mode_bulk_pause", 10L, 30L, null,
                "accepting", "paused", "主管一键下班");
    }

    @Test
    void pauseAllDispatchReturnsZeroForManagerWithoutSubordinates() {
        when(permissionService.getManagedUserIds(10L)).thenReturn(Set.of());

        var result = service.pauseAllDispatch(10L);

        assertEquals(0, result.getTotalCount());
        assertEquals(0, result.getChangedCount());
        assertEquals(0, result.getAlreadyPausedCount());
        verify(dispatchStatusService, never()).pausePreferenceByManager(org.mockito.ArgumentMatchers.anyLong());
    }

    private static LeadAssignmentUserRespVO sales(Long id) {
        LeadAssignmentUserRespVO result = new LeadAssignmentUserRespVO();
        result.setId(id); result.setNickname("销售" + id); return result;
    }

    private static AdminUserRespDTO subordinate(Long id, int status, Long postId) {
        AdminUserRespDTO result = new AdminUserRespDTO();
        result.setId(id);
        result.setStatus(status);
        result.setPostIds(Set.of(postId));
        return result;
    }
}
