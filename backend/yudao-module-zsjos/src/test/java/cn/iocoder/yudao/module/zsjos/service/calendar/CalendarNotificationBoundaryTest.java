package cn.iocoder.yudao.module.zsjos.service.calendar;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserCandidatePageReqDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.CalendarNotifyBatchDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.calendar.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@ExtendWith(MockitoExtension.class)
class CalendarNotificationBoundaryTest {
    @InjectMocks private CalendarNotificationService service;
    @Mock private CalendarNotificationAccess notificationAccess;
    @Mock private AdminUserApi adminUserApi;
    @Mock private CalendarNotifyBatchMapper batchMapper;
    @Mock private CalendarNotifyRecipientMapper recipientMapper;

    @BeforeEach void setup() { TenantContextHolder.setTenantId(10L); }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }

    @Test void employeePageDelegatesToSystemAndReturnsMinimalProjection() {
        var req = new CalendarNotifyUserPageReqVO();
        req.setCalendarType("EXAM"); req.setKeyword("测试"); req.setPageNo(2); req.setPageSize(20);
        var employee = new AdminUserRespDTO(); employee.setId(7L); employee.setNickname("测试员工");
        when(adminUserApi.getCandidateUserPage(any())).thenReturn(new PageResult<>(List.of(employee), 25L));
        var page = service.getUsers(req);
        var query = ArgumentCaptor.forClass(AdminUserCandidatePageReqDTO.class);
        var order = inOrder(notificationAccess, adminUserApi);
        order.verify(notificationAccess).check("EXAM", false);
        order.verify(adminUserApi).getCandidateUserPage(query.capture());
        assertEquals("ALL", query.getValue().getQualificationMode());
        assertEquals("测试", query.getValue().getKeyword());
        assertEquals(2, query.getValue().getPageNo()); assertEquals(20, query.getValue().getPageSize());
        assertEquals(25L, page.getTotal());
        assertEquals(List.of(new CalendarNotifyUserRespVO(7L, "测试员工", null)), page.getList());
    }

    @Test void permissionDenialStopsEmployeeLookupAndSendAndPreview() {
        when(notificationAccess.check(anyString(), anyBoolean())).thenThrow(new ServiceException(CALENDAR_NOTIFY_PERMISSION_DENIED));
        var users = new CalendarNotifyUserPageReqVO(); users.setCalendarType("COURSE");
        var command = new CalendarNotifyReqVO(); command.setCalendarType("COURSE"); command.setScope("ALL");
        assertThrows(ServiceException.class, () -> service.getUsers(users));
        assertThrows(ServiceException.class, () -> service.send(command));
        assertThrows(ServiceException.class, () -> service.preview(command));
        verifyNoInteractions(adminUserApi, batchMapper, recipientMapper);
    }

    @Test void historyUsesPersistedCalendarTypeBeforeReadingRecipients() {
        var batch = new CalendarNotifyBatchDO().setCalendarType("EXAM"); batch.setTenantId(10L);
        when(batchMapper.selectById(1L)).thenReturn(batch);
        when(notificationAccess.check("EXAM", false)).thenThrow(new ServiceException(CALENDAR_NOTIFY_PERMISSION_DENIED));
        assertThrows(ServiceException.class, () -> service.getBatch(1L));
        verifyNoInteractions(recipientMapper);
    }

    @Test void foreignTenantHistoryDoesNotExposeSnapshotOrRecipients() {
        var batch = new CalendarNotifyBatchDO().setCalendarType("EXAM"); batch.setTenantId(11L);
        when(batchMapper.selectById(1L)).thenReturn(batch);
        assertEquals(CALENDAR_NOTIFY_BATCH_NOT_EXISTS.getCode(),
                assertThrows(ServiceException.class, () -> service.getBatch(1L)).getCode());
        verifyNoInteractions(notificationAccess, recipientMapper);
    }

    @Test void historyPageChecksTypeBeforeQueryAndReturnsSnapshotCounts() {
        var req = new CalendarNotifyBatchPageReqVO(); req.setCalendarType("COURSE"); req.setCalendarId(7L);
        var row = new CalendarNotifyBatchDO().setId(5L).setCalendarType("COURSE").setTitleSnapshot("历史名称")
                .setRequestedCount(20).setAcceptedCount(18).setSkippedCount(2).setEventType("DELETED");
        when(batchMapper.selectHistoryPage(req, 10L)).thenReturn(new PageResult<>(List.of(row), 21L));
        var result = service.getBatchPage(req);
        var order = inOrder(notificationAccess, batchMapper);
        order.verify(notificationAccess).check("COURSE", false);
        order.verify(batchMapper).selectHistoryPage(req, 10L);
        assertEquals(21L, result.getTotal()); assertEquals(18, result.getList().get(0).getAcceptedCount());
        assertEquals("历史名称", result.getList().get(0).getTitleSnapshot());
        verifyNoInteractions(recipientMapper, adminUserApi);
    }

    @Test void batchDetailDoesNotLoadWholeTenantRoster() {
        var batch = new CalendarNotifyBatchDO().setCalendarType("EXAM").setAcceptedCount(1000); batch.setTenantId(10L);
        when(batchMapper.selectById(1L)).thenReturn(batch);
        assertEquals(1000, service.getBatch(1L).getAcceptedCount());
        verifyNoInteractions(recipientMapper, adminUserApi);
    }

    @Test void recipientPageUsesPersistedTypeAndHistoricalNameWithoutCurrentUserLookup() {
        var batch = new CalendarNotifyBatchDO().setCalendarType("EXAM"); batch.setTenantId(10L);
        when(batchMapper.selectById(1L)).thenReturn(batch);
        var req = new cn.iocoder.yudao.framework.common.pojo.PageParam(); req.setPageNo(2); req.setPageSize(20);
        var row = new cn.iocoder.yudao.module.zsjos.dal.dataobject.calendar.CalendarNotifyRecipientDO()
                .setUserId(3L).setUserType(2).setNicknameSnapshot("当时姓名").setStatus("SKIPPED").setSkipReason("ALREADY_ACCEPTED");
        when(recipientMapper.selectBatchPage(1L, 10L, req)).thenReturn(new PageResult<>(List.of(row), 25L));
        var result = service.getRecipientPage(1L, req);
        assertEquals(25L, result.getTotal()); assertEquals("当时姓名", result.getList().get(0).nickname());
        var order = inOrder(batchMapper, notificationAccess, recipientMapper);
        order.verify(batchMapper).selectById(1L);
        order.verify(notificationAccess).check("EXAM", false);
        order.verify(recipientMapper).selectBatchPage(1L, 10L, req);
        verifyNoInteractions(adminUserApi);
    }

    @Test void historyCannotRequestUnboundedRecipientsOrBypassForeignBatchCheck() {
        var req = new CalendarNotifyBatchPageReqVO(); req.setCalendarType("COURSE"); req.setPageSize(-1);
        assertEquals(CALENDAR_NOTIFY_REQUEST_INVALID.getCode(), assertThrows(ServiceException.class,
                () -> service.getBatchPage(req)).getCode());
        verifyNoInteractions(batchMapper);
        var batch = new CalendarNotifyBatchDO().setCalendarType("EXAM"); batch.setTenantId(11L);
        when(batchMapper.selectById(1L)).thenReturn(batch);
        assertEquals(CALENDAR_NOTIFY_BATCH_NOT_EXISTS.getCode(), assertThrows(ServiceException.class,
                () -> service.getRecipientPage(1L, req)).getCode());
        verifyNoInteractions(recipientMapper);
    }
}
