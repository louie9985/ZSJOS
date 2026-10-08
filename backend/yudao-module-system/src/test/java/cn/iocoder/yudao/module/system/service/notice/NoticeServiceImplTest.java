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
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.dataobject.notice.NoticeAttachmentDO;
import cn.iocoder.yudao.module.system.dal.dataobject.notice.NoticeDO;
import cn.iocoder.yudao.module.system.dal.mysql.notice.NoticeAttachmentMapper;
import cn.iocoder.yudao.module.system.dal.mysql.notice.NoticeMapper;
import cn.iocoder.yudao.module.system.dal.mysql.notice.NoticeReadMapper;
import cn.iocoder.yudao.module.system.dal.mysql.notice.NoticeRecipientMapper;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import cn.iocoder.yudao.module.system.service.permission.PermissionService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import cn.iocoder.yudao.module.system.enums.notice.NoticePublishStatusEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

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
    @Resource private NoticeRecipientMapper recipientMapper;

    @MockitoBean private FileApi fileApi;
    @MockitoBean private XssCleaner xssCleaner;
    @MockitoBean private WebSocketSenderApi webSocketSenderApi;
    @MockitoBean private DeptService deptService;
    @MockitoBean private AdminUserService userService;
    @MockitoBean private PermissionService permissionService;

    @BeforeEach
    void setUp() {
        AdminUserDO publisher = new AdminUserDO(); publisher.setId(900L); publisher.setNickname("测试发布人"); publisher.setDeptId(900L);
        DeptDO origin = new DeptDO(); origin.setId(900L); origin.setName("测试来源部门");
        when(userService.getUser(900L)).thenReturn(publisher);
        when(deptService.getDept(900L)).thenReturn(origin);
        when(xssCleaner.clean(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldAllow100MbZipAndRejectOneByteOverBeforeStorage() throws Exception {
        var upload = org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        when(upload.getOriginalFilename()).thenReturn("notice.zip");
        when(upload.getSize()).thenReturn(100L * 1024 * 1024);
        when(upload.getBytes()).thenReturn(new byte[]{80, 75});
        when(upload.getContentType()).thenReturn("application/zip");
        when(fileApi.createFileInfo(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("notice.zip"),
                org.mockito.ArgumentMatchers.eq("system/notice/7"), org.mockito.ArgumentMatchers.eq("application/zip")))
                .thenReturn(file(101L, USER_ID));
        assertEquals(101L, noticeService.uploadAttachment(upload, USER_ID).getInfraFileId());
        when(upload.getSize()).thenReturn(100L * 1024 * 1024 + 1);
        assertServiceException(() -> noticeService.uploadAttachment(upload, USER_ID), NOTICE_ATTACHMENT_INVALID);
        org.mockito.Mockito.verify(fileApi, org.mockito.Mockito.times(1)).createFileInfo(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
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
    void shouldKeepAttachmentBindingsAcrossRepeatedSavesAndPublish() {
        when(fileApi.getFileInfo(101L)).thenReturn(file(101L, USER_ID));
        when(fileApi.getFileInfo(102L)).thenReturn(file(102L, USER_ID));
        NoticeSaveReqVO request = saveRequest("<p>正文</p>");
        request.setAttachments(List.of(attachment(101L)));
        Long id = noticeService.createNotice(request, USER_ID);
        NoticeAttachmentDO original = attachmentMapper.selectListByNoticeId(id).get(0);
        request.setId(id);
        request.setAttachments(List.of(attachment(102L), attachment(101L)));

        noticeService.updateNotice(request, USER_ID);
        noticeService.updateNotice(request, USER_ID);
        noticeService.publishNotice(id, 900L);

        List<NoticeAttachmentDO> saved = attachmentMapper.selectListByNoticeId(id);
        assertEquals(List.of(102L, 101L), saved.stream().map(NoticeAttachmentDO::getInfraFileId).toList());
        assertEquals(List.of(0, 1), saved.stream().map(NoticeAttachmentDO::getSort).toList());
        assertEquals(original.getId(), saved.get(1).getId());
        assertEquals(original.getCreateTime(), saved.get(1).getCreateTime());
        assertEquals(2, attachmentMapper.selectListIncludingDeletedByNoticeId(id).size());
        assertEquals(NoticePublishStatusEnum.PUBLISHED.getStatus(), noticeMapper.selectById(id).getPublishStatus());
    }

    @Test
    void shouldRestoreRemovedOwnedAttachmentWithoutInsertingAnotherBinding() {
        when(fileApi.getFileInfo(101L)).thenReturn(file(101L, USER_ID));
        when(fileApi.getFileInfo(102L)).thenReturn(file(102L, USER_ID));
        NoticeSaveReqVO request = saveRequest("<p>正文</p>");
        request.setAttachments(List.of(attachment(101L), attachment(102L)));
        Long id = noticeService.createNotice(request, USER_ID);
        List<Long> originalIds = attachmentMapper.selectListByNoticeId(id).stream()
                .map(NoticeAttachmentDO::getId).toList();
        request.setId(id);
        request.setAttachments(List.of(attachment(102L)));

        noticeService.updateNotice(request, USER_ID);

        assertEquals(List.of(102L), attachmentMapper.selectListByNoticeId(id).stream()
                .map(NoticeAttachmentDO::getInfraFileId).toList());
        assertEquals(originalIds.get(1), attachmentMapper.selectListByNoticeId(id).get(0).getId());
        assertTrue(attachmentMapper.selectListIncludingDeletedByNoticeId(id).stream()
                .filter(row -> row.getInfraFileId().equals(101L)).findFirst().orElseThrow().getDeleted());
        request.setAttachments(List.of());
        noticeService.updateNotice(request, USER_ID);
        assertTrue(attachmentMapper.selectListByNoticeId(id).isEmpty());

        request.setAttachments(List.of(attachment(101L), attachment(102L)));
        noticeService.updateNotice(request, USER_ID);
        noticeService.updateNotice(request, USER_ID);

        assertEquals(originalIds, attachmentMapper.selectListByNoticeId(id).stream()
                .map(NoticeAttachmentDO::getId).toList());
        assertEquals(2, attachmentMapper.selectListIncludingDeletedByNoticeId(id).size());
    }

    @Test
    void shouldSaveAndPublishCopiedAttachmentsWithoutChangingPublishedSource() {
        when(fileApi.getFileInfo(101L)).thenReturn(file(101L, USER_ID));
        NoticeSaveReqVO request = saveRequest("<p>原正文</p>");
        request.setAttachments(List.of(attachment(101L)));
        Long sourceId = noticeService.createNotice(request, USER_ID);
        noticeService.publishNotice(sourceId, 900L);
        NoticeDO source = noticeMapper.selectById(sourceId);
        NoticeAttachmentDO sourceAttachment = attachmentMapper.selectListByNoticeId(sourceId).get(0);

        Long copyId = noticeService.copyNotice(sourceId);
        NoticeAttachmentDO copyAttachment = attachmentMapper.selectListByNoticeId(copyId).get(0);
        assertNotEquals(sourceAttachment.getId(), copyAttachment.getId());
        assertNull(noticeMapper.selectById(copyId).getPublishTime());
        request.setId(copyId);
        request.setTitle("修改后的副本");
        request.setContent("<p>副本正文</p>");

        // A different authorized editor may retain the copied binding without owning the upload.
        noticeService.updateNotice(request, 8L);
        assertEquals(NoticePublishStatusEnum.DRAFT.getStatus(), noticeMapper.selectById(copyId).getPublishStatus());
        noticeService.updateNotice(request, 8L);
        noticeService.publishNotice(copyId, 900L);

        NoticeDO publishedCopy = noticeMapper.selectById(copyId);
        assertEquals(NoticePublishStatusEnum.PUBLISHED.getStatus(), publishedCopy.getPublishStatus());
        assertNotNull(publishedCopy.getPublishTime());
        assertEquals("修改后的副本", publishedCopy.getTitle());
        assertEquals("<p>副本正文</p>", publishedCopy.getContent());
        assertEquals(List.of(copyAttachment.getId()), attachmentMapper.selectListIncludingDeletedByNoticeId(copyId)
                .stream().map(NoticeAttachmentDO::getId).toList());
        assertEquals(101L, noticeService.getMyNotice(copyId, 8L).getAttachments().get(0).getInfraFileId());
        assertEquals(source, noticeMapper.selectById(sourceId));
        assertEquals(List.of(sourceAttachment), attachmentMapper.selectListByNoticeId(sourceId));
    }

    @Test
    void shouldRetainCopiedAttachmentButRejectReaddingAnotherUsersRemovedUpload() {
        when(fileApi.getFileInfo(101L)).thenReturn(file(101L, USER_ID));
        NoticeSaveReqVO request = saveRequest("<p>正文</p>");
        request.setAttachments(List.of(attachment(101L)));
        Long sourceId = noticeService.createNotice(request, USER_ID);
        noticeService.publishNotice(sourceId, 900L);
        Long copyId = noticeService.copyNotice(sourceId);
        request.setId(copyId);

        noticeService.updateNotice(request, 8L);
        assertEquals(1, attachmentMapper.selectListByNoticeId(copyId).size());
        request.setAttachments(List.of());
        noticeService.updateNotice(request, 8L);
        request.setAttachments(List.of(attachment(101L)));

        assertServiceException(() -> noticeService.updateNotice(request, 8L), NOTICE_ATTACHMENT_INVALID);
        assertTrue(attachmentMapper.selectListByNoticeId(copyId).isEmpty());
        assertTrue(attachmentMapper.selectListIncludingDeletedByNoticeId(copyId).get(0).getDeleted());
        assertEquals(1, attachmentMapper.selectListByNoticeId(sourceId).size());
    }

    @Test
    void shouldRollBackDraftChangesWhenAttachmentValidationFails() {
        when(fileApi.getFileInfo(101L)).thenReturn(file(101L, USER_ID));
        when(fileApi.getFileInfo(102L)).thenReturn(file(102L, USER_ID));
        when(fileApi.getFileInfo(103L)).thenReturn(file(103L, 8L));
        NoticeSaveReqVO request = saveRequest("<p>原正文</p>");
        request.setAttachments(List.of(attachment(101L), attachment(102L)));
        Long id = noticeService.createNotice(request, USER_ID);
        List<Long> originalIds = attachmentMapper.selectListByNoticeId(id).stream()
                .map(NoticeAttachmentDO::getId).toList();
        request.setId(id);
        request.setContent("<p>修改正文</p>");
        request.setAttachments(List.of(attachment(102L), attachment(103L)));

        assertServiceException(() -> noticeService.updateNotice(request, USER_ID), NOTICE_ATTACHMENT_INVALID);

        assertEquals("<p>原正文</p>", noticeMapper.selectById(id).getContent());
        assertEquals(originalIds, attachmentMapper.selectListByNoticeId(id).stream()
                .map(NoticeAttachmentDO::getId).toList());
        assertEquals(2, attachmentMapper.selectListIncludingDeletedByNoticeId(id).size());
        request.setAttachments(List.of(attachment(101L), attachment(101L)));
        assertServiceException(() -> noticeService.updateNotice(request, USER_ID), NOTICE_ATTACHMENT_INVALID);
        assertEquals(originalIds, attachmentMapper.selectListByNoticeId(id).stream()
                .map(NoticeAttachmentDO::getId).toList());
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

        noticeService.publishNotice(notice.getId(), 900L);
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

    @Test
    void shouldFreezeTargetUsersAndRejectNonRecipients() {
        when(permissionService.getEnabledUserIdsByPermission("system:notice:read")).thenReturn(Set.of(USER_ID));
        when(userService.getUserList(Set.of(USER_ID))).thenReturn(List.of(user(USER_ID, null)));
        NoticeSaveReqVO request = saveRequest("<p>定向正文</p>");
        request.setAudienceType("TARGET");
        request.setTargetUserIds(List.of(USER_ID, USER_ID));

        Long id = noticeService.createNotice(request, USER_ID);
        noticeService.publishNotice(id, 900L);

        assertEquals(1, recipientMapper.selectListByNoticeId(id).size());
        assertEquals(id, noticeService.getMyNotice(id, USER_ID).getId());
        assertServiceException(() -> noticeService.getMyNotice(id, 8L), NOTICE_RECIPIENT_INVALID);
        assertServiceException(() -> noticeService.markRead(id, 8L), NOTICE_RECIPIENT_INVALID);
    }

    @Test
    void shouldClearHiddenTargetsWhenSavingAllAudience() {
        NoticeSaveReqVO request = saveRequest("<p>全员正文</p>");
        request.setAudienceType("ALL");
        request.setTargetDeptIds(List.of(999L));
        request.setTargetUserIds(List.of(999L));

        NoticeDO stored = noticeMapper.selectById(noticeService.createNotice(request, USER_ID));

        assertEquals("[]", stored.getTargetDeptIds());
        assertEquals("[]", stored.getTargetUserIds());
    }

    @Test
    void shouldExpandDepartmentAndDeduplicateExplicitUsersAtPublish() {
        DeptDO child = new DeptDO();
        child.setId(20L);
        AdminUserDO departmentUser = user(30L, 20L);
        AdminUserDO explicitUser = user(USER_ID, null);
        when(permissionService.getEnabledUserIdsByPermission("system:notice:read"))
                .thenReturn(Set.of(USER_ID, 30L));
        when(userService.getUserListByDeptIds(Set.of(10L, 20L)))
                .thenReturn(List.of(departmentUser, explicitUser));
        when(deptService.getChildDeptList(Set.of(10L))).thenReturn(List.of(child));
        when(userService.getUserList(Set.of(USER_ID, 30L))).thenReturn(List.of(departmentUser, explicitUser));

        NoticeSaveReqVO request = saveRequest("<p>部门公告</p>");
        request.setAudienceType("TARGET");
        request.setTargetDeptIds(List.of(10L));
        request.setTargetUserIds(List.of(USER_ID));
        Long id = noticeService.createNotice(request, USER_ID);
        noticeService.publishNotice(id, 900L);

        assertEquals(Set.of(USER_ID, 30L), recipientMapper.selectListByNoticeId(id).stream()
                .map(row -> row.getUserId()).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void shouldRejectExplicitUserWithoutReadPermission() {
        when(permissionService.getEnabledUserIdsByPermission("system:notice:read")).thenReturn(Set.of());
        NoticeSaveReqVO request = saveRequest("<p>无权限</p>");
        request.setAudienceType("TARGET");
        request.setTargetUserIds(List.of(USER_ID));

        assertServiceException(() -> noticeService.createNotice(request, USER_ID), NOTICE_RECIPIENT_INVALID);
    }

    @Test
    void shouldFreezeSelectedSourceActualPublisherAndAudience() {
        AdminUserDO author = user(USER_ID, 10L); author.setNickname("撰稿人");
        DeptDO source = new DeptDO(); source.setId(10L); source.setName("考务部");
        DeptDO audience = new DeptDO(); audience.setId(20L); audience.setName("综合行政部");
        when(userService.getUser(USER_ID)).thenReturn(author);
        when(deptService.getDept(10L)).thenReturn(source);
        when(deptService.getDeptMap(List.of(20L))).thenReturn(java.util.Map.of(20L, audience));
        when(permissionService.getEnabledUserIdsByPermission("system:notice:read")).thenReturn(Set.of(USER_ID));
        when(userService.getUserList(Set.of(USER_ID))).thenReturn(List.of(author));
        NoticeSaveReqVO request = saveRequest("<p>来源快照</p>");
        request.setAudienceType("TARGET"); request.setTargetDeptIds(List.of(20L)); request.setTargetUserIds(List.of(USER_ID));
        Long id = noticeService.createNotice(request, USER_ID);
        assertEquals(10L, noticeService.getNotice(id).getSourceDeptId());
        assertNull(noticeService.getNotice(id).getPublisherName());
        noticeService.publishNotice(id, 900L);
        source.setName("更名后的部门");
        audience.setName("更名后的接收部门");
        var detail = noticeService.getMyNotice(id, USER_ID);
        assertEquals("考务部", detail.getSourceDeptName());
        assertEquals("测试发布人", detail.getPublisherName());
        assertEquals(900L, detail.getPublisherId());
        assertEquals("综合行政部（含子部门）、指定用户 1 人", detail.getAudienceSummary());
        var copy = noticeService.getNotice(noticeService.copyNotice(id));
        assertEquals(10L, copy.getSourceDeptId());
        assertNull(copy.getPublisherName()); assertNull(copy.getPublisherId());
    }

    @Test
    void shouldAllowSelectedSourceAndRetainItForOlderUpdateClients() {
        DeptDO source = new DeptDO(); source.setId(20L); source.setName("综合行政部");
        when(deptService.getDept(20L)).thenReturn(source);
        NoticeSaveReqVO request = saveRequest("<p>代部门发布</p>"); request.setSourceDeptId(20L);
        Long id = noticeService.createNotice(request, 900L);
        request.setId(id); request.setSourceDeptId(null);
        noticeService.updateNotice(request, 900L);
        noticeService.publishNotice(id, 900L);
        assertEquals("综合行政部", noticeService.getNotice(id).getSourceDeptName());
        assertEquals("全体员工", noticeService.getNotice(id).getAudienceSummary());
    }

    @Test
    void shouldRejectInvalidSourceAndLeaveLegacyMetadataUnknown() {
        NoticeSaveReqVO request = saveRequest("<p>无效来源</p>"); request.setSourceDeptId(404L);
        org.mockito.Mockito.doThrow(cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(DEPT_NOT_FOUND))
                .when(deptService).validateDeptList(List.of(404L));
        assertServiceException(() -> noticeService.createNotice(request, USER_ID), DEPT_NOT_FOUND);
        NoticeDO legacy = insertNotice(NoticePublishStatusEnum.PUBLISHED, LocalDateTime.now());
        assertNull(noticeService.getMyNotice(legacy.getId(), USER_ID).getSourceDeptName());
        assertNull(noticeService.getMyNotice(legacy.getId(), USER_ID).getPublisherName());
    }

    private AdminUserDO user(Long id, Long deptId) {
        AdminUserDO user = new AdminUserDO();
        user.setId(id);
        user.setDeptId(deptId);
        user.setStatus(0);
        return user;
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
