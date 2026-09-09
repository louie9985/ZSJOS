package cn.iocoder.yudao.module.infra.service.file;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.exception.ErrorCode;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.infra.api.file.dto.FileDirectUploadCompleteReqDTO;
import cn.iocoder.yudao.module.infra.api.file.dto.FileDirectUploadInitReqDTO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.dal.redis.file.FileDirectUploadRedisDAO;
import cn.iocoder.yudao.module.infra.dal.redis.file.FileDirectUploadSession;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileObjectMetadata;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileUploadPresignResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.Map;

import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_DIRECT_UPLOAD_METADATA_MISMATCH;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.FILE_DIRECT_UPLOAD_OWNER_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileDirectUploadServiceTest {

    @InjectMocks private FileDirectUploadService service;
    @Mock private FileConfigService fileConfigService;
    @Mock private FileMapper fileMapper;
    @Mock private FileDirectUploadRedisDAO redisDAO;
    @Mock private RedissonClient redissonClient;
    @Mock private RLock lock;
    @Mock private FileClient client;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(redissonClient.getLock(anyString())).thenReturn(lock);
        org.mockito.Mockito.lenient().when(lock.isHeldByCurrentThread()).thenReturn(true);
    }

    @Test
    void initBindsServerOwnedCoordinatesWithoutReturningThem() {
        when(fileConfigService.getMasterFileClient()).thenReturn(client);
        when(client.getId()).thenReturn(8L);
        when(client.presignPutUrl(anyString(), eq("video/mp4"), eq(1024L), eq(900)))
                .thenReturn(new FileUploadPresignResult("https://upload.test/signed",
                        Map.of("Content-Type", "video/mp4")));

        var response = service.init(initRequest());

        assertNotNull(response.getUploadToken());
        assertEquals("https://upload.test/signed", response.getUploadUrl());
        ArgumentCaptor<String> digest = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<FileDirectUploadSession> session = ArgumentCaptor.forClass(FileDirectUploadSession.class);
        verify(redisDAO).set(digest.capture(), session.capture());
        assertEquals(DigestUtil.sha256Hex(response.getUploadToken()), digest.getValue());
        assertEquals(1L, session.getValue().getTenantId());
        assertEquals(42L, session.getValue().getUserId());
        assertEquals("ZSJOS_CONTENT", session.getValue().getScene());
        assertEquals(8L, session.getValue().getConfigId());
        assertEquals("video.mp4", session.getValue().getName());
        assertTrue(session.getValue().getFinalPath().matches("zsjos/content/42/[0-9a-f]+/video\\.mp4"));
        assertTrue(session.getValue().getStagingPath().startsWith(".direct-upload/" + digest.getValue()));
    }

    @Test
    void completeUsesHeadMetadataAndIsIdempotent() throws Exception {
        String token = "direct-upload-token";
        FileDirectUploadSession session = pendingSession();
        when(redisDAO.get(DigestUtil.sha256Hex(token))).thenReturn(session);
        when(fileConfigService.getFileClient(8L)).thenReturn(client);
        when(client.getObjectMetadata(session.getStagingPath()))
                .thenReturn(new FileObjectMetadata(1024L, "video/mp4", "etag-1"));
        when(client.getObjectMetadata(session.getFinalPath()))
                .thenReturn(new FileObjectMetadata(1024L, "video/mp4", "etag-2"));
        when(client.presignGetUrl(session.getFinalPath(), null))
                .thenReturn("https://files.test/video.mp4?signature=secret");
        doAnswer(invocation -> {
            ((FileDO) invocation.getArgument(0)).setId(99L);
            return 1;
        }).when(fileMapper).insert(any(FileDO.class));

        var response = service.complete(completeRequest(token));

        assertEquals(99L, response.getId());
        assertEquals("https://files.test/video.mp4", response.getUrl());
        assertEquals("42", response.getCreator());
        verify(client).copyObject(session.getStagingPath(), session.getFinalPath(), "etag-1");
        verify(client).delete(session.getStagingPath());
        ArgumentCaptor<FileDirectUploadSession> saved = ArgumentCaptor.forClass(FileDirectUploadSession.class);
        verify(redisDAO).set(eq(DigestUtil.sha256Hex(token)), saved.capture());
        assertEquals(FileDirectUploadSession.STATUS_COMPLETED, saved.getValue().getStatus());
        assertEquals(99L, saved.getValue().getFileId());
    }

    @Test
    void completeRejectsDifferentTenantBeforeReadingStorage() {
        String token = "direct-upload-token";
        when(redisDAO.get(DigestUtil.sha256Hex(token))).thenReturn(pendingSession());
        FileDirectUploadCompleteReqDTO request = completeRequest(token);
        request.setTenantId(2L);

        assertServiceCode(FILE_DIRECT_UPLOAD_OWNER_MISMATCH, () -> service.complete(request));

        verify(fileConfigService, never()).getFileClient(any());
        verify(fileMapper, never()).insert(any(FileDO.class));
    }

    @Test
    void completeRejectsMetadataMismatchWithoutCopyOrInsert() {
        String token = "direct-upload-token";
        FileDirectUploadSession session = pendingSession();
        when(redisDAO.get(DigestUtil.sha256Hex(token))).thenReturn(session);
        when(fileConfigService.getFileClient(8L)).thenReturn(client);
        when(client.getObjectMetadata(session.getStagingPath()))
                .thenReturn(new FileObjectMetadata(2048L, "video/mp4", "etag"));

        assertServiceCode(FILE_DIRECT_UPLOAD_METADATA_MISMATCH,
                () -> service.complete(completeRequest(token)));

        verify(client, never()).copyObject(anyString(), anyString(), anyString());
        verify(fileMapper, never()).insert(any(FileDO.class));
    }

    @Test
    void completeReplaysPreviouslyCompletedFile() {
        String token = "direct-upload-token";
        FileDirectUploadSession session = pendingSession();
        session.setStatus(FileDirectUploadSession.STATUS_COMPLETED);
        session.setFileId(99L);
        FileDO file = completedFile(session).setId(99L);
        when(redisDAO.get(DigestUtil.sha256Hex(token))).thenReturn(session);
        when(fileMapper.selectById(99L)).thenReturn(file);

        var response = service.complete(completeRequest(token));

        assertEquals(99L, response.getId());
        verify(fileConfigService, never()).getFileClient(any());
        verify(fileMapper, never()).insert(any(FileDO.class));
    }

    @Test
    void completeRecoversWhenDatabaseInsertFinishedBeforeRedisUpdate() throws Exception {
        String token = "direct-upload-token";
        FileDirectUploadSession session = pendingSession();
        FileDO existing = completedFile(session).setId(99L);
        when(redisDAO.get(DigestUtil.sha256Hex(token))).thenReturn(session);
        when(fileMapper.selectLatestByConfigIdAndPath(8L, session.getFinalPath())).thenReturn(existing);
        when(fileConfigService.getFileClient(8L)).thenReturn(client);

        var response = service.complete(completeRequest(token));

        assertEquals(99L, response.getId());
        verify(fileMapper, never()).insert(any(FileDO.class));
        verify(client, never()).copyObject(anyString(), anyString(), anyString());
        verify(client).delete(session.getStagingPath());
    }

    private FileDirectUploadInitReqDTO initRequest() {
        FileDirectUploadInitReqDTO request = new FileDirectUploadInitReqDTO();
        request.setTenantId(1L);
        request.setUserType(2);
        request.setUserId(42L);
        request.setScene("ZSJOS_CONTENT");
        request.setDirectory("zsjos/content/42");
        request.setName("video.mp4");
        request.setContentType("video/mp4");
        request.setSize(1024L);
        return request;
    }

    private FileDirectUploadCompleteReqDTO completeRequest(String token) {
        FileDirectUploadCompleteReqDTO request = new FileDirectUploadCompleteReqDTO();
        request.setUploadToken(token);
        request.setTenantId(1L);
        request.setUserType(2);
        request.setUserId(42L);
        request.setScene("ZSJOS_CONTENT");
        return request;
    }

    private FileDirectUploadSession pendingSession() {
        FileDirectUploadSession session = new FileDirectUploadSession();
        session.setTenantId(1L);
        session.setUserType(2);
        session.setUserId(42L);
        session.setScene("ZSJOS_CONTENT");
        session.setConfigId(8L);
        session.setStagingPath(".direct-upload/digest/video.mp4");
        session.setFinalPath("zsjos/content/42/uuid/video.mp4");
        session.setName("video.mp4");
        session.setContentType("video/mp4");
        session.setSize(1024L);
        session.setExpiresAtMillis(System.currentTimeMillis() + 60_000L);
        session.setStatus(FileDirectUploadSession.STATUS_PENDING);
        return session;
    }

    private FileDO completedFile(FileDirectUploadSession session) {
        FileDO file = new FileDO().setConfigId(session.getConfigId()).setName(session.getName())
                .setPath(session.getFinalPath()).setUrl("https://files.test/video.mp4")
                .setType(session.getContentType()).setSize(session.getSize());
        file.setCreator(String.valueOf(session.getUserId()));
        return file;
    }

    private void assertServiceCode(ErrorCode errorCode, org.junit.jupiter.api.function.Executable executable) {
        ServiceException error = assertThrows(ServiceException.class, executable);
        assertEquals(errorCode.getCode(), error.getCode());
    }
}
