package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate.SubordinateBatchResultVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate.SubordinateBatchTransferReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate.SubordinateSalesRespVO;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubordinateSalesServiceImplTest {
    @InjectMocks private SubordinateSalesServiceImpl service;
    @Mock private LeadObjectPermissionService permissionService;
    @Mock private LeadAssignmentService assignmentService;
    @Mock private SubordinateSalesCommandService commandService;
    @Mock private SalesDispatchStatusService dispatchStatusService;

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
        doAnswer(invocation -> {
            if (Long.valueOf(2L).equals(invocation.getArgument(0))) {
                throw new ServiceException(SUBORDINATE_LEAD_OWNER_CHANGED);
            }
            return null;
        }).when(commandService).transferOne(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.eq(30L), org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq("团队调整"));
        SubordinateBatchTransferReqVO request = new SubordinateBatchTransferReqVO();
        request.setLeadIds(List.of(1L, 2L)); request.setTargetUserId(30L); request.setReason("  团队调整  ");

        SubordinateBatchResultVO result = service.batchTransfer(request, 10L);

        assertEquals(1, result.getSuccessCount(), result.getItems().toString());
        assertEquals(1, result.getFailureCount());
        assertEquals("SUCCESS", result.getItems().get(0).getCode());
        assertEquals(String.valueOf(SUBORDINATE_LEAD_OWNER_CHANGED.getCode()), result.getItems().get(1).getCode());
        verify(commandService).transferOne(1L, 30L, 10L, "团队调整");
        verify(commandService).transferOne(2L, 30L, 10L, "团队调整");
    }

    @Test
    void batchTransferRejectsBlankReasonBeforeMutation() {
        SubordinateBatchTransferReqVO request = new SubordinateBatchTransferReqVO();
        request.setLeadIds(List.of(1L)); request.setTargetUserId(30L); request.setReason("   ");
        assertThrows(ServiceException.class, () -> service.batchTransfer(request, 10L));
    }

    private static LeadAssignmentUserRespVO sales(Long id) {
        LeadAssignmentUserRespVO result = new LeadAssignmentUserRespVO();
        result.setId(id); result.setNickname("销售" + id); return result;
    }
}
