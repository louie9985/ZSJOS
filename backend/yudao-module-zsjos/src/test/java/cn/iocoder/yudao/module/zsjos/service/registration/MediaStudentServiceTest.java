package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MediaStudentDetailRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MyStudentRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardSubmissionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.production.ProductionTicketDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardSubmissionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.production.ProductionTicketMapper;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentops.MediaStudentTalkRecordMapper;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningCardRespVO;
import cn.iocoder.yudao.module.zsjos.service.account.MediaAccountService;
import cn.iocoder.yudao.module.zsjos.service.content.ContentService;
import cn.iocoder.yudao.module.zsjos.service.positioning.PositioningCardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaStudentServiceTest {
    @InjectMocks private MediaStudentService service;
    @Mock private MyStudentService myStudentService;
    @Mock private MediaAccountMapper accountMapper;
    @Mock private PositioningCardMapper positioningMapper;
    @Mock private PositioningCardSubmissionMapper positioningSubmissionMapper;
    @Mock private ContentMapper contentMapper;
    @Mock private ProductionTicketMapper ticketMapper;
    @Mock private MediaAccountService accountService;
    @Mock private ContentService contentService;
    @Mock private PositioningCardService positioningService;
    @Mock private AdminUserApi adminUserApi;
    @Mock private MediaStudentTalkRecordMapper talkRecordMapper;
    @Mock private PermissionApi permissionApi;

    @Test
    void detailUsesOnlyDirectorOwnedStudentAccounts() {
        MyStudentRespVO student = new MyStudentRespVO();
        student.setPersonId(2L); student.setName("学员"); student.setServices(List.of());
        MediaAccountDO account = new MediaAccountDO().setId(3L).setAccountNo("MA-3")
                .setNickname("账号").setPlatformLabelSnapshot("视频平台").setSStage("s2");
        ContentDO content = new ContentDO(); content.setId(4L); content.setAccountId(3L); content.setStatus("script");
        PositioningCardDO positioning = new PositioningCardDO().setId(5L).setAccountId(3L)
                .setStudentPersonId(2L).setDirectorUserId(1L).setStatus("co_creating");
        PositioningCardSubmissionDO submission = new PositioningCardSubmissionDO().setId(7L).setCardId(5L)
                .setAccountId(3L).setStudentPersonId(2L).setDirectorUserId(1L).setOperatorUserId(1L)
                .setSubmissionNo(1).setStatus("operator_feasibility").setSubmittedAt(java.time.LocalDateTime.now());
        ProductionTicketDO ticket = new ProductionTicketDO(); ticket.setId(6L); ticket.setAccountId(3L); ticket.setStatus("pending_accept");

        when(myStudentService.getMediaStudent(1L, 2L)).thenReturn(student);
        when(accountMapper.selectByParticipantAndStudent(1L, 2L)).thenReturn(List.of(account));
        MediaAccountRespVO accountDetail = new MediaAccountRespVO();
        accountDetail.setAvailableActions(List.of("update")); accountDetail.setDetailSnapshots(List.of());
        when(accountService.get(3L, 1L)).thenReturn(accountDetail);
        when(positioningMapper.selectByStudentAndAccountIds(2L, List.of(3L))).thenReturn(List.of(positioning));
        when(positioningSubmissionMapper.selectByStudentAndAccountIds(2L, List.of(3L))).thenReturn(List.of(submission));
        when(positioningService.availableActionsForVisible(positioning, 1L)).thenReturn(List.of());
        when(contentMapper.selectByAccountIds(List.of(3L))).thenReturn(List.of(content));
        ContentRespVO contentDetail = new ContentRespVO();
        contentDetail.setAvailableActions(List.of("update-script"));
        when(contentService.availableActionsForVisible(content, 1L, false))
                .thenReturn(contentDetail.getAvailableActions());
        when(ticketMapper.selectByAccountIds(List.of(3L))).thenReturn(List.of(ticket));
        when(talkRecordMapper.selectRecentByStudent(2L)).thenReturn(List.of());

        MediaStudentDetailRespVO result = service.getDetail(1L, 2L);

        assertEquals("学员", result.getStudent().getName());
        assertEquals("视频平台", result.getAccounts().getFirst().getPlatformLabel());
        assertEquals("s2", result.getAccounts().getFirst().getStage());
        assertEquals(4L, result.getContents().getFirst().getId());
        assertEquals(5L, result.getPositioningCards().getFirst().getId());
        assertEquals(7L, result.getPositioningCards().getFirst().getSubmissionId());
        assertEquals(1, result.getPositioningDrafts().size());
        assertEquals(List.of(), result.getPositioningCards().getFirst().getAvailableActions());
        assertEquals(6L, result.getProductionTickets().getFirst().getId());
        assertEquals(2, result.getStudentTaskLine().size());
        assertEquals(0, result.getPendingStats().getPositioningCount());
        assertEquals(1, result.getPendingStats().getContentCount());
        assertEquals(1, result.getPendingStats().getProductionCount());
        verify(positioningService).availableActionsForVisible(positioning, 1L);
    }

    @Test
    void accountTaskLineAllowsMissingPositioningStatus() throws Exception {
        Method method = MediaStudentService.class.getDeclaredMethod("buildAccountTaskLine", String.class);
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(service, (Object) null));
    }

    @Test
    void detailSeparatesLatestRoundFromEffectiveSubmission() {
        MyStudentRespVO student = new MyStudentRespVO();
        student.setPersonId(2L); student.setServices(List.of());
        MediaAccountDO account = new MediaAccountDO().setId(3L).setAccountNo("MA-3");
        PositioningCardDO card = new PositioningCardDO().setId(5L).setAccountId(3L)
                .setStudentPersonId(2L).setDirectorUserId(1L).setStatus("operator_feasibility");
        PositioningCardSubmissionDO latest = new PositioningCardSubmissionDO().setId(8L).setCardId(5L)
                .setAccountId(3L).setStudentPersonId(2L).setSubmissionNo(2)
                .setStatus("operator_feasibility").setSubmittedAt(java.time.LocalDateTime.now());
        PositioningCardSubmissionDO effective = new PositioningCardSubmissionDO().setId(7L).setCardId(5L)
                .setAccountId(3L).setStudentPersonId(2L).setSubmissionNo(1)
                .setStatus("confirmed").setSubmittedAt(java.time.LocalDateTime.now().minusDays(1));
        when(myStudentService.getMediaStudent(1L, 2L)).thenReturn(student);
        when(accountMapper.selectByParticipantAndStudent(1L, 2L)).thenReturn(List.of(account));
        when(accountService.get(3L, 1L)).thenReturn(new MediaAccountRespVO().setDetailSnapshots(List.of()));
        when(positioningMapper.selectByStudentAndAccountIds(2L, List.of(3L))).thenReturn(List.of(card));
        when(positioningSubmissionMapper.selectByStudentAndAccountIds(2L, List.of(3L)))
                .thenReturn(List.of(latest, effective));
        when(contentMapper.selectByAccountIds(List.of(3L))).thenReturn(List.of());
        when(ticketMapper.selectByAccountIds(List.of(3L))).thenReturn(List.of());
        when(talkRecordMapper.selectRecentByStudent(2L)).thenReturn(List.of());

        MediaStudentDetailRespVO result = service.getDetail(1L, 2L);

        var latestProjection = result.getPositioningCards().get(0);
        var effectiveProjection = result.getPositioningCards().get(1);
        assertTrue(latestProjection.getLatestRound());
        assertTrue(latestProjection.getCurrent());
        assertFalse(latestProjection.getEffective());
        assertFalse(effectiveProjection.getLatestRound());
        assertFalse(effectiveProjection.getCurrent());
        assertTrue(effectiveProjection.getEffective());
    }
}
