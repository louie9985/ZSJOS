package cn.iocoder.yudao.module.zsjos.service.content;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentUploadRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentVersionFileRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentVersionRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.content.vo.ContentVersionSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.file.vo.ZsjosDirectUploadInitReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.file.vo.ZsjosDirectUploadInitRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentVersionFileDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentVersionFileMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentVersionMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.file.BusinessFileDirectUploadService;
import cn.iocoder.yudao.module.zsjos.service.file.ContentAttachmentTypes;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class ContentVersionService {
    private static final int MAX_CONTENT_FILES = 20;
    private static final long MAX_CONTENT_FILE_BYTES = 1024L * 1024 * 1024;
    private static final int FILE_PREVIEW_SECONDS = 3600;

    @Resource private ContentVersionMapper mapper;
    @Resource private ContentVersionFileMapper contentVersionFileMapper;
    @Resource private ContentMapper contentMapper;
    @Resource private ContentService contentService;
    @Resource private FileApi fileApi;
    @Resource private BusinessFileDirectUploadService directUploadService;

    public List<ContentVersionRespVO> list(Long contentId, Long userId) {
        contentService.get(contentId, userId);
        return mapper.selectByContentId(contentId).stream()
                .map(this::toResponse).toList();
    }

    @ZsjosPermission(bizType = "content", bizId = "#req.contentId", action = "version-create")
    @Transactional(rollbackFor = Exception.class)
    public Long create(ContentVersionSaveReqVO req, Long userId) {
        String idempotencyKey = normalizeIdempotencyKey(req.getIdempotencyKey());
        ContentDO content = contentMapper.selectByIdForUpdate(req.getContentId(),
                TenantContextHolder.getRequiredTenantId());
        if (content == null) throw exception(CONTENT_NOT_EXISTS);
        ContentVersionDO replay = idempotencyKey == null ? null
                : mapper.selectByContentAndIdempotencyKey(req.getContentId(), idempotencyKey);
        if (replay != null) {
            if (!sameReplayPayload(req, content, replay)) {
                throw exception(CONTENT_VERSION_IDEMPOTENCY_CONFLICT);
            }
            return replay.getId();
        }
        if (!List.of(CONTENT_TOPIC, CONTENT_SCRIPT, CONTENT_IN_PRODUCTION, CONTENT_REVISING)
                .contains(content.getStatus())) {
            throw exception(CONTENT_VERSION_STAGE_INVALID);
        }
        ContentVersionDO current = content.getCurrentVersionNo() == null ? null
                : mapper.selectByContentAndVersionNoForUpdate(content.getId(), content.getCurrentVersionNo(),
                TenantContextHolder.getRequiredTenantId());
        if (current != null && current.getFrozenAt() != null && current.getReviewDecision() == null) {
            throw exception(CONTENT_VERSION_STAGE_INVALID);
        }
        List<ContentVersionFileDO> currentFiles = current == null ? List.of() : contentVersionFileMapper.selectByVersionId(current.getId());
        Set<Long> currentFileIds = currentFiles.stream().map(ContentVersionFileDO::getInfraFileId)
                .collect(Collectors.toSet());
        Map<String, Map<Long, ContentVersionFileDO>> currentFilesByField = currentFiles.stream()
                .collect(Collectors.groupingBy(ContentVersionFileDO::getFieldKey,
                        Collectors.toMap(ContentVersionFileDO::getInfraFileId, Function.identity(),
                                (left, right) -> left, LinkedHashMap::new)));
        BoundFiles coverFiles = normalizeFiles(req.getCoverSnapshotJson(), "cover", userId, true, 1,
                currentFilesByField.getOrDefault("cover", Map.of()), currentFileIds);
        BoundFiles deliverableFiles = normalizeFiles(req.getDeliverableSnapshotJson(), "deliverable",
                userId, false, MAX_CONTENT_FILES,
                currentFilesByField.getOrDefault("deliverable", Map.of()), currentFileIds);
        ContentVersionDO row = new ContentVersionDO();
        row.setContentId(content.getId());
        int nextVersion = content.getCurrentVersionNo() == null ? 1 : content.getCurrentVersionNo() + 1;
        row.setVersionNo(nextVersion);
        // A client cannot choose a workflow stage; it is the state of the locked content record.
        row.setStage(content.getStatus());
        row.setTitleSnapshot(normalize(req.getTitleSnapshot(), content.getTitle()));
        row.setTopicSnapshot(normalize(req.getTopicSnapshot(), content.getTopic()));
        String deliverableUrl = normalizeOptional(req.getDeliverableUrl());
        String leadResourceUrl = normalizeOptional(req.getLeadResourceUrl());
        validateHttps(deliverableUrl);
        validateHttps(leadResourceUrl);
        row.setCoverSnapshotJson(coverFiles.snapshotJson());
        // 首个版本接受客户端提交的参考素材快照；创建后续版本时完整继承，不允许客户端改写引用审计。
        row.setMaterialRefsJson(current == null ? normalizeMaterialRefs(req.getMaterialRefsJson())
                : current.getMaterialRefsJson());
        row.setDeliverableUrl(deliverableUrl);
        row.setDeliverableSnapshotJson(deliverableFiles.snapshotJson());
        row.setScriptText(req.getScriptText());
        row.setPurposeValue(req.getPurposeValue());
        row.setPurposeLabelSnapshot(req.getPurposeLabelSnapshot());
        row.setFormatValue(req.getFormatValue());
        row.setFormatLabelSnapshot(req.getFormatLabelSnapshot());
        row.setDetailUrl(req.getDetailUrl());
        row.setCommentHook(req.getCommentHook());
        row.setLeadResourceUrl(leadResourceUrl);
        row.setReferenceContentVersionId(req.getReferenceContentVersionId());
        row.setReferenceWorkUrl(normalizeOptional(req.getReferenceWorkUrl()));
        row.setPlannedPublishAt(req.getPlannedPublishAt());
        row.setSubmittedByUserId(userId);
        row.setSubmittedAt(LocalDateTime.now());
        row.setIdempotencyKey(idempotencyKey);
        mapper.insert(row);
        insertFiles(row.getId(), coverFiles.files());
        insertFiles(row.getId(), deliverableFiles.files());
        if (contentService.advanceCurrentVersion(content.getId(), content.getVersion(), nextVersion) == 0) {
            throw exception(CONTENT_VERSION_STAGE_INVALID);
        }
        return row.getId();
    }

    /** Creates the next editable review version under the existing content aggregate. */
    @Transactional(rollbackFor = Exception.class)
    public Long copyForReview(ContentVersionDO source, ContentVersionSaveReqVO changes, Long userId) {
        ContentVersionSaveReqVO req = new ContentVersionSaveReqVO();
        req.setContentId(source.getContentId());
        req.setTitleSnapshot(changes.getTitleSnapshot() != null ? changes.getTitleSnapshot() : source.getTitleSnapshot());
        req.setTopicSnapshot(source.getTopicSnapshot());
        req.setCoverSnapshotJson(changes.getCoverSnapshotJson() != null ? changes.getCoverSnapshotJson() : source.getCoverSnapshotJson());
        // Omitted preserves history; an explicit empty array removes attachments only in the new version.
        req.setDeliverableSnapshotJson(changes.getDeliverableSnapshotJson() != null
                ? changes.getDeliverableSnapshotJson() : source.getDeliverableSnapshotJson());
        req.setDeliverableUrl(source.getDeliverableUrl());
        req.setScriptText(changes.getScriptText() != null ? changes.getScriptText() : source.getScriptText());
        req.setPurposeValue(changes.getPurposeValue() != null ? changes.getPurposeValue() : source.getPurposeValue());
        req.setPurposeLabelSnapshot(changes.getPurposeLabelSnapshot() != null ? changes.getPurposeLabelSnapshot() : source.getPurposeLabelSnapshot());
        req.setFormatValue(changes.getFormatValue() != null ? changes.getFormatValue() : source.getFormatValue());
        req.setFormatLabelSnapshot(changes.getFormatLabelSnapshot() != null ? changes.getFormatLabelSnapshot() : source.getFormatLabelSnapshot());
        req.setDetailUrl(changes.getDetailUrl() != null ? changes.getDetailUrl() : source.getDetailUrl());
        req.setCommentHook(changes.getCommentHook() != null ? changes.getCommentHook() : source.getCommentHook());
        req.setLeadResourceUrl(changes.getLeadResourceUrl() != null ? changes.getLeadResourceUrl() : source.getLeadResourceUrl());
        req.setReferenceContentVersionId(changes.getReferenceContentVersionId() != null ? changes.getReferenceContentVersionId() : source.getReferenceContentVersionId());
        req.setReferenceWorkUrl(changes.getReferenceWorkUrl() != null ? changes.getReferenceWorkUrl() : source.getReferenceWorkUrl());
        req.setPlannedPublishAt(changes.getPlannedPublishAt() != null ? changes.getPlannedPublishAt() : source.getPlannedPublishAt());
        return create(req, userId);
    }

    public ZsjosDirectUploadInitRespVO initUpload(ZsjosDirectUploadInitReqVO request, Long userId) {
        return BeanUtils.toBean(directUploadService.initContent(request, userId), ZsjosDirectUploadInitRespVO.class);
    }

    public ContentUploadRespVO completeUpload(String uploadToken, Long userId) {
        FileInfoRespDTO saved = directUploadService.completeContent(uploadToken, userId);
        ContentUploadRespVO response = new ContentUploadRespVO();
        response.setFileId(saved.getId());
        response.setName(saved.getName());
        response.setContentType(saved.getType());
        response.setSize(saved.getSize());
        response.setPreviewUrl(previewUrl(saved));
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public void review(Long id, boolean approved, String comment, Long userId) {
        throw exception(CONTENT_REVIEW_LEGACY_ENTRY_DISABLED);
    }

    private String normalize(String value, String fallback) {
        String candidate = value == null || value.isBlank() ? fallback : value;
        return candidate == null ? null : candidate.trim();
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 参考素材快照必须是合法 JSON，否则拒绝，避免脏数据写入版本审计。 */
    private String normalizeMaterialRefs(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) return null;
        try {
            JsonUtils.parseTree(normalized);
        } catch (RuntimeException error) {
            throw exception(CONTENT_VERSION_FILE_INVALID);
        }
        return normalized;
    }

    private String normalizeIdempotencyKey(String value) {
        String normalized = normalizeOptional(value);
        if (normalized != null && normalized.length() > 128) {
            throw exception(CONTENT_VERSION_IDEMPOTENCY_INVALID);
        }
        return normalized;
    }

    private boolean sameReplayPayload(ContentVersionSaveReqVO req, ContentDO content, ContentVersionDO replay) {
        if (!Objects.equals(normalize(req.getTitleSnapshot(), content.getTitle()), replay.getTitleSnapshot())
                || !Objects.equals(normalize(req.getTopicSnapshot(), content.getTopic()), replay.getTopicSnapshot())
                || !Objects.equals(normalizeOptional(req.getDeliverableUrl()), replay.getDeliverableUrl())
                || !Objects.equals(req.getScriptText(), replay.getScriptText())
                || !Objects.equals(normalizeOptional(req.getLeadResourceUrl()), replay.getLeadResourceUrl())
                || !Objects.equals(req.getPlannedPublishAt(), replay.getPlannedPublishAt())) {
            return false;
        }
        List<ContentVersionFileDO> replayFiles = new ArrayList<>(contentVersionFileMapper.selectByVersionId(replay.getId()));
        replayFiles.sort(java.util.Comparator.comparing(ContentVersionFileDO::getFieldKey)
                .thenComparing(ContentVersionFileDO::getSortNo)
                .thenComparing(ContentVersionFileDO::getId, java.util.Comparator.nullsLast(Long::compareTo)));
        Map<String, List<Long>> storedFileIds = replayFiles.stream()
                .collect(Collectors.groupingBy(ContentVersionFileDO::getFieldKey, LinkedHashMap::new,
                        Collectors.mapping(ContentVersionFileDO::getInfraFileId, Collectors.toList())));
        try {
            return Objects.equals(parseFileIds(req.getCoverSnapshotJson()),
                    storedFileIds.getOrDefault("cover", List.of()))
                    && Objects.equals(parseFileIds(req.getDeliverableSnapshotJson()),
                    storedFileIds.getOrDefault("deliverable", List.of()));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private List<Long> parseFileIds(String json) {
        if (json == null || json.isBlank()) return List.of();
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        collectFileIds(JsonUtils.parseTree(json), ids);
        return List.copyOf(ids);
    }

    private BoundFiles normalizeFiles(String json, String fieldKey, Long userId, boolean imageOnly, int maxCount,
                                      Map<Long, ContentVersionFileDO> inheritedFiles,
                                      Set<Long> currentFileIds) {
        if (json == null || json.isBlank()) return new BoundFiles(null, List.of());
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        try {
            var node = JsonUtils.parseTree(json);
            if (node != null && node.isArray() && node.isEmpty()) return new BoundFiles(null, List.of());
            collectFileIds(node, ids);
        } catch (RuntimeException error) {
            throw exception(CONTENT_VERSION_FILE_INVALID);
        }
        if (ids.isEmpty() || ids.size() > maxCount) throw exception(CONTENT_VERSION_FILE_INVALID);
        List<BoundFile> files = new ArrayList<>(ids.size());
        List<Map<String, Object>> snapshots = new ArrayList<>(ids.size());
        int sortNo = 1;
        for (Long id : ids) {
            ContentVersionFileDO inherited = inheritedFiles.get(id);
            if (inherited != null) {
                FileInfoRespDTO file = inheritedFileInfo(inherited);
                validateFileMetadata(file, imageOnly);
                snapshots.add(fileSnapshot(file));
                files.add(new BoundFile(fieldKey, sortNo++, file, inherited.getUploadedByUserId()));
                continue;
            }
            if (currentFileIds.contains(id)) {
                throw exception(CONTENT_VERSION_FILE_INVALID);
            }
            FileInfoRespDTO file;
            try {
                file = fileApi.getFileInfo(id);
            } catch (ServiceException error) {
                throw exception(CONTENT_VERSION_FILE_INVALID);
            }
            if (file == null || file.getPath() == null
                    || !file.getPath().startsWith(contentDirectory(userId) + "/")
                    || !Objects.equals(String.valueOf(userId), file.getCreator())) {
                throw exception(CONTENT_VERSION_FILE_INVALID);
            }
            validateFileMetadata(file, imageOnly);
            snapshots.add(fileSnapshot(file));
            files.add(new BoundFile(fieldKey, sortNo++, file, userId));
        }
        return new BoundFiles(JsonUtils.toJsonString(snapshots), files);
    }

    private FileInfoRespDTO inheritedFileInfo(ContentVersionFileDO inherited) {
        return new FileInfoRespDTO(inherited.getInfraFileId(), null, inherited.getOriginalName(), null,
                inherited.getFileUrlSnapshot(), inherited.getContentType(), inherited.getFileSize(),
                String.valueOf(inherited.getUploadedByUserId()));
    }

    private void validateFileMetadata(FileInfoRespDTO file, boolean imageOnly) {
        String contentType = Objects.toString(file.getType(), "").toLowerCase(Locale.ROOT);
        boolean acceptedType = imageOnly ? contentType.startsWith("image/")
                : ContentAttachmentTypes.accepts(contentType);
        if (file.getSize() == null || file.getSize() < 0 || file.getSize() > MAX_CONTENT_FILE_BYTES
                || !acceptedType) {
            throw exception(CONTENT_VERSION_FILE_INVALID);
        }
    }

    private Map<String, Object> fileSnapshot(FileInfoRespDTO file) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", file.getId());
        snapshot.put("name", file.getName());
        snapshot.put("contentType", file.getType());
        snapshot.put("size", file.getSize());
        snapshot.put("url", file.getUrl());
        return snapshot;
    }

    private void collectFileIds(tools.jackson.databind.JsonNode node, LinkedHashSet<Long> ids) {
        if (node == null || node.isNull()) return;
        if (node.isArray()) {
            node.forEach(item -> collectFileIds(item, ids));
            return;
        }
        if (node.isNumber()) {
            ids.add(node.longValue());
            return;
        }
        if (node.isTextual()) {
            ids.add(Long.valueOf(node.textValue()));
            return;
        }
        if (node.isObject()) {
            if (node.hasNonNull("fileId")) {
                collectFileIds(node.get("fileId"), ids);
                return;
            }
            if (node.hasNonNull("id")) {
                collectFileIds(node.get("id"), ids);
                return;
            }
        }
        throw exception(CONTENT_VERSION_FILE_INVALID);
    }

    private void insertFiles(Long contentVersionId, List<BoundFile> files) {
        if (files.isEmpty()) return;
        List<ContentVersionFileDO> rows = files.stream().map(bound -> {
            FileInfoRespDTO file = bound.file();
            ContentVersionFileDO row = new ContentVersionFileDO();
            row.setContentVersionId(contentVersionId);
            row.setFieldKey(bound.fieldKey());
            row.setSortNo(bound.sortNo());
            row.setInfraFileId(file.getId());
            row.setFileUrlSnapshot(file.getUrl());
            row.setOriginalName(file.getName());
            row.setContentType(file.getType());
            row.setFileSize(file.getSize());
            row.setUploadedByUserId(bound.uploadedByUserId());
            return row;
        }).toList();
        contentVersionFileMapper.insertBatch(rows);
    }

    private void validateHttps(String value) {
        if (value == null || value.isBlank()) return;
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw exception(CONTENT_VERSION_STAGE_INVALID);
            }
        } catch (IllegalArgumentException error) {
            throw exception(CONTENT_VERSION_STAGE_INVALID);
        }
    }

    private String contentDirectory(Long userId) {
        return "zsjos/content/" + userId;
    }

    private String previewUrl(FileInfoRespDTO file) {
        try {
            return fileApi.presignGetUrl(file.getId(), FILE_PREVIEW_SECONDS);
        } catch (RuntimeException ignored) {
            return file.getUrl();
        }
    }

    private ContentVersionRespVO toResponse(ContentVersionDO row) {
        ContentVersionRespVO response = BeanUtils.toBean(row, ContentVersionRespVO.class);
        response.setFiles(contentVersionFileMapper.selectByVersionId(row.getId()).stream().map(file -> {
            ContentVersionFileRespVO item = BeanUtils.toBean(file, ContentVersionFileRespVO.class);
            item.setPreviewUrl(previewUrl(new FileInfoRespDTO(file.getInfraFileId(), null, file.getOriginalName(),
                    null, file.getFileUrlSnapshot(), file.getContentType(), file.getFileSize(),
                    String.valueOf(file.getUploadedByUserId()))));
            return item;
        }).toList());
        return response;
    }

    private record BoundFiles(String snapshotJson, List<BoundFile> files) {
    }

    private record BoundFile(String fieldKey, int sortNo, FileInfoRespDTO file, Long uploadedByUserId) {
    }
}



