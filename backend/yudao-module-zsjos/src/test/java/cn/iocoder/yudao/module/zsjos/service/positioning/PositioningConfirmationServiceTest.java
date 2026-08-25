package cn.iocoder.yudao.module.zsjos.service.positioning;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.pub.positioning.vo.PublicPositioningDecisionReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardSubmissionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningConfirmationLinkDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardSubmissionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningConfirmationLinkMapper;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.POSITIONING_STUDENT_CONFIRM;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.POSITIONING_STUDENT_LINK_PENDING;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.POSITIONING_PUBLIC_H5_URL_INVALID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PositioningConfirmationServiceTest {
    @Mock private PositioningCardService cardService;
    @Mock private PositioningCardMapper cardMapper;
    @Mock private PositioningCardSubmissionMapper submissionMapper;
    @Mock private PositioningConfirmationLinkMapper linkMapper;
    @Mock private MediaAccountMapper accountMapper;
    @Mock private MediaWorkflowEventService workflowEventService;
    @InjectMocks private PositioningConfirmationService service;

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void generateLinkStoresOnlyHashAndAdvancesPendingState() {
        ReflectionTestUtils.setField(service, "publicBaseUrl", "https://m.example.com/");
        PositioningCardDO card = card(POSITIONING_STUDENT_LINK_PENDING, 3);
        PositioningCardSubmissionDO submission = submission(POSITIONING_STUDENT_LINK_PENDING, 1);
        when(cardService.require(1L)).thenReturn(card);
        when(cardService.requireLatestSubmission(card, POSITIONING_STUDENT_LINK_PENDING)).thenReturn(submission);
        when(submissionMapper.markStatus(11L, 1, POSITIONING_STUDENT_LINK_PENDING,
                POSITIONING_STUDENT_CONFIRM)).thenReturn(1);
        when(cardMapper.transition(1L, 3, POSITIONING_STUDENT_LINK_PENDING,
                POSITIONING_STUDENT_CONFIRM)).thenReturn(1);

        var result = service.generateLink(1L, 3, 88L);

        ArgumentCaptor<PositioningConfirmationLinkDO> inserted = ArgumentCaptor.forClass(PositioningConfirmationLinkDO.class);
        verify(linkMapper).revokeActiveBySubmission(eq(11L), any());
        verify(linkMapper).insert(inserted.capture());
        String rawToken = result.getSharePath().substring(result.getSharePath().indexOf("#token=") + 7);
        assertTrue(result.getSharePath().startsWith("https://m.example.com/positioning/share#token="));
        assertEquals(64, inserted.getValue().getTokenHash().length());
        assertNotEquals(rawToken, inserted.getValue().getTokenHash());
        assertFalse(inserted.getValue().getTokenHash().contains(rawToken));
    }

    @Test
    void regenerateRevokesOldLinkWithoutChangingCardStateAgain() {
        ReflectionTestUtils.setField(service, "publicBaseUrl", "https://m.example.com");
        PositioningCardDO card = card(POSITIONING_STUDENT_CONFIRM, 4);
        PositioningCardSubmissionDO submission = submission(POSITIONING_STUDENT_CONFIRM, 2);
        when(cardService.require(1L)).thenReturn(card);
        when(cardService.requireLatestSubmission(card, POSITIONING_STUDENT_CONFIRM)).thenReturn(submission);

        var result = service.generateLink(1L, 4, 88L);

        assertTrue(result.getSharePath().startsWith("https://m.example.com/positioning/share#token="));
        verify(linkMapper).revokeActiveBySubmission(eq(11L), any());
        verify(submissionMapper, never()).markStatus(any(), any(), any(), any());
        verify(cardMapper, never()).transition(any(), any(), any(), any());
    }

    @Test
    void generateLinkRejectsMissingPublicBaseUrlBeforeMutation() {
        ReflectionTestUtils.setField(service, "publicBaseUrl", " ");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.generateLink(1L, 3, 88L));

        assertEquals(POSITIONING_PUBLIC_H5_URL_INVALID.getCode(), error.getCode());
        verifyNoInteractions(cardService, cardMapper, submissionMapper, linkMapper, workflowEventService);
    }

    @Test
    void generateLinkRejectsNonHttpPublicBaseUrlBeforeMutation() {
        ReflectionTestUtils.setField(service, "publicBaseUrl", "javascript:alert(1)");

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.generateLink(1L, 3, 88L));

        assertEquals(POSITIONING_PUBLIC_H5_URL_INVALID.getCode(), error.getCode());
        verifyNoInteractions(cardService, cardMapper, submissionMapper, linkMapper, workflowEventService);
    }

    @Test
    void publicDetailReadsSnapshotInsideOwningTenantAndRestoresContext() {
        TenantContextHolder.setTenantId(99L);
        PositioningConfirmationLinkDO link = link("active");
        link.setTenantId(7L);
        PositioningCardDO card = card(POSITIONING_STUDENT_CONFIRM, 4);
        PositioningCardSubmissionDO submission = submission(POSITIONING_STUDENT_CONFIRM, 2)
                .setFieldsSnapshotJson("[{\"key\":\"persona\",\"title\":\"人设\",\"enabled\":true}]")
                .setValuesSnapshotJson("{\"persona\":\"专业顾问\"}")
                .setDictSnapshotJson("{}");
        when(linkMapper.selectByTokenHash(anyString())).thenReturn(link);
        when(cardMapper.selectById(1L)).thenAnswer(ignored -> {
            assertEquals(7L, TenantContextHolder.getTenantId());
            assertFalse(TenantContextHolder.isIgnore());
            return card;
        });
        when(submissionMapper.selectById(11L)).thenReturn(submission);
        when(submissionMapper.selectLatestByCard(1L)).thenReturn(submission);
        when(accountMapper.selectById(10L)).thenReturn(new MediaAccountDO().setId(10L)
                .setNickname("知识账号").setPlatformLabelSnapshot("抖音"));

        var result = service.publicDetail("raw-token");

        assertEquals("ready", result.getState());
        assertEquals("知识账号", result.getAccountName());
        assertEquals("专业顾问", result.getValues().get("persona"));
        assertEquals(99L, TenantContextHolder.getTenantId());
    }

    @Test
    void agreeConsumesLinkAndAdvancesToTrial() {
        PositioningConfirmationLinkDO link = link("active");
        link.setTenantId(7L);
        PositioningCardDO card = card(POSITIONING_STUDENT_CONFIRM, 4);
        PositioningCardSubmissionDO submission = submission(POSITIONING_STUDENT_CONFIRM, 2);
        when(linkMapper.selectByTokenHash(anyString())).thenReturn(link);
        when(linkMapper.selectByTokenHashForUpdate(anyString())).thenReturn(link);
        when(cardMapper.selectByIdForUpdate(1L, 7L)).thenReturn(card);
        when(submissionMapper.selectByIdForUpdate(11L, 7L)).thenReturn(submission);
        when(submissionMapper.selectLatestByCard(1L)).thenReturn(submission);
        when(submissionMapper.markStudentDecision(eq(11L), eq(2), eq(POSITIONING_STUDENT_CONFIRM),
                eq("student_agreed"), eq("agree"), isNull(), any())).thenReturn(1);
        when(linkMapper.consume(eq(21L), eq(0), any())).thenReturn(1);
        PublicPositioningDecisionReqVO request = new PublicPositioningDecisionReqVO();
        request.setDecision("agree");

        service.decide("raw-token", request);

        verify(cardService).studentConfirmFromLink(1L, 4);
        verify(cardService, never()).studentRejectFromLink(any(), any(), any());
    }

    @Test
    void requestChangesRequiresCommentBeforeLookingUpToken() {
        PublicPositioningDecisionReqVO request = new PublicPositioningDecisionReqVO();
        request.setDecision("request_changes");
        request.setComment("   ");

        assertThrows(ServiceException.class, () -> service.decide("raw-token", request));
        verifyNoInteractions(linkMapper);
    }

    @Test
    void usedLinkIsViewableOnlyAsProcessed() {
        PositioningConfirmationLinkDO link = link("used");
        link.setTenantId(7L);
        when(linkMapper.selectByTokenHash(anyString())).thenReturn(link);

        var result = service.publicDetail("raw-token");

        assertEquals("processed", result.getState());
        verifyNoInteractions(cardMapper, submissionMapper, accountMapper);
    }

    private PositioningCardDO card(String status, int version) {
        return new PositioningCardDO().setId(1L).setAccountId(10L).setStudentPersonId(20L)
                .setOperatorUserId(88L).setStatus(status).setVersion(version);
    }

    private PositioningCardSubmissionDO submission(String status, int version) {
        return new PositioningCardSubmissionDO().setId(11L).setCardId(1L).setAccountId(10L)
                .setOperatorUserId(88L).setSubmissionNo(1).setStatus(status).setVersion(version);
    }

    private PositioningConfirmationLinkDO link(String status) {
        return new PositioningConfirmationLinkDO().setId(21L).setCardId(1L).setSubmissionId(11L)
                .setTokenHash("hash").setStatus(status).setVersion(0);
    }
}
