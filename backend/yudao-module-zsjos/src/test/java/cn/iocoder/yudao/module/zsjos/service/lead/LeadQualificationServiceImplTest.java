package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification.LeadDispositionReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification.LeadJudgeInvalidReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification.LeadQualificationCommandReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAssignmentHistoryMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.QUALIFICATION_RESTORED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.QUALIFICATION_SUSPENDED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadQualificationServiceImplTest {
    @InjectMocks private LeadQualificationServiceImpl service;
    @Mock private LeadMapper leadMapper;
    @Mock private LeadAssignmentHistoryMapper historyMapper;
    @Mock private BusinessEventMapper eventMapper;
    @Mock private DictDataApi dictDataApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private LeadAssignmentService assignmentService;
    @Mock private LeadObjectPermissionService permissionService;
    @Mock private LeadLifecycleTaskService lifecycleTaskService;
    @Mock private LeadNotifyEventPublisher notifyEventPublisher;

    @Test
    void judgeValidCompletesCurrentQualificationRound() {
        LeadDO lead = pendingLead();
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        when(eventMapper.selectByIdempotencyKey("lead-qualification:request-1")).thenReturn(null);

        withTenant(() -> service.judgeValid(1L, 20L, command("request-1")));

        assertEquals("valid", lead.getStatus());
        assertEquals(20L, lead.getQualifiedByUserId());
        assertNotNull(lead.getQualifiedAt());
        verify(lifecycleTaskService).completeQualificationTask(eq(1L), eq(2), any(LocalDateTime.class));
        verify(eventMapper).insert(argThat((BusinessEventDO event) ->
                "lead_qualified_valid".equals(event.getEventType())));
    }

    @Test
    void judgeInvalidStoresStableReasonAndLabelSnapshot() {
        LeadDO lead = pendingLead();
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        when(eventMapper.selectByIdempotencyKey("lead-qualification:request-2")).thenReturn(null);
        DictDataRespDTO reason = new DictDataRespDTO();
        reason.setValue("no_budget"); reason.setLabel("暂无预算"); reason.setStatus(0);
        when(dictDataApi.getDictDataList("zsjos_lead_invalid_reason")).thenReturn(List.of(reason));
        LeadJudgeInvalidReqVO request = new LeadJudgeInvalidReqVO();
        request.setIdempotencyKey("request-2"); request.setReasonCode("no_budget");
        request.setDescription("  客户确认本季度无预算  ");

        withTenant(() -> service.judgeInvalid(1L, 20L, request));

        assertEquals("invalid", lead.getStatus());
        assertEquals("no_budget", lead.getInvalidReason());
        assertEquals("暂无预算", lead.getInvalidReasonLabelSnapshot());
        assertEquals("客户确认本季度无预算", lead.getInvalidDescription());
    }

    @Test
    void schedulerSuspendsOnlyAfterItLocksStillExpiredLead() {
        LeadDO lead = pendingLead();
        lead.setQualificationDeadlineAt(LocalDateTime.now().minusMinutes(1));
        when(leadMapper.selectExpiredQualifications(any(LocalDateTime.class))).thenReturn(List.of(lead));
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);

        int processed = withTenantResult(service::processExpired);

        assertEquals(1, processed);
        assertEquals("suspended", lead.getStatus());
        assertNotNull(lead.getSuspendedAt());
        verify(notifyEventPublisher).publish(eq(QUALIFICATION_SUSPENDED), eq(1L), anyString(),
                isNull(), any(LocalDateTime.class), anyMap());
    }

    @Test
    void restoreStartsNewQualificationRoundAndPublishesDisposition() {
        LeadDO lead = pendingLead();
        lead.setStatus("suspended");
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        when(eventMapper.selectByIdempotencyKey("lead-disposition:request-3")).thenReturn(null);
        when(permissionService.hasQualificationManageAll()).thenReturn(true);
        when(assignmentService.getEligibleSalesUsers()).thenReturn(List.of(candidate(20L)));
        LeadDispositionReqVO request = new LeadDispositionReqVO();
        request.setIdempotencyKey("request-3"); request.setReason("主管恢复");

        withTenant(() -> service.restore(1L, 99L, request));

        assertEquals("submitted", lead.getStatus());
        verify(lifecycleTaskService).createQualificationTask(eq(lead), eq(20L), any(LocalDateTime.class));
        verify(notifyEventPublisher).publish(eq(QUALIFICATION_RESTORED), eq(1L), anyString(),
                eq(99L), any(LocalDateTime.class), anyMap());
    }

    @Test
    void repeatedJudgmentReturnsWithoutSecondMutation() {
        LeadDO lead = pendingLead();
        BusinessEventDO event = new BusinessEventDO();
        event.setAggregateId(1L); event.setOperatorUserId(20L); event.setEventType("lead_qualified_valid");
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        when(eventMapper.selectByIdempotencyKey("lead-qualification:request-4")).thenReturn(event);

        withTenant(() -> service.judgeValid(1L, 20L, command("request-4")));

        verify(leadMapper, never()).updateById(any(LeadDO.class));
        verify(lifecycleTaskService, never()).completeQualificationTask(anyLong(), anyInt(), any());
    }

    @Test
    void suspendedTransferCandidatesExcludeCurrentOwner() {
        LeadDO lead = pendingLead();
        lead.setStatus("suspended");
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(permissionService.hasQualificationManageAll()).thenReturn(true);
        when(assignmentService.getEligibleSalesUsers()).thenReturn(List.of(candidate(20L), candidate(30L)));

        var result = service.getTransferCandidates(1L, 99L);

        assertEquals(List.of(30L), result.stream().map(item -> item.getId()).toList());
    }

    private LeadDO pendingLead() {
        LeadDO lead = new LeadDO();
        lead.setId(1L); lead.setStatus("submitted"); lead.setAssignmentStatus("owned");
        lead.setOwnerUserId(20L); lead.setQualificationRoundNo(2);
        lead.setQualificationDeadlineAt(LocalDateTime.now().plusHours(1));
        return lead;
    }

    private LeadQualificationCommandReqVO command(String key) {
        LeadQualificationCommandReqVO request = new LeadQualificationCommandReqVO();
        request.setIdempotencyKey(key);
        return request;
    }

    private cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO candidate(Long id) {
        cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO result =
                new cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO();
        result.setId(id);
        return result;
    }

    private void withTenant(Runnable runnable) {
        try (MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class)) {
            tenant.when(TenantContextHolder::getRequiredTenantId).thenReturn(9L);
            runnable.run();
        }
    }

    private int withTenantResult(java.util.function.IntSupplier supplier) {
        try (MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class)) {
            tenant.when(TenantContextHolder::getRequiredTenantId).thenReturn(9L);
            return supplier.getAsInt();
        }
    }
}
