package cn.iocoder.yudao.module.infra.service.file;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import cn.iocoder.yudao.module.infra.api.file.dto.FileDirectUploadCompleteReqDTO;
import cn.iocoder.yudao.module.infra.api.file.dto.FileDirectUploadInitReqDTO;
import cn.iocoder.yudao.module.infra.api.file.dto.FileDirectUploadInitRespDTO;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;
import cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper;
import cn.iocoder.yudao.module.infra.dal.redis.file.FileDirectUploadRedisDAO;
import cn.iocoder.yudao.module.infra.dal.redis.file.FileDirectUploadSession;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileObjectMetadata;
import cn.iocoder.yudao.module.infra.framework.file.core.client.FileUploadPresignResult;
import cn.iocoder.yudao.module.infra.framework.file.core.utils.FilePathUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.infra.enums.ErrorCodeConstants.*;

@Service
@Slf4j
public class FileDirectUploadService {

    private static final int UPLOAD_EXPIRES_SECONDS = 15 * 60;
    private static final long MAX_SINGLE_FILE_BYTES = 5L * 1024 * 1024 * 1024;
    private static final String STAGING_DIRECTORY = ".direct-upload";
    private static final String LOCK_PREFIX = "infra:file:direct-upload:lock:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Resource private FileConfigService fileConfigService;
    @Resource private FileMapper fileMapper;
    @Resource private FileDirectUploadRedisDAO redisDAO;
    @Resource private RedissonClient redissonClient;

    public FileDirectUploadInitRespDTO init(FileDirectUploadInitReqDTO request) {
        validateInitRequest(request);
        FileClient client = fileConfigService.getMasterFileClient();
        if (client == null) {
            throw exception(FILE_DIRECT_UPLOAD_UNSUPPORTED);
        }
        String token = generateToken();
        String digest = DigestUtil.sha256Hex(token);
        String name = FilePathUtils.validateFileName(request.getName().trim());
        String finalPath = request.getDirectory() + "/" + IdUtil.fastSimpleUUID() + "/" + name;
        String stagingPath = STAGING_DIRECTORY + "/" + digest + "/" + name;
        FilePathUtils.validatePath(finalPath);
        FilePathUtils.validatePath(stagingPath);

        FileUploadPresignResult presign;
        try {
            presign = client.presignPutUrl(stagingPath, request.getContentType(), request.getSize(),
                    UPLOAD_EXPIRES_SECONDS);
        } catch (UnsupportedOperationException error) {
            throw exception(FILE_DIRECT_UPLOAD_UNSUPPORTED);
        }
        long expiresAtMillis = System.currentTimeMillis() + UPLOAD_EXPIRES_SECONDS * 1000L;
        FileDirectUploadSession session = new FileDirectUploadSession();
        session.setTenantId(request.getTenantId());
        session.setUserType(request.getUserType());
        session.setUserId(request.getUserId());
        session.setScene(request.getScene());
        session.setConfigId(client.getId());
        session.setStagingPath(stagingPath);
        session.setFinalPath(finalPath);
        session.setName(name);
        session.setContentType(request.getContentType());
        session.setSize(request.getSize());
        session.setExpiresAtMillis(expiresAtMillis);
        session.setStatus(FileDirectUploadSession.STATUS_PENDING);
        redisDAO.set(digest, session);

        FileDirectUploadInitRespDTO response = new FileDirectUploadInitRespDTO();
        response.setUploadToken(token);
        response.setUploadUrl(presign.uploadUrl());
        response.setUploadHeaders(presign.headers());
        response.setExpiresAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(expiresAtMillis), ZoneId.systemDefault()));
        return response;
    }

    public FileInfoRespDTO complete(FileDirectUploadCompleteReqDTO request) {
        String digest = DigestUtil.sha256Hex(request.getUploadToken());
        RLock lock = redissonClient.getLock(LOCK_PREFIX + digest);
        lock.lock();
        try {
            FileDirectUploadSession session = redisDAO.get(digest);
            validateSession(request, session);
            if (FileDirectUploadSession.STATUS_COMPLETED.equals(session.getStatus())) {
                return toResponse(requireCompletedFile(session));
            }
            if (session.getExpiresAtMillis() < System.currentTimeMillis()) {
                throw exception(FILE_DIRECT_UPLOAD_EXPIRED);
            }
            FileDO existing = fileMapper.selectLatestByConfigIdAndPath(session.getConfigId(), session.getFinalPath());
            if (existing != null) {
                validateExisting(existing, session);
                markCompleted(digest, session, existing.getId());
                deleteStagingQuietly(session);
                return toResponse(existing);
            }

            FileClient client = fileConfigService.getFileClient(session.getConfigId());
            if (client == null) {
                throw exception(FILE_DIRECT_UPLOAD_UNSUPPORTED);
            }
            FileObjectMetadata staging = getMetadata(client, session.getStagingPath());
            validateMetadata(staging, session);
            try {
                client.copyObject(session.getStagingPath(), session.getFinalPath(), staging.etag());
            } catch (UnsupportedOperationException error) {
                throw exception(FILE_DIRECT_UPLOAD_UNSUPPORTED);
            }
            FileObjectMetadata stored = getMetadata(client, session.getFinalPath());
            validateMetadata(stored, session);

            String url = HttpUtils.removeUrlQuery(client.presignGetUrl(session.getFinalPath(), null));
            FileDO file = new FileDO().setConfigId(session.getConfigId())
                    .setName(session.getName()).setPath(session.getFinalPath()).setUrl(url)
                    .setType(stored.contentType()).setSize(stored.size());
            file.setCreator(String.valueOf(session.getUserId()));
            fileMapper.insert(file);
            markCompleted(digest, session, file.getId());
            deleteStagingQuietly(session);
            return toResponse(file);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void validateInitRequest(FileDirectUploadInitReqDTO request) {
        if (request.getSize() == null || request.getSize() <= 0 || request.getSize() > MAX_SINGLE_FILE_BYTES
                || request.getName() == null || request.getName().isBlank() || request.getName().length() > 255
                || request.getDirectory() == null || request.getDirectory().isBlank()
                || request.getScene() == null || request.getScene().isBlank()
                || request.getContentType() == null || request.getContentType().isBlank()
                || request.getContentType().length() > 100 || request.getContentType().contains("\r")
                || request.getContentType().contains("\n")) {
            throw exception(FILE_DIRECT_UPLOAD_INVALID);
        }
        FilePathUtils.validateDirectory(request.getDirectory());
    }

    private void validateSession(FileDirectUploadCompleteReqDTO request, FileDirectUploadSession session) {
        if (session == null) {
            throw exception(FILE_DIRECT_UPLOAD_TOKEN_INVALID);
        }
        if (!Objects.equals(session.getTenantId(), request.getTenantId())
                || !Objects.equals(session.getUserType(), request.getUserType())
                || !Objects.equals(session.getUserId(), request.getUserId())
                || !Objects.equals(session.getScene(), request.getScene())) {
            throw exception(FILE_DIRECT_UPLOAD_OWNER_MISMATCH);
        }
    }

    private FileDO requireCompletedFile(FileDirectUploadSession session) {
        FileDO file = session.getFileId() == null ? null : fileMapper.selectById(session.getFileId());
        if (file == null) {
            throw exception(FILE_DIRECT_UPLOAD_STATE_INVALID);
        }
        validateExisting(file, session);
        return file;
    }

    private FileObjectMetadata getMetadata(FileClient client, String path) {
        try {
            return client.getObjectMetadata(path);
        } catch (UnsupportedOperationException error) {
            throw exception(FILE_DIRECT_UPLOAD_UNSUPPORTED);
        } catch (RuntimeException error) {
            throw exception(FILE_DIRECT_UPLOAD_OBJECT_MISSING);
        }
    }

    private void validateMetadata(FileObjectMetadata metadata, FileDirectUploadSession session) {
        if (metadata == null || metadata.size() != session.getSize()
                || !Objects.equals(normalizeContentType(metadata.contentType()),
                normalizeContentType(session.getContentType()))) {
            throw exception(FILE_DIRECT_UPLOAD_METADATA_MISMATCH);
        }
    }

    private void validateExisting(FileDO file, FileDirectUploadSession session) {
        if (!Objects.equals(file.getConfigId(), session.getConfigId())
                || !Objects.equals(file.getPath(), session.getFinalPath())
                || !Objects.equals(file.getName(), session.getName())
                || !Objects.equals(file.getSize(), session.getSize())
                || !Objects.equals(normalizeContentType(file.getType()), normalizeContentType(session.getContentType()))
                || !Objects.equals(file.getCreator(), String.valueOf(session.getUserId()))) {
            throw exception(FILE_DIRECT_UPLOAD_STATE_INVALID);
        }
    }

    private String normalizeContentType(String value) {
        return StrUtil.blankToDefault(value, "").trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void markCompleted(String digest, FileDirectUploadSession session, Long fileId) {
        session.setStatus(FileDirectUploadSession.STATUS_COMPLETED);
        session.setFileId(fileId);
        redisDAO.set(digest, session);
    }

    private void deleteStagingQuietly(FileDirectUploadSession session) {
        try {
            FileClient client = fileConfigService.getFileClient(session.getConfigId());
            if (client != null) {
                client.delete(session.getStagingPath());
            }
        } catch (Exception error) {
            log.warn("[deleteStagingQuietly][path({}) 删除直传临时对象失败]", session.getStagingPath(), error);
        }
    }

    private FileInfoRespDTO toResponse(FileDO file) {
        return new FileInfoRespDTO(file.getId(), file.getConfigId(), file.getName(), file.getPath(), file.getUrl(),
                file.getType(), file.getSize(), file.getCreator());
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
