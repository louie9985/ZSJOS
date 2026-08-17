package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate.SubordinateBatchResultVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate.SubordinateBatchTransferReqVO;
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
                org.mockito.ArgumentMatchers.eq("团队调整"));
        SubordinateBatchTransferReqVO request = new SubordinateBatchTransferReqVO();
        request.setLeadIds(List.of(1L, 2L)); request.setTargetUserId(30L); request.setReason("  团队调整  ");

        SubordinateBatchResultVO result = service.batchTransfer(request, 10L);

        assertEquals(1, result.getSuccessCount(), result.getItems().toString());
        assertEquals(1, result.getFailureCount());
        assertEquals("SUCCESS", result.getItems().get(0).getCode());
        assertEquals("KZ202608141200000001", result.getItems().get(0).getLeadNo());
        assertEquals(String.valueOf(SUBORDINATE_LEAD_OWNER_CHANGED.getCode()), result.getItems().get(1).getCode());
        assertEquals("KZ202608141200000002", result.getItems().get(1).getLeadNo());
        verify(commandService).transferOne(1L, 30L, 10L, "团队调整");
        verify(commandService).transferOne(2L, 30L, 10L, "团队调整");
    }

    @Test
    void batchTransferRejectsBlankReasonBeforeMutation() {
        SubordinateBatchTransferReqVO request = new SubordinateBatchTransferReqVO();
        request.setLeadIds(List.of(1L)); request.setTargetUserId(30L); request.setReason("   ");
        assertThrows(ServiceException.class, () -> service.batchTransfer(request, 10L));
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

    private static LeadAssignmentUserRespVO sales(Long id) {
        LeadAssignmentUserRespVO result = new LeadAssignmentUserRespVO();
        result.setId(id); result.setNickname("销售" + id); return result;
    }
}
