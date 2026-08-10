package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup.LeadFollowUpCreateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadFollowUpImageMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadFollowUpRecordMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_FOLLOW_UP_STATE_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_FOLLOW_UP_TIME_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

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

    private LeadDO validLead() {
        LeadDO lead = new LeadDO();
        lead.setId(1L); lead.setStatus("submitted"); lead.setAssignmentStatus("owned");
        lead.setOwnerUserId(20L); lead.setCurrentAssignmentHistoryId(88L); lead.setLeadCategory("a");
        return lead;
    }

    private LeadFollowUpCreateReqVO request(LocalDateTime nextAt) {
        LeadFollowUpCreateReqVO request = new LeadFollowUpCreateReqVO();
        request.setMethod("phone"); request.setResult("interested"); request.setLeadCategory("a");
        request.setNextFollowUpAt(nextAt); request.setIdempotencyKey("request-1");
        return request;
    }
}
