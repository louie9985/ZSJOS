package cn.iocoder.yudao.module.system.service.notice;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.xss.core.clean.XssCleaner;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.infra.api.websocket.WebSocketSenderApi;
import cn.iocoder.yudao.module.system.controller.admin.notice.vo.NoticeAttachmentVO;
import cn.iocoder.yudao.module.system.controller.admin.notice.vo.NoticeMyRespVO;
import cn.iocoder.yudao.module.system.controller.admin.notice.vo.NoticeSaveReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.notice.NoticeAttachmentDO;
import cn.iocoder.yudao.module.system.dal.dataobject.notice.NoticeDO;
import cn.iocoder.yudao.module.system.dal.mysql.notice.NoticeAttachmentMapper;
import cn.iocoder.yudao.module.system.dal.mysql.notice.NoticeMapper;
import cn.iocoder.yudao.module.system.dal.mysql.notice.NoticeReadMapper;
import cn.iocoder.yudao.module.system.enums.notice.NoticePublishStatusEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Import(NoticeServiceImpl.class)
class NoticeServiceImplTest extends BaseDbUnitTest {

    private static final Long USER_ID = 7L;

    @Resource private NoticeServiceImpl noticeService;
    @Resource private NoticeMapper noticeMapper;
    @Resource private NoticeAttachmentMapper attachmentMapper;
    @Resource private NoticeReadMapper readMapper;

    @MockitoBean private FileApi fileApi;
    @MockitoBean private XssCleaner xssCleaner;
    @MockitoBean private WebSocketSenderApi webSocketSenderApi;

    @BeforeEach
    void setUp() {
        when(xssCleaner.clean(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldCreateSanitizedDraftWithOwnedAttachmentSnapshot() {
        when(xssCleaner.clean("<p onclick=bad>正文</p>")).thenReturn("<p>正文</p>");
        when(fileApi.getFileInfo(101L)).thenReturn(file(101L, USER_ID));
        NoticeSaveReqVO request = saveRequest("<p onclick=bad>正文</p>");
        request.setAttachments(List.of(attachment(101L)));

        Long id = noticeService.createNotice(request, USER_ID);

        NoticeDO stored = noticeMapper.selectById(id);
        assertEquals(NoticePublishStatusEnum.DRAFT.getStatus(), stored.getPublishStatus());
        assertEquals("<p>正文</p>", stored.getContent());
        List<NoticeAttachmentDO> attachments = attachmentMapper.selectListByNoticeId(id);
        assertEquals(1, attachments.size());
        assertEquals("制度.pdf", attachments.get(0).getFileName());
        assertEquals(1024L, attachments.get(0).getFileSize());
    }

    @Test
    void shouldRejectSanitizedEmptyContentWithStableError() {
        when(xssCleaner.clean(anyString())).thenReturn("<script></script>");
        assertServiceException(() -> noticeService.createNotice(saveRequest("<script>bad()</script>"), USER_ID),
                NOTICE_CONTENT_EMPTY);
    }

    @Test
    void shouldRejectAttachmentOutsideCurrentUsersNoticeDirectory() {
        when(fileApi.getFileInfo(101L)).thenReturn(file(101L, 8L));
        NoticeSaveReqVO request = saveRequest("<p>正文</p>");
        request.setAttachments(List.of(attachment(101L)));

        assertServiceException(() -> noticeService.createNotice(request, USER_ID), NOTICE_ATTACHMENT_INVALID);
        assertEquals(0, noticeMapper.selectCount());
    }

    @Test
    void shouldEnforceDraftOnlyMutationAndLifecycleTransitions() {
        NoticeDO notice = insertNotice(NoticePublishStatusEnum.DRAFT, null);

        noticeService.publishNotice(notice.getId());
        NoticeDO published = noticeMapper.selectById(notice.getId());
        assertEquals(NoticePublishStatusEnum.PUBLISHED.getStatus(), published.getPublishStatus());
        assertNotNull(published.getPublishTime());
        assertServiceException(() -> noticeService.updateNotice(saveRequest(notice.getId()), USER_ID), NOTICE_NOT_DRAFT);
        assertServiceException(() -> noticeService.deleteNotice(notice.getId()), NOTICE_NOT_DRAFT);

        noticeService.offlineNotice(notice.getId());
        NoticeDO offline = noticeMapper.selectById(notice.getId());
        assertEquals(NoticePublishStatusEnum.OFFLINE.getStatus(), offline.getPublishStatus());
        assertNotNull(offline.getOfflineTime());
        assertServiceException(() -> noticeService.getMyNotice(notice.getId(), USER_ID), NOTICE_NOT_PUBLISHED);
    }

    @Test
    void shouldExposeOnlyPublishedNoticesAndPersistReadState() {
        insertNotice(NoticePublishStatusEnum.DRAFT, null);
        NoticeDO published = insertNotice(NoticePublishStatusEnum.PUBLISHED, LocalDateTime.now());
        insertNotice(NoticePublishStatusEnum.OFFLINE, LocalDateTime.now().minusMinutes(1));

        assertEquals(1, noticeService.getMyNoticePage(page(), USER_ID).getTotal());
        assertEquals(1L, noticeService.getUnreadSummary(USER_ID).getUnreadCount());
        assertEquals(published.getId(), noticeService.getUnreadSummary(USER_ID).getLatest().getId());

        noticeService.markRead(published.getId(), USER_ID);
        noticeService.markRead(published.getId(), USER_ID);

        assertEquals(1, readMapper.selectListByNoticeIdsAndUserId(List.of(published.getId()), USER_ID).size());
        assertEquals(0L, noticeService.getUnreadSummary(USER_ID).getUnreadCount());
        NoticeMyRespVO detail = noticeService.getMyNotice(published.getId(), USER_ID);
        assertTrue(detail.getRead());
        assertNotNull(detail.getReadTime());
    }

    @Test
    void shouldKeepAttachmentSnapshotWhenInfraFileWasDeleted() {
        NoticeDO published = insertNotice(NoticePublishStatusEnum.PUBLISHED, LocalDateTime.now());
        NoticeAttachmentDO attachment = new NoticeAttachmentDO();
        attachment.setNoticeId(published.getId());
        attachment.setInfraFileId(404L);
        attachment.setFileName("已删除.pdf");
        attachment.setFileSize(100L);
        attachment.setSort(0);
        attachmentMapper.insert(attachment);
        when(fileApi.presignGetUrl(404L, 600)).thenThrow(new IllegalStateException("missing"));

        NoticeMyRespVO detail = noticeService.getMyNotice(published.getId(), USER_ID);

        assertEquals(1, detail.getAttachments().size());
        assertEquals("已删除.pdf", detail.getAttachments().get(0).getFileName());
        assertNull(detail.getAttachments().get(0).getDownloadUrl());
    }

    @Test
    void shouldCopyPublishedNoticeToLengthSafeDraft() {
        NoticeDO source = insertNotice(NoticePublishStatusEnum.PUBLISHED, LocalDateTime.now());
        source.setTitle("长".repeat(50));
        noticeMapper.updateById(source);

        NoticeDO copy = noticeMapper.selectById(noticeService.copyNotice(source.getId()));

        assertEquals(50, copy.getTitle().length());
        assertTrue(copy.getTitle().endsWith("（副本）"));
        assertEquals(NoticePublishStatusEnum.DRAFT.getStatus(), copy.getPublishStatus());
    }

    private NoticeSaveReqVO saveRequest(String content) {
        NoticeSaveReqVO request = new NoticeSaveReqVO();
        request.setTitle("测试公告");
        request.setType(2);
        request.setContent(content);
        return request;
    }

    private NoticeSaveReqVO saveRequest(Long id) {
        NoticeSaveReqVO request = saveRequest("<p>修改正文</p>");
        request.setId(id);
        return request;
    }

    private NoticeDO insertNotice(NoticePublishStatusEnum publishStatus, LocalDateTime publishTime) {
        NoticeDO notice = new NoticeDO();
        notice.setTitle(publishStatus.name());
        notice.setType(2);
        notice.setContent("<p>正文</p>");
        notice.setStatus(0);
        notice.setPublishStatus(publishStatus.getStatus());
        notice.setPublishTime(publishTime);
        noticeMapper.insert(notice);
        return notice;
    }

    private NoticeAttachmentVO attachment(Long fileId) {
        NoticeAttachmentVO attachment = new NoticeAttachmentVO();
        attachment.setInfraFileId(fileId);
        return attachment;
    }

    private FileInfoRespDTO file(Long id, Long creator) {
        return new FileInfoRespDTO(id, 1L, "制度.pdf", "system/notice/" + creator + "/20260826/制度.pdf",
                "https://files.example/制度.pdf", "application/pdf", 1024L, String.valueOf(creator));
    }

    private PageParam page() {
        PageParam page = new PageParam();
        page.setPageNo(1);
        page.setPageSize(20);
        return page;
    }

}
