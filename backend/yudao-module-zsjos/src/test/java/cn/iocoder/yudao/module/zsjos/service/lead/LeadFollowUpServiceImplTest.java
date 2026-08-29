package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup.LeadFollowUpCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup.LeadFollowUpRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadFollowUpRecordDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityFollowUpRecordDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadFollowUpImageMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadFollowUpRecordMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityFollowUpImageMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityFollowUpRecordMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_FOLLOW_UP_STATE_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_FOLLOW_UP_TIME_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadFollowUpServiceImplTest {
    @InjectMocks private LeadFollowUpServiceImpl service;
    @Mock private LeadMapper leadMapper;
    @Mock private LeadFollowUpRecordMapper recordMapper;
    @Mock private LeadFollowUpImageMapper imageMapper;
    @Mock private BusinessEventMapper eventMapper;
    @Mock private DictDataApi dictDataApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private FileApi fileApi;
    @Mock private LeadAttachmentService attachmentService;
    @Mock private LeadLifecycleTaskService lifecycleTaskService;
    @Mock private LeadNotifyEventPublisher notifyEventPublisher;
    @Mock private OpportunityMapper opportunityMapper;
    @Mock private OpportunityFollowUpRecordMapper opportunityRecordMapper;
    @Mock private OpportunityFollowUpImageMapper opportunityImageMapper;
    @Mock private LeadCollaborationService collaborationService;

    @Test
    void createRejectsLeadOutsideOwnedSubmittedCycle() {
        LeadDO lead = validLead();
        lead.setAssignmentStatus("public_pool");
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        try (MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class)) {
            tenant.when(TenantContextHolder::getRequiredTenantId).thenReturn(9L);
            ServiceException error = assertThrows(ServiceException.class,
                    () -> service.create(1L, 20L, request(LocalDateTime.now().plusHours(1))));
            assertEquals(LEAD_FOLLOW_UP_STATE_INVALID.getCode(), error.getCode());
        }
    }

    @Test
    void createRejectsNextTimeThatIsNotFuture() {
        LeadDO lead = validLead();
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        when(recordMapper.selectByIdempotencyKey("request-1")).thenReturn(null);
        try (MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class)) {
            tenant.when(TenantContextHolder::getRequiredTenantId).thenReturn(9L);
            ServiceException error = assertThrows(ServiceException.class,
                    () -> service.create(1L, 20L, request(LocalDateTime.now().minusMinutes(1))));
            assertEquals(LEAD_FOLLOW_UP_TIME_INVALID.getCode(), error.getCode());
        }
    }

    @Test
    void invalidLeadRejectsNewFollowUp() {
        LeadDO lead = validLead();
        lead.setStatus("invalid"); lead.setAssignmentStatus("owned");
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);

        ServiceException error = assertThrows(ServiceException.class,
                () -> withTenant(() -> service.create(1L, 20L, request(LocalDateTime.now().plusHours(1)))));

        assertEquals(LEAD_FOLLOW_UP_STATE_INVALID.getCode(), error.getCode());
        verifyNoInteractions(lifecycleTaskService);
    }

    @Test
    void submittedLeadFollowUpUsesLeadReminderScope() {
        LeadDO lead = validLead();
        stubSuccessfulCreate(lead);
        doAnswer(invocation -> {
            invocation.<LeadFollowUpRecordDO>getArgument(0).setId(40L);
            return 1;
        }).when(recordMapper).insert(any(LeadFollowUpRecordDO.class));

        withTenant(() -> service.create(1L, 20L, request(LocalDateTime.now().plusHours(1))));

        verify(lifecycleTaskService).replaceFollowUpReminder(eq(1L), eq(20L), eq("lead"), eq(40L),
                any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void firstUnreachableFollowUpCreatesSubmitterAssistTaskAndNotification() {
        LeadDO lead = validLead();
        lead.setSourceUserId(30L);
        stubSuccessfulCreate(lead);
        when(dictDataApi.getDictDataList("zsjos_lead_follow_up_result"))
                .thenReturn(List.of(dict("unreachable", "未联系上")));
        when(lifecycleTaskService.completeFirstFollowUpTask(eq(88L), any(LocalDateTime.class))).thenReturn(true);
        doAnswer(invocation -> {
            invocation.<LeadFollowUpRecordDO>getArgument(0).setId(40L);
            return 1;
        }).when(recordMapper).insert(any(LeadFollowUpRecordDO.class));

        LeadFollowUpCreateReqVO request = request(LocalDateTime.now().plusHours(1));
        request.setResult("unreachable");
        request.setRemark("电话多次未接，请协助确认联系方式");
        withTenant(() -> service.create(1L, 20L, request));

        verify(lifecycleTaskService).createQualificationTask(eq(lead), eq(20L), any(LocalDateTime.class));
        verify(lifecycleTaskService).createSubmitterAssistTask(eq(lead), eq(40L), any(LocalDateTime.class),
                eq("未联系上"), eq("电话多次未接，请协助确认联系方式"));
        verify(notifyEventPublisher).publish(eq("zsjos.lead.submitter_assist_requested"), eq(1L),
                eq("lead-submitter-assist-requested:40"), eq(20L), any(LocalDateTime.class), anyMap());
    }

    @Test
    void createDoesNotStoreCategoryKeyWhenDictionaryLabelIsMissing() {
        LeadDO lead = validLead();
        stubSuccessfulCreate(lead);
        when(dictDataApi.getDictDataList("zsjos_lead_category")).thenReturn(List.of());
        doAnswer(invocation -> {
            invocation.<LeadFollowUpRecordDO>getArgument(0).setId(40L);
            return 1;
        }).when(recordMapper).insert(any(LeadFollowUpRecordDO.class));

        withTenant(() -> service.create(1L, 20L, request(LocalDateTime.now().plusHours(1))));

        ArgumentCaptor<LeadFollowUpRecordDO> captor = ArgumentCaptor.forClass(LeadFollowUpRecordDO.class);
        verify(recordMapper).insert(captor.capture());
        assertNull(captor.getValue().getCategoryBeforeLabelSnapshot());
        assertNull(captor.getValue().getCategoryAfterLabelSnapshot());
    }

    @Test
    void validLeadFollowUpBelongsToOpportunityAndUpdatesReminder() {
        LeadDO lead = validLead();
        lead.setStatus("valid"); lead.setAssignmentStatus("owned");
        stubSuccessfulCreate(lead);
        OpportunityDO opportunity = new OpportunityDO();
        opportunity.setId(30L); opportunity.setStatus("open");
        when(opportunityMapper.selectByLeadId(1L)).thenReturn(opportunity);
        doAnswer(invocation -> {
            invocation.<OpportunityFollowUpRecordDO>getArgument(0).setId(50L);
            return 1;
        }).when(opportunityRecordMapper).insert(any(OpportunityFollowUpRecordDO.class));

        LeadFollowUpRespVO result = withTenant(() -> service.create(1L, 20L,
                request(LocalDateTime.now().plusHours(1))));

        assertEquals("opportunity", result.getRecordScope());
        assertEquals(30L, result.getOpportunityId());
        assertEquals("following", opportunity.getStatus());
        verify(opportunityRecordMapper).insert(any(OpportunityFollowUpRecordDO.class));
        verify(lifecycleTaskService).replaceFollowUpReminder(eq(1L), eq(20L), eq("opportunity"), eq(50L),
                any(LocalDateTime.class), any(LocalDateTime.class));
    }

    private void stubSuccessfulCreate(LeadDO lead) {
        when(leadMapper.selectByIdForUpdate(1L, 9L)).thenReturn(lead);
        when(recordMapper.selectByIdempotencyKey("request-1")).thenReturn(null);
        when(opportunityRecordMapper.selectByIdempotencyKey("request-1")).thenReturn(null);
        when(dictDataApi.getDictDataList(anyString())).thenAnswer(invocation -> {
            String type = invocation.getArgument(0);
            if ("zsjos_lead_follow_up_method".equals(type)) return List.of(dict("phone", "电话"));
            if ("zsjos_lead_follow_up_result".equals(type)) return List.of(dict("interested", "有意向"),
                    dict("unreachable", "未联系上"));
            return List.of(dict("a", "A 类"));
        });
        when(adminUserApi.getUser(20L)).thenReturn(null);
        when(attachmentService.validateReferences(anyList(), eq(20L))).thenReturn(Map.of());
        lenient().when(imageMapper.selectListByRecordIds(anyList())).thenReturn(List.of());
        lenient().when(opportunityImageMapper.selectListByRecordIds(anyList())).thenReturn(List.of());
    }

    private DictDataRespDTO dict(String value, String label) {
        DictDataRespDTO item = new DictDataRespDTO();
        item.setValue(value); item.setLabel(label); item.setStatus(0);
        return item;
    }

    private <T> T withTenant(java.util.function.Supplier<T> supplier) {
        try (MockedStatic<TenantContextHolder> tenant = mockStatic(TenantContextHolder.class)) {
            tenant.when(TenantContextHolder::getRequiredTenantId).thenReturn(9L);
            return supplier.get();
        }
    }

    private LeadDO validLead() {
        LeadDO lead = new LeadDO();
        lead.setId(1L); lead.setStatus("submitted"); lead.setAssignmentStatus("owned");
        lead.setOwnerUserId(20L); lead.setCurrentAssignmentHistoryId(88L); lead.setLeadCategory("a");
        return lead;
    }

    private LeadFollowUpCreateReqVO request(LocalDateTime nextAt) {
        LeadFollowUpCreateReqVO request = new LeadFollowUpCreateReqVO();
        request.setMethod("phone"); request.setResult("interested"); request.setLeadCategory("a");
        request.setRemark("已联系客户");
        request.setNextFollowUpAt(nextAt); request.setIdempotencyKey("request-1");
        return request;
    }
}
