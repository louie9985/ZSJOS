package cn.iocoder.yudao.module.zsjos.service.delivery;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.delivery.vo.StudentDeliveryDeferReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryDeferDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryStageDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.StudentDeliveryDeferMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.StudentDeliveryStageMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentDeliveryDeferServiceImplTest {
    @InjectMocks private StudentDeliveryDeferServiceImpl service;
    @Mock private StudentDeliveryDeferMapper deferMapper;
    @Mock private StudentDeliveryStageMapper stageMapper;
    @Mock private BpmProcessInstanceApi processInstanceApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private DeptApi deptApi;
    private StudentDeliveryStageDO stage;

    @BeforeEach void setUp() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "delivery-test"), StudentDeliveryStageDO.class);
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(10L).setUserType(2), new MockHttpServletRequest());
        stage = new StudentDeliveryStageDO().setId(1L).setDirectorUserId(10L).setStageCode("S1")
                .setStatus("PENDING").setDueAt(LocalDateTime.of(2026, 9, 14, 12, 0));
    }
    @AfterEach void tearDown() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    private StudentDeliveryDeferReqVO request(int days) {
        return new StudentDeliveryDeferReqVO().setStageId(1L).setRequestedBy(999L)
                .setSupervisorUserId(999L).setRequestedDays(days).setReason("调整交付安排");
    }
    private void stubStage() { when(stageMapper.selectByIdForUpdate(1L, 1L)).thenReturn(stage); }
    private void stubSupervisor() {
        when(adminUserApi.getUser(10L)).thenReturn(new AdminUserRespDTO().setId(10L).setDeptId(20L).setStatus(0));
        when(deptApi.getDept(20L)).thenReturn(new DeptRespDTO().setId(20L).setLeaderUserId(30L).setStatus(0));
        when(adminUserApi.getUser(30L)).thenReturn(new AdminUserRespDTO().setId(30L).setStatus(0));
    }
    @Test void acceptsThirtyDaysAndResolvesIdentityAndApproverOnServer() {
        stubStage(); stubSupervisor();
        when(stageMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        doAnswer(invocation -> { invocation.<StudentDeliveryDeferDO>getArgument(0).setId(50L); return 1; }).when(deferMapper).insert(any(StudentDeliveryDeferDO.class));
        when(processInstanceApi.createProcessInstance(eq(10L), any())).thenReturn("process-1");
        var result = service.request(request(30));
        assertEquals(30, result.getRequestedDays());
        assertEquals(10L, result.getRequestedBy());
        assertEquals(30L, result.getSupervisorUserId());
        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> process = ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(processInstanceApi).createProcessInstance(eq(10L), process.capture());
        assertEquals(java.util.List.of(30L), process.getValue().getStartUserSelectAssignees().get("deliverySupervisorReview"));
        assertEquals("2026-10-14T12:00", process.getValue().getVariables().get("requestedDueAt"));
        assertEquals("process-1", result.getBpmProcessInstanceId());
    }
    @Test void rejectsNonpositiveDays() {
        assertThrows(RuntimeException.class, () -> service.request(request(0)));
        verifyNoInteractions(stageMapper, deferMapper, processInstanceApi);
    }
    @Test void rejectsBlankReason() {
        assertThrows(RuntimeException.class, () -> service.request(request(7).setReason("   ")));
        verifyNoInteractions(stageMapper, deferMapper, processInstanceApi);
    }
    @Test void rejectsAnotherDirectorBeforeWriting() {
        stage.setDirectorUserId(11L); stubStage();
        assertThrows(RuntimeException.class, () -> service.request(request(7)));
        verifyNoInteractions(deferMapper, processInstanceApi);
    }
    @Test void missingSupervisorDoesNotCreateRequest() {
        stubStage();
        assertThrows(RuntimeException.class, () -> service.request(request(7)));
        verify(deferMapper, never()).insert(any(StudentDeliveryDeferDO.class));
        verifyNoInteractions(processInstanceApi);
    }
    @Test void duplicateRequestReturnsPendingApproval() {
        stubStage(); stage.setStatus("DEFER_PENDING");
        var pending = new StudentDeliveryDeferDO().setId(50L).setStatus("PENDING");
        when(deferMapper.selectOne(any(Wrapper.class))).thenReturn(pending);
        assertSame(pending, service.request(request(7)));
        verifyNoInteractions(adminUserApi, processInstanceApi);
    }
    @Test void ignoresRunningEvent() {
        service.handleProcessResult("process-1", 1, null);
        verifyNoInteractions(deferMapper, stageMapper);
    }
    @Test void approvedResultIsIdempotent() {
        stubStage(); stage.setStatus("DEFER_PENDING");
        var row = new StudentDeliveryDeferDO().setId(50L).setStageId(1L).setStatus("PENDING")
                .setOriginalDueAt(stage.getDueAt()).setRequestedDays(30);
        when(deferMapper.selectByProcessInstanceId("process-1")).thenReturn(row);
        when(stageMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        service.handleProcessResult("process-1", 2, "通过");
        service.handleProcessResult("process-1", 2, "通过");
        assertEquals("APPROVED", row.getStatus());
        assertNotNull(row.getDecidedAt());
        verify(stageMapper, times(1)).update(isNull(), any(Wrapper.class));
        verify(deferMapper, times(1)).updateById(row);
    }
    @Test void cancelledResultIsNotRecordedAsRejected() {
        stubStage(); stage.setStatus("DEFER_PENDING");
        var row = new StudentDeliveryDeferDO().setId(50L).setStageId(1L).setStatus("PENDING")
                .setOriginalDueAt(stage.getDueAt()).setRequestedDays(7);
        when(deferMapper.selectByProcessInstanceId("process-1")).thenReturn(row);
        when(stageMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        service.handleProcessResult("process-1", 4, "取消");
        assertEquals("CANCELLED", row.getStatus());
    }
    @Test void requestValidationAcceptsMoreThanThreeDays() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(request(365)).isEmpty());
            assertFalse(validator.validate(request(0)).isEmpty());
        }
    }
    @Test void rejectedResultRetainsOriginalDeadline() {
        stubStage(); stage.setStatus("DEFER_PENDING");
        var row = new StudentDeliveryDeferDO().setId(50L).setStageId(1L).setStatus("PENDING")
                .setOriginalDueAt(stage.getDueAt()).setRequestedDays(7);
        when(deferMapper.selectByProcessInstanceId("process-1")).thenReturn(row);
        when(stageMapper.update(isNull(), any(Wrapper.class))).thenAnswer(invocation -> {
            var update = (com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<StudentDeliveryStageDO>) invocation.getArgument(1);
            assertTrue(update.getParamNameValuePairs().containsValue(row.getOriginalDueAt()));
            return 1;
        });
        service.handleProcessResult("process-1", 3, "驳回");
        assertEquals("REJECTED", row.getStatus());
    }

}
