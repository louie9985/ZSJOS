package cn.iocoder.yudao.module.zsjos.service.content;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentVersionSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentVersionFileDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentVersionFileMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentVersionMapper;
import cn.iocoder.yudao.module.zsjos.service.file.BusinessFileDirectUploadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.CONTENT_ACCEPTANCE;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.CONTENT_REVISING;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.CONTENT_VERSION_FILE_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.CONTENT_VERSION_IDEMPOTENCY_CONFLICT;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.CONTENT_VERSION_IDEMPOTENCY_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentVersionServiceTest {

    private static final long TENANT_ID = 1L;
    private static final long CONTENT_ID = 10L;
    private static final long USER_ID = 20L;

    @InjectMocks private ContentVersionService service;
    @Mock private ContentVersionMapper mapper;
    @Mock private ContentVersionFileMapper fileMapper;
    @Mock private ContentMapper contentMapper;
    @Mock private ContentService contentService;
    @Mock private FileApi fileApi;
    @Mock private BusinessFileDirectUploadService directUploadService;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void replaysSamePayloadBeforeStageValidation() {
        ContentDO content = content(CONTENT_ACCEPTANCE, 1, 4);
        ContentVersionDO replay = version(101L).setTitleSnapshot("标题").setTopicSnapshot("选题")
                .setScriptText("正文").setDeliverableUrl("https://example.com/video")
                .setLeadResourceUrl("https://example.com/lead")
                .setPlannedPublishAt(LocalDateTime.of(2026, 9, 9, 9, 0));
        when(contentMapper.selectByIdForUpdate(CONTENT_ID, TENANT_ID)).thenReturn(content);
        when(mapper.selectByContentAndIdempotencyKey(CONTENT_ID, "retry-key")).thenReturn(replay);
        when(fileMapper.selectByVersionId(101L)).thenReturn(List.of(
                file(1L, 101L, "cover", 1, 11L, 30L),
                file(2L, 101L, "deliverable", 1, 12L, 31L)));

        ContentVersionSaveReqVO request = request("retry-key");
        request.setCoverSnapshotJson("[{\"fileId\":11}]");
        request.setDeliverableSnapshotJson("[12]");

        assertEquals(101L, service.create(request, USER_ID));
        verify(mapper, never()).insert(any(ContentVersionDO.class));
        verify(contentService, never()).advanceCurrentVersion(any(), any(), any());
    }

    @Test
    void rejectsDifferentPayloadForSameKey() {
        ContentDO content = content(CONTENT_ACCEPTANCE, 1, 4);
        ContentVersionDO replay = version(101L).setTitleSnapshot("标题").setTopicSnapshot("选题")
                .setScriptText("原正文").setDeliverableUrl("https://example.com/video")
                .setLeadResourceUrl("https://example.com/lead")
                .setPlannedPublishAt(LocalDateTime.of(2026, 9, 9, 9, 0));
        when(contentMapper.selectByIdForUpdate(CONTENT_ID, TENANT_ID)).thenReturn(content);
        when(mapper.selectByContentAndIdempotencyKey(CONTENT_ID, "retry-key")).thenReturn(replay);

        ContentVersionSaveReqVO request = request("retry-key");
        request.setScriptText("修改后的正文");

        assertServiceCode(CONTENT_VERSION_IDEMPOTENCY_CONFLICT, () -> service.create(request, USER_ID));
        verifyNoInteractions(fileMapper, fileApi, contentService);
    }

    @Test
    void treatsBlankKeyAsAbsentAndRejectsOverlongKey() {
        mockInitialContent();
        ContentVersionSaveReqVO blank = request("   ");

        service.create(blank, USER_ID);

        ArgumentCaptor<ContentVersionDO> versionCaptor = ArgumentCaptor.forClass(ContentVersionDO.class);
        verify(mapper).insert(versionCaptor.capture());
        assertNull(versionCaptor.getValue().getIdempotencyKey());
        verify(mapper, never()).selectByContentAndIdempotencyKey(any(), any());

        ContentVersionSaveReqVO overlong = request("x".repeat(129));
        assertServiceCode(CONTENT_VERSION_IDEMPOTENCY_INVALID, () -> service.create(overlong, USER_ID));
    }

    @Test
    void inheritsFilesAndMaterialReferencesWithoutChangingUploader() {
        ContentDO content = content(CONTENT_REVISING, 1, 3);
        ContentVersionDO current = version(100L).setMaterialRefsJson("[{\"materialVersionId\":9}]")
                .setFrozenAt(LocalDateTime.of(2026, 9, 8, 9, 0)).setReviewDecision("rejected");
        List<ContentVersionFileDO> currentFiles = List.of(
                file(1L, 100L, "cover", 1, 11L, 30L),
                file(2L, 100L, "deliverable", 1, 12L, 31L));
        when(contentMapper.selectByIdForUpdate(CONTENT_ID, TENANT_ID)).thenReturn(content);
        when(mapper.selectByContentAndVersionNoForUpdate(CONTENT_ID, 1, TENANT_ID)).thenReturn(current);
        when(fileMapper.selectByVersionId(100L)).thenReturn(currentFiles);
        doAnswer(invocation -> {
            invocation.<ContentVersionDO>getArgument(0).setId(102L);
            return 1;
        }).when(mapper).insert(any(ContentVersionDO.class));
        when(contentService.advanceCurrentVersion(CONTENT_ID, 3, 2)).thenReturn(1);

        ContentVersionSaveReqVO request = request(null);
        request.setMaterialRefsJson("[{\"materialVersionId\":999}]");
        request.setCoverSnapshotJson("[11]");
        request.setDeliverableSnapshotJson("[{\"id\":12}]");

        assertEquals(102L, service.create(request, USER_ID));

        ArgumentCaptor<ContentVersionDO> versionCaptor = ArgumentCaptor.forClass(ContentVersionDO.class);
        verify(mapper).insert(versionCaptor.capture());
        assertEquals("[{\"materialVersionId\":9}]", versionCaptor.getValue().getMaterialRefsJson());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ContentVersionFileDO>> filesCaptor = ArgumentCaptor.forClass(List.class);
        verify(fileMapper, times(2)).insertBatch(filesCaptor.capture());
        assertEquals(List.of(30L, 31L), filesCaptor.getAllValues().stream().flatMap(List::stream)
                .map(ContentVersionFileDO::getUploadedByUserId).toList());
        verifyNoInteractions(fileApi);
    }

    @Test
    void rejectsReusingAnInheritedFileUnderAnotherField() {
        ContentDO content = content(CONTENT_REVISING, 1, 3);
        ContentVersionDO current = version(100L).setReviewDecision("rejected");
        when(contentMapper.selectByIdForUpdate(CONTENT_ID, TENANT_ID)).thenReturn(content);
        when(mapper.selectByContentAndVersionNoForUpdate(CONTENT_ID, 1, TENANT_ID)).thenReturn(current);
        when(fileMapper.selectByVersionId(100L)).thenReturn(
                List.of(file(1L, 100L, "deliverable", 1, 11L, 30L)));
        ContentVersionSaveReqVO request = request(null);
        request.setCoverSnapshotJson("[11]");

        assertServiceCode(CONTENT_VERSION_FILE_INVALID, () -> service.create(request, USER_ID));
        verify(mapper, never()).insert(any(ContentVersionDO.class));
        verifyNoInteractions(fileApi);
    }

    @Test
    void persistsClientMaterialReferencesOnFirstVersion() {
        mockInitialContent();
        ContentVersionSaveReqVO request = request(null);
        request.setMaterialRefsJson("[{\"materialVersionId\":999}]");

        service.create(request, USER_ID);

        ArgumentCaptor<ContentVersionDO> captor = ArgumentCaptor.forClass(ContentVersionDO.class);
        verify(mapper).insert(captor.capture());
        assertEquals("[{\"materialVersionId\":999}]", captor.getValue().getMaterialRefsJson());
    }

    @Test
    void rejectsInvalidMaterialReferencesJsonOnFirstVersion() {
        when(contentMapper.selectByIdForUpdate(CONTENT_ID, TENANT_ID)).thenReturn(content(CONTENT_REVISING, 0, 3));
        when(mapper.selectByContentAndVersionNoForUpdate(CONTENT_ID, 0, TENANT_ID)).thenReturn(null);
        ContentVersionSaveReqVO request = request(null);
        request.setMaterialRefsJson("not-json");

        assertServiceCode(CONTENT_VERSION_FILE_INVALID, () -> service.create(request, USER_ID));
        verify(mapper, never()).insert(any(ContentVersionDO.class));
    }

    private void mockInitialContent() {
        ContentDO content = content(CONTENT_REVISING, 0, 3);
        when(contentMapper.selectByIdForUpdate(CONTENT_ID, TENANT_ID)).thenReturn(content);
        when(mapper.selectByContentAndVersionNoForUpdate(CONTENT_ID, 0, TENANT_ID)).thenReturn(null);
        doAnswer(invocation -> {
            invocation.<ContentVersionDO>getArgument(0).setId(102L);
            return 1;
        }).when(mapper).insert(any(ContentVersionDO.class));
        when(contentService.advanceCurrentVersion(CONTENT_ID, 3, 1)).thenReturn(1);
    }

    private ContentDO content(String status, int currentVersionNo, int version) {
        return new ContentDO().setId(CONTENT_ID).setTitle("标题").setTopic("选题")
                .setStatus(status).setCurrentVersionNo(currentVersionNo).setVersion(version);
    }

    private ContentVersionDO version(Long id) {
        return new ContentVersionDO().setId(id).setContentId(CONTENT_ID).setVersionNo(1);
    }

    private ContentVersionFileDO file(Long id, Long versionId, String fieldKey, int sortNo,
                                      Long infraFileId, Long uploader) {
        return new ContentVersionFileDO().setId(id).setContentVersionId(versionId).setFieldKey(fieldKey)
                .setSortNo(sortNo).setInfraFileId(infraFileId).setFileUrlSnapshot("https://example.com/" + infraFileId)
                .setOriginalName(infraFileId + ".png").setContentType("image/png").setFileSize(1024L)
                .setUploadedByUserId(uploader);
    }

    private ContentVersionSaveReqVO request(String idempotencyKey) {
        ContentVersionSaveReqVO request = new ContentVersionSaveReqVO();
        request.setContentId(CONTENT_ID);
        request.setTitleSnapshot("标题");
        request.setTopicSnapshot("选题");
        request.setDeliverableUrl("https://example.com/video");
        request.setScriptText("正文");
        request.setLeadResourceUrl("https://example.com/lead");
        request.setPlannedPublishAt(LocalDateTime.of(2026, 9, 9, 9, 0));
        request.setIdempotencyKey(idempotencyKey);
        return request;
    }

    private void assertServiceCode(cn.iocoder.yudao.framework.common.exception.ErrorCode expected,
                                   org.junit.jupiter.api.function.Executable executable) {
        ServiceException error = assertThrows(ServiceException.class, executable);
        assertEquals(expected.getCode(), error.getCode());
    }
}
