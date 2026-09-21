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
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.StudentDeliveryStageMapper deliveryStages;
    @Mock private StudentServiceObjectPermissionProvider servicePermissions;
    @Mock private cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper relationMapper;
    @Mock private MyStudentService myStudentService;
    @Mock private MediaAccountMapper accountMapper;
    @Mock private PositioningCardMapper positioningMapper;
    @Mock private PositioningCardSubmissionMapper positioningSubmissionMapper;
    @Mock private ContentMapper contentMapper;
    @Mock private ProductionTicketMapper ticketMapper;
    @Mock private MediaAccountService accountService;
    @Mock private cn.iocoder.yudao.module.zsjos.service.account.MediaAccountObjectPermissionProvider accountPermissionProvider;
    @Mock private ContentService contentService;
    @Mock private PositioningCardService positioningService;
    @Mock private AdminUserApi adminUserApi;
    @Mock private MediaStudentTalkRecordMapper talkRecordMapper;
    @Mock private PermissionApi permissionApi;

    @Test
    void detailRestoresOwnUnboundDraftWithoutExposingOtherCards() {
        MyStudentRespVO student = new MyStudentRespVO();
        student.setPersonId(2L); student.setServices(List.of());
        when(myStudentService.getMediaStudent(1L, 2L)).thenReturn(student);
        PositioningCardDO own = new PositioningCardDO().setId(19L).setStudentPersonId(2L)
                .setDirectorUserId(1L).setStatus("co_creating");
        PositioningCardDO otherDirector = new PositioningCardDO().setId(20L).setStudentPersonId(2L)
                .setDirectorUserId(9L).setStatus("co_creating");
        PositioningCardDO bound = new PositioningCardDO().setId(21L).setStudentPersonId(2L)
                .setDirectorUserId(1L).setAccountId(99L).setStatus("co_creating");
        PositioningCardDO submitted = new PositioningCardDO().setId(22L).setStudentPersonId(2L)
                .setDirectorUserId(1L).setStatus("confirmed");
        PositioningCardDO otherStudent = new PositioningCardDO().setId(23L).setStudentPersonId(8L)
                .setDirectorUserId(1L).setStatus("co_creating");
        when(positioningMapper.selectByDirectorAndStudent(1L, 2L))
                .thenReturn(List.of(own, otherDirector, bound, submitted, otherStudent));
        when(positioningService.availableActionsForVisible(own, 1L)).thenReturn(List.of("update"));

        var detail = service.getDetail(1L, 2L);

        assertEquals(List.of(19L), detail.getPositioningDrafts().stream()
                .map(MediaStudentDetailRespVO.PositioningVO::getId).toList());
        assertEquals(List.of("update"), detail.getPositioningDrafts().getFirst().getAvailableActions());
        assertTrue(detail.getAccounts().isEmpty());
        assertTrue(detail.getPositioningCards().isEmpty());
        verify(positioningMapper).selectByStudentAndAccountIds(2L, List.of());
        verify(positioningService, never()).availableActionsForVisible(otherDirector, 1L);
        verify(positioningService, never()).availableActionsForVisible(bound, 1L);
    }

    @Test
    void detailFiltersForeignAccountBeforeLoadingItsBusinessDataAndTimeline() {
        MyStudentRespVO student = new MyStudentRespVO();
        student.setPersonId(2L); student.setServices(List.of());
        MediaAccountDO visible = new MediaAccountDO().setId(3L).setAccountNo("MA-3");
        MediaAccountDO foreign = new MediaAccountDO().setId(99L).setAccountNo("MA-99");
        foreign.setUpdateTime(java.time.LocalDateTime.now());
        when(myStudentService.getMediaStudent(1L, 2L)).thenReturn(student);
        when(accountMapper.selectByStudent(2L)).thenReturn(List.of(visible, foreign));
        when(accountPermissionProvider.hasPermission(3L, "read", 1L)).thenReturn(true);
        when(accountPermissionProvider.hasPermission(99L, "read", 1L)).thenReturn(false);
        when(accountService.projectStudentReadOnly(visible)).thenReturn(new MediaAccountRespVO());

        var result = service.getDetail(1L, 2L);

        assertEquals(List.of(3L), result.getAccounts().stream().map(MediaStudentDetailRespVO.AccountVO::getId).toList());
        assertTrue(result.getOperationTimeline().stream().noneMatch(row -> "account-99".equals(row.getKey())));
        verify(positioningMapper).selectByStudentAndAccountIds(2L, List.of(3L));
        verify(positioningSubmissionMapper).selectByStudentAndAccountIds(2L, List.of(3L));
        verify(contentMapper).selectByAccountIds(List.of(3L));
        verify(ticketMapper).selectByAccountIds(List.of(3L));
        verify(accountService, never()).projectStudentReadOnly(foreign);
    }

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
        when(accountMapper.selectByStudent(2L)).thenReturn(List.of(account));
        when(accountPermissionProvider.hasPermission(3L, "read", 1L)).thenReturn(true);
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
        when(accountMapper.selectByStudent(2L)).thenReturn(List.of(account));
        when(accountPermissionProvider.hasPermission(3L, "read", 1L)).thenReturn(true);
        when(accountService.get(3L, 1L)).thenReturn(new MediaAccountRespVO().setDetailSnapshots(List.of()));
        when(positioningMapper.selectByStudentAndAccountIds(2L, List.of(3L))).thenReturn(List.of(card));
        when(positioningSubmissionMapper.selectByStudentAndAccountIds(2L, List.of(3L)))
                .thenReturn(List.of(latest, effective));
        when(positioningSubmissionMapper.selectCurrentConfirmedByAccount(3L)).thenReturn(effective);
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
    @Test void unboundPositioningNotificationResolvesStudentAndCourseAfterObjectCheck() {
        var card=new PositioningCardRespVO();card.setId(9L);card.setStudentPersonId(4L);card.setServiceRelationId(5L);
        when(positioningService.get(9L,1L)).thenReturn(card);
        var subject=spy(service);doReturn(new MediaStudentDetailRespVO()).when(subject).getDetail(1L,4L);
        var target=subject.resolveTarget(1L,"positioning-card",9L);
        assertEquals(4L,target.getPersonId());assertEquals(5L,target.getServiceRelationId());
        verify(positioningService).get(9L,1L);
    }
    @Test void diagnosisAndDeliveryTargetsRequireAccountObjectAccess() {
        var stage=new cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.StudentDeliveryStageDO();stage.setAccountId(7L);
        when(deliveryStages.selectById(9L)).thenReturn(stage);
        when(accountService.get(7L,1L)).thenThrow(new IllegalStateException("denied"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,()->service.resolveTarget(1L,"student_delivery_stage",9L));
        verify(accountService).get(7L,1L);
        verifyNoInteractions(myStudentService);
    }
}
