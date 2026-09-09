package cn.iocoder.yudao.module.zsjos.service.material;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialInteractionRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialReferenceFieldReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialReferencePreviewReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialReferencePreviewRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialReferenceReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentVersionFileDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialFavoriteDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialFileDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialLikeDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialReferenceDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.material.MaterialVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentVersionFileMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentVersionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialFavoriteMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialFileMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialLikeMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialReferenceMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.material.MaterialVersionMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.content.ContentObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.jsoup.Jsoup;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.CONTENT_IN_PRODUCTION;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.CONTENT_REVISING;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.CONTENT_SCRIPT;
import static cn.iocoder.yudao.module.zsjos.enums.MediaWorkflowConstants.CONTENT_TOPIC;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class MaterialInteractionService {

    private static final Set<String> TEXT_TARGETS = Set.of(
            "titleSnapshot", "topicSnapshot", "scriptText");
    private static final Set<String> TEXT_SOURCE_TYPES = Set.of(
            FIELD_TEXT, FIELD_TEXTAREA, FIELD_RICH_TEXT, FIELD_NUMBER, FIELD_DATE, FIELD_DATETIME,
            FIELD_DICT_SINGLE, FIELD_DICT_MULTI, FIELD_EMPLOYEE, FIELD_DEPARTMENT, FIELD_HTTPS_LINK);
    private static final Set<String> MEDIA_TARGETS = Set.of(
            "coverSnapshotJson", "deliverableSnapshotJson");

    @Resource
    private MaterialService materialService;
    @Resource
    private MaterialMapper materialMapper;
    @Resource
    private MaterialVersionMapper versionMapper;
    @Resource
    private MaterialLikeMapper likeMapper;
    @Resource
    private MaterialFavoriteMapper favoriteMapper;
    @Resource
    private MaterialReferenceMapper referenceMapper;
    @Resource
    private MaterialFileMapper materialFileMapper;
    @Resource
    private ContentMapper contentMapper;
    @Resource
    private ContentVersionMapper contentVersionMapper;
    @Resource
    private ContentVersionFileMapper contentVersionFileMapper;
    @Resource
    private ContentObjectPermissionProvider contentPermissionProvider;
    @Resource
    private MaterialSchemaService schemaService;

    @ZsjosPermission(bizType = "material", bizId = "#materialId", action = "interact")
    @Transactional(rollbackFor = Exception.class)
    public MaterialInteractionRespVO toggleLike(Long materialId, Long userId) {
        MaterialDO material = requireUsableMaterial(materialId);
        MaterialLikeDO row = likeMapper.selectForUpdate(materialId, userId, tenantId());
        LocalDateTime now = LocalDateTime.now();
        boolean active;
        if (row == null) {
            row = new MaterialLikeDO();
            row.setMaterialId(materialId);
            row.setUserId(userId);
            row.setActive(true);
            row.setLikedAt(now);
            row.setVersion(0);
            likeMapper.insert(row);
            active = true;
        } else {
            active = !Boolean.TRUE.equals(row.getActive());
            row.setActive(active);
            row.setLikedAt(active ? now : row.getLikedAt());
            row.setUnlikedAt(active ? null : now);
            row.setVersion(row.getVersion() + 1);
            if (likeMapper.updateById(row) != 1) {
                throw exception(MATERIAL_LIKE_CONFLICT);
            }
        }
        int delta = active ? 1 : -1;
        if (materialMapper.incrementLike(materialId, delta) != 1) {
            throw exception(MATERIAL_LIKE_CONFLICT);
        }
        return new MaterialInteractionRespVO(active, Math.max(0, material.getLikeCount() + delta));
    }

    @ZsjosPermission(bizType = "material", bizId = "#materialId", action = "interact")
    @Transactional(rollbackFor = Exception.class)
    public MaterialInteractionRespVO toggleFavorite(Long materialId, Long userId) {
        MaterialDO material = requireUsableMaterial(materialId);
        MaterialFavoriteDO row = favoriteMapper.selectForUpdate(materialId, userId, tenantId());
        LocalDateTime now = LocalDateTime.now();
        boolean active;
        if (row == null) {
            row = new MaterialFavoriteDO();
            row.setMaterialId(materialId);
            row.setUserId(userId);
            row.setActive(true);
            row.setFavoritedAt(now);
            row.setVersion(0);
            favoriteMapper.insert(row);
            active = true;
        } else {
            active = !Boolean.TRUE.equals(row.getActive());
            row.setActive(active);
            row.setFavoritedAt(active ? now : row.getFavoritedAt());
            row.setUnfavoritedAt(active ? null : now);
            row.setVersion(row.getVersion() + 1);
            if (favoriteMapper.updateById(row) != 1) {
                throw exception(MATERIAL_FAVORITE_CONFLICT);
            }
        }
        int delta = active ? 1 : -1;
        if (materialMapper.incrementFavorite(materialId, delta) != 1) {
            throw exception(MATERIAL_FAVORITE_CONFLICT);
        }
        return new MaterialInteractionRespVO(active, Math.max(0, material.getFavoriteCount() + delta));
    }

    @ZsjosPermission(bizType = "material", bizId = "#materialId", action = "reference")
    public MaterialReferencePreviewRespVO previewReference(Long materialId, Long materialVersionId,
                                                           MaterialReferencePreviewReqVO request, Long userId) {
        ReferenceContext context = requireReferenceContext(materialId, materialVersionId,
                request.getTargetContentVersionId(), userId, false);
        AppliedReference applied = applyFields(context.materialVersion(), context.targetVersion(), request.getFields());
        MaterialReferencePreviewRespVO response = new MaterialReferencePreviewRespVO();
        response.setMaterialVersionId(materialVersionId);
        response.setTargetContentVersionId(request.getTargetContentVersionId());
        response.setBefore(applied.before());
        response.setAfter(applied.after());
        return response;
    }

    @ZsjosPermission(bizType = "material", bizId = "#materialId", action = "reference")
    @Transactional(rollbackFor = Exception.class)
    public void reference(Long materialId, Long materialVersionId, MaterialReferenceReqVO request, Long userId) {
        String fieldsJson = JsonUtils.toJsonString(request.getFields());
        MaterialReferenceDO replay = referenceMapper.selectByIdempotencyKey(request.getIdempotencyKey());
        if (replay != null) {
            requireMatchingReplay(replay, materialId, materialVersionId,
                    request.getTargetContentVersionId(), fieldsJson, userId);
            return;
        }
        ReferenceContext context = requireReferenceContext(materialId, materialVersionId,
                request.getTargetContentVersionId(), userId, true);
        replay = referenceMapper.selectByIdempotencyKey(request.getIdempotencyKey());
        if (replay != null) {
            requireMatchingReplay(replay, materialId, materialVersionId,
                    request.getTargetContentVersionId(), fieldsJson, userId);
            return;
        }
        MaterialReferenceDO existing = referenceMapper.selectByTarget(materialVersionId,
                request.getTargetContentVersionId());
        if (existing != null) {
            requireMatchingReplay(existing, materialId, materialVersionId,
                    request.getTargetContentVersionId(), fieldsJson, userId);
            return;
        }
        AppliedReference applied = applyFields(context.materialVersion(), context.targetVersion(), request.getFields());
        writeApplied(context.targetVersion(), applied);
        appendMaterialReference(context.targetVersion(), context.material(), context.materialVersion(), request.getFields());
        if (contentVersionMapper.updateById(context.targetVersion()) != 1) {
            throw exception(MATERIAL_REFERENCE_TARGET_INVALID);
        }
        insertContentFiles(context.targetVersion().getId(), applied.appendedFiles());
        MaterialReferenceDO reference = new MaterialReferenceDO();
        reference.setMaterialVersionId(materialVersionId);
        reference.setTargetContentVersionId(request.getTargetContentVersionId());
        reference.setReferencedByUserId(userId);
        reference.setCopiedFieldsJson(fieldsJson);
        reference.setIdempotencyKey(request.getIdempotencyKey());
        reference.setReferencedAt(LocalDateTime.now());
        try {
            referenceMapper.insert(reference);
        } catch (DuplicateKeyException error) {
            throw exception(MATERIAL_REFERENCE_CONFLICT);
        }
        if (materialMapper.incrementReference(materialId) != 1) {
            throw exception(MATERIAL_REFERENCE_CONFLICT);
        }
    }

    private void requireMatchingReplay(MaterialReferenceDO replay, Long materialId, Long materialVersionId,
                                       Long targetContentVersionId, String fieldsJson, Long userId) {
        if (!Objects.equals(replay.getMaterialVersionId(), materialVersionId)
                || !Objects.equals(replay.getTargetContentVersionId(), targetContentVersionId)
                || !Objects.equals(replay.getCopiedFieldsJson(), fieldsJson)
                || !Objects.equals(replay.getReferencedByUserId(), userId)
                || !materialVersionBelongsTo(replay.getMaterialVersionId(), materialId)) {
            throw exception(MATERIAL_REFERENCE_CONFLICT);
        }
    }

    private boolean materialVersionBelongsTo(Long materialVersionId, Long materialId) {
        MaterialVersionDO version = versionMapper.selectById(materialVersionId);
        return version != null && Objects.equals(version.getMaterialId(), materialId);
    }

    private MaterialDO requireUsableMaterial(Long materialId) {
        MaterialDO material = materialService.lockMaterial(materialId);
        if (!MATERIAL_EFFECTIVE.equals(material.getStatus()) || material.getCurrentEffectiveVersionId() == null) {
            throw exception(MATERIAL_STATE_INVALID);
        }
        return material;
    }

    private ReferenceContext requireReferenceContext(Long materialId, Long materialVersionId,
                                                     Long targetContentVersionId, Long userId, boolean lock) {
        MaterialDO material = lock ? requireUsableMaterial(materialId) : materialService.requireMaterial(materialId);
        if (!MATERIAL_EFFECTIVE.equals(material.getStatus())
                || !Objects.equals(material.getCurrentEffectiveVersionId(), materialVersionId)) {
            throw exception(MATERIAL_STATE_INVALID);
        }
        MaterialVersionDO materialVersion = lock
                ? versionMapper.selectByIdForUpdate(materialVersionId, tenantId())
                : versionMapper.selectById(materialVersionId);
        if (materialVersion == null || !VERSION_EFFECTIVE.equals(materialVersion.getStatus())
                || !Objects.equals(materialVersion.getMaterialId(), materialId)) {
            throw exception(MATERIAL_VERSION_NOT_EXISTS);
        }
        ContentVersionDO locatedTarget = contentVersionMapper.selectById(targetContentVersionId);
        if (locatedTarget == null) {
            throw exception(MATERIAL_REFERENCE_TARGET_INVALID);
        }
        ContentDO content = lock
                ? contentMapper.selectByIdForUpdate(locatedTarget.getContentId(), tenantId())
                : contentMapper.selectById(locatedTarget.getContentId());
        ContentVersionDO target = lock
                ? contentVersionMapper.selectByIdForUpdate(targetContentVersionId, tenantId())
                : locatedTarget;
        if (target == null || target.getFrozenAt() != null || target.getReviewDecision() != null) {
            throw exception(MATERIAL_REFERENCE_TARGET_INVALID);
        }
        if (content == null || content.getStatus() == null
                || !List.of(CONTENT_TOPIC, CONTENT_SCRIPT, CONTENT_IN_PRODUCTION, CONTENT_REVISING)
                .contains(content.getStatus())
                || !Objects.equals(target.getContentId(), content.getId())
                || !Objects.equals(content.getCurrentVersionNo(), target.getVersionNo())
                || !contentPermissionProvider.hasPermission(content.getId(), "version-create", userId)) {
            throw exception(MATERIAL_REFERENCE_TARGET_INVALID);
        }
        return new ReferenceContext(material, materialVersion, target);
    }

    private AppliedReference applyFields(MaterialVersionDO materialVersion, ContentVersionDO target,
                                         List<MaterialReferenceFieldReqVO> fields) {
        if (fields == null || fields.isEmpty()) {
            throw exception(MATERIAL_FIELD_INVALID, "至少选择一个引用字段");
        }
        Map<String, Object> values = parseMap(materialVersion.getValuesJson());
        Map<String, Object> snapshots = parseMap(materialVersion.getDictSnapshotJson());
        Map<String, String> sourceTypes = sourceTypes(materialVersion);
        Map<String, List<MaterialFileDO>> sourceFiles = materialFiles(materialVersion.getId());
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        Map<String, List<MaterialFileDO>> appendedFiles = new LinkedHashMap<>();
        Set<String> targetKeys = new LinkedHashSet<>();
        int copied = 0;
        for (MaterialReferenceFieldReqVO field : fields) {
            String targetKey = canonicalTarget(field.getTargetField());
            if (!targetKeys.add(targetKey)) {
                throw exception(MATERIAL_FIELD_INVALID, "同一生产内容字段不能重复引用");
            }
            if ("SKIP".equals(field.getAction())) {
                continue;
            }
            String sourceField = canonicalSource(field.getSourceField());
            String sourceType = sourceTypes.get(sourceField);
            if (sourceType == null) {
                throw exception(MATERIAL_FIELD_INVALID, "引用源字段不存在：" + field.getSourceField());
            }
            Object source = sourceValue(materialVersion, values, snapshots, sourceFiles, sourceField, sourceType);
            if (source == null) {
                throw exception(MATERIAL_FIELD_INVALID, "引用源字段不存在：" + field.getSourceField());
            }
            Object current = targetValue(target, targetKey);
            before.put(targetKey, current);
            Object result;
            if (TEXT_TARGETS.contains(targetKey)) {
                if (!TEXT_SOURCE_TYPES.contains(sourceType)) {
                    throw incompatibleField(field);
                }
                String sourceText = FIELD_RICH_TEXT.equals(sourceType)
                        ? Jsoup.parse(stringify(source)).text() : stringify(source);
                result = "APPEND".equals(field.getAction()) ? appendText((String) current, sourceText) : sourceText;
                validateTargetText(targetKey, (String) result);
            } else if ("leadResourceUrl".equals(targetKey)) {
                if (!FIELD_HTTPS_LINK.equals(sourceType) || !"REPLACE".equals(field.getAction())) {
                    throw incompatibleField(field);
                }
                String link = stringify(source).trim();
                if (!validHttps(link)) {
                    throw incompatibleField(field);
                }
                result = link;
            } else if (MEDIA_TARGETS.contains(targetKey)) {
                if (!"APPEND".equals(field.getAction())) {
                    throw exception(MATERIAL_FIELD_INVALID, "媒体字段只支持追加或跳过");
                }
                List<MaterialFileDO> files = sourceFiles.getOrDefault(sourceField, List.of());
                validateMediaSource(targetKey, sourceType, files);
                result = appendMedia(current, fileSnapshots(files));
                int maxCount = "coverSnapshotJson".equals(targetKey) ? 1 : 20;
                if (toList(result).size() > maxCount) {
                    throw exception(MATERIAL_FIELD_INVALID, "引用后的媒体文件数量超过限制");
                }
                appendedFiles.put(targetKey, files);
            } else {
                throw exception(MATERIAL_FIELD_INVALID, "不支持的生产内容字段：" + field.getTargetField());
            }
            after.put(targetKey, result);
            copied++;
        }
        if (copied == 0) {
            throw exception(MATERIAL_FIELD_INVALID, "至少复制一个字段");
        }
        return new AppliedReference(before, after, appendedFiles);
    }

    private void writeApplied(ContentVersionDO target, AppliedReference applied) {
        applied.after().forEach((key, value) -> {
            switch (key) {
                case "titleSnapshot" -> target.setTitleSnapshot((String) value);
                case "topicSnapshot" -> target.setTopicSnapshot((String) value);
                case "scriptText" -> target.setScriptText((String) value);
                case "leadResourceUrl" -> target.setLeadResourceUrl((String) value);
                case "coverSnapshotJson" -> target.setCoverSnapshotJson(JsonUtils.toJsonString(value));
                case "deliverableSnapshotJson" ->
                        target.setDeliverableSnapshotJson(JsonUtils.toJsonString(value));
                default -> throw exception(MATERIAL_FIELD_INVALID, "不支持的生产内容字段：" + key);
            }
        });
    }

    private void appendMaterialReference(ContentVersionDO target, MaterialDO material,
                                         MaterialVersionDO version, List<MaterialReferenceFieldReqVO> fields) {
        List<Map<String, Object>> references = parseReferenceList(target.getMaterialRefsJson());
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("materialId", material.getId());
        value.put("materialNo", material.getMaterialNo());
        value.put("materialVersionId", version.getId());
        value.put("materialVersionNo", version.getVersionNo());
        value.put("fields", fields);
        references.add(value);
        target.setMaterialRefsJson(JsonUtils.toJsonString(references));
    }

    private Object sourceValue(MaterialVersionDO version, Map<String, Object> values, Map<String, Object> snapshots,
                               Map<String, List<MaterialFileDO>> files, String sourceField, String sourceType) {
        if ("title".equals(sourceField)) return version.getTitle();
        if ("summary".equals(sourceField)) return version.getSummary();
        if ("cover".equals(sourceField) || "__cover__".equals(sourceField)) {
            List<MaterialFileDO> coverFiles = files.get("__cover__");
            return coverFiles == null || coverFiles.isEmpty() ? null : fileSnapshots(coverFiles);
        }
        List<MaterialFileDO> fileValue = files.get(sourceField);
        if (fileValue != null && !fileValue.isEmpty()) return fileSnapshots(fileValue);
        if (Set.of(FIELD_DICT_SINGLE, FIELD_DICT_MULTI, FIELD_EMPLOYEE, FIELD_DEPARTMENT).contains(sourceType)) {
            Object snapshot = pathValue(snapshots, sourceField);
            if (snapshot != null) return snapshotLabels(snapshot);
        }
        return pathValue(values, sourceField);
    }

    private Object pathValue(Object value, String path) {
        String[] segments = path.split("\\.");
        Object current = value;
        for (String segment : segments) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(segment);
            } else if (current instanceof Collection<?> collection) {
                List<Object> values = collection.stream().map(item -> (Object) (item instanceof Map<?, ?> map
                        ? map.get(segment) : null)).filter(Objects::nonNull).toList();
                current = values;
            } else {
                return null;
            }
        }
        return current;
    }

    private Map<String, List<MaterialFileDO>> materialFiles(Long versionId) {
        Map<String, List<MaterialFileDO>> result = new LinkedHashMap<>();
        for (MaterialFileDO file : materialFileMapper.selectByVersionId(versionId)) {
            result.computeIfAbsent(file.getFieldKey(), ignored -> new ArrayList<>()).add(file);
        }
        return result;
    }

    private List<Map<String, Object>> fileSnapshots(List<MaterialFileDO> files) {
        return files.stream().map(file -> {
            Map<String, Object> snapshot = new LinkedHashMap<String, Object>();
            snapshot.put("id", file.getInfraFileId());
            snapshot.put("name", file.getOriginalName());
            snapshot.put("contentType", file.getContentType());
            snapshot.put("size", file.getFileSize());
            snapshot.put("url", file.getFileUrlSnapshot());
            return snapshot;
        }).toList();
    }

    private Map<String, String> sourceTypes(MaterialVersionDO version) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("title", FIELD_TEXT);
        result.put("summary", FIELD_TEXTAREA);
        result.put("__cover__", FIELD_IMAGE);
        addSourceTypes(result, "", schemaService.parseFields(version.getFieldSnapshotJson()));
        return result;
    }

    private void addSourceTypes(Map<String, String> target, String parent,
                                List<MaterialFieldDefinition> fields) {
        for (MaterialFieldDefinition field : fields) {
            String path = parent.isEmpty() ? field.getKey() : parent + "." + field.getKey();
            if (FIELD_REPEAT_GROUP.equals(field.getType())) {
                addSourceTypes(target, path, field.getChildren());
            } else {
                target.put(path, field.getType());
            }
        }
    }

    private Object snapshotLabels(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.containsKey("label") ? Objects.toString(map.get("label"), "") : value;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::snapshotLabels).toList();
        }
        return value;
    }

    private RuntimeException incompatibleField(MaterialReferenceFieldReqVO field) {
        return exception(MATERIAL_FIELD_INVALID,
                "源字段 " + field.getSourceField() + " 与目标字段 " + field.getTargetField() + " 不兼容");
    }

    private void validateMediaSource(String targetKey, String sourceType, List<MaterialFileDO> files) {
        boolean cover = "coverSnapshotJson".equals(targetKey);
        boolean acceptedType = cover ? FIELD_IMAGE.equals(sourceType)
                : FIELD_IMAGE.equals(sourceType) || FIELD_VIDEO.equals(sourceType);
        boolean acceptedFiles = !files.isEmpty() && files.stream().allMatch(file -> {
            String contentType = Objects.toString(file.getContentType(), "").toLowerCase(java.util.Locale.ROOT);
            return cover ? contentType.startsWith("image/")
                    : contentType.startsWith("image/") || contentType.startsWith("video/");
        });
        if (!acceptedType || !acceptedFiles) {
            throw exception(MATERIAL_FIELD_INVALID, "媒体源字段与生产内容字段不兼容");
        }
    }

    private void validateTargetText(String targetKey, String value) {
        int maxLength = switch (targetKey) {
            case "titleSnapshot" -> 255;
            case "topicSnapshot" -> 1000;
            default -> Integer.MAX_VALUE;
        };
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw exception(MATERIAL_FIELD_INVALID, "引用后的文字内容为空或超过目标字段长度");
        }
    }

    private boolean validHttps(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private void insertContentFiles(Long contentVersionId,
                                    Map<String, List<MaterialFileDO>> appendedFiles) {
        if (appendedFiles.isEmpty()) return;
        List<ContentVersionFileDO> existing = contentVersionFileMapper.selectByVersionId(contentVersionId);
        Set<String> existingKeys = existing.stream().map(file -> file.getFieldKey() + ":" + file.getInfraFileId())
                .collect(java.util.stream.Collectors.toSet());
        Map<String, Integer> nextSort = new LinkedHashMap<>();
        existing.forEach(file -> nextSort.merge(file.getFieldKey(), file.getSortNo() + 1, Math::max));
        List<ContentVersionFileDO> rows = new ArrayList<>();
        appendedFiles.forEach((targetKey, files) -> {
            String fieldKey = "coverSnapshotJson".equals(targetKey) ? "cover" : "deliverable";
            for (MaterialFileDO source : files) {
                if (!existingKeys.add(fieldKey + ":" + source.getInfraFileId())) continue;
                int sortNo = nextSort.compute(fieldKey, (ignored, value) -> value == null ? 2 : value + 1) - 1;
                ContentVersionFileDO row = new ContentVersionFileDO();
                row.setContentVersionId(contentVersionId);
                row.setFieldKey(fieldKey);
                row.setSortNo(sortNo);
                row.setInfraFileId(source.getInfraFileId());
                row.setFileUrlSnapshot(source.getFileUrlSnapshot());
                row.setOriginalName(source.getOriginalName());
                row.setContentType(source.getContentType());
                row.setFileSize(source.getFileSize());
                row.setUploadedByUserId(source.getUploadedByUserId());
                rows.add(row);
            }
        });
        if (!rows.isEmpty()) contentVersionFileMapper.insertBatch(rows);
    }

    private Object targetValue(ContentVersionDO target, String key) {
        return switch (key) {
            case "titleSnapshot" -> target.getTitleSnapshot();
            case "topicSnapshot" -> target.getTopicSnapshot();
            case "scriptText" -> target.getScriptText();
            case "leadResourceUrl" -> target.getLeadResourceUrl();
            case "coverSnapshotJson" -> parseJsonValue(target.getCoverSnapshotJson());
            case "deliverableSnapshotJson" -> parseJsonValue(target.getDeliverableSnapshotJson());
            default -> null;
        };
    }

    private String canonicalTarget(String target) {
        if (target == null) return "";
        return switch (target.trim()) {
            case "title", "titleSnapshot" -> "titleSnapshot";
            case "topic", "topicSnapshot" -> "topicSnapshot";
            case "cover", "coverSnapshotJson" -> "coverSnapshotJson";
            case "deliverable", "deliverableSnapshotJson" -> "deliverableSnapshotJson";
            default -> target.trim();
        };
    }

    private String canonicalSource(String source) {
        if (source == null) return "";
        return "cover".equals(source.trim()) ? "__cover__" : source.trim();
    }

    private String stringify(Object value) {
        if (value instanceof String string) return string;
        if (value instanceof Collection<?> collection) {
            return String.join("\n", collection.stream().map(String::valueOf).toList());
        }
        return String.valueOf(value);
    }

    private String appendText(String current, String addition) {
        if (current == null || current.isBlank()) return addition;
        return current + "\n" + addition;
    }

    private List<Object> appendMedia(Object current, Object source) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        toList(current).forEach(item -> values.put(mediaIdentity(item), item));
        toList(source).forEach(item -> values.put(mediaIdentity(item), item));
        return new ArrayList<>(values.values());
    }

    private String mediaIdentity(Object value) {
        if (value instanceof Map<?, ?> map && map.get("id") != null) {
            return "id:" + map.get("id");
        }
        return JsonUtils.toJsonString(value);
    }

    private List<Object> toList(Object value) {
        if (value == null) return List.of();
        if (value instanceof Collection<?> collection) return new ArrayList<>(collection);
        return List.of(value);
    }

    private Object parseJsonValue(String json) {
        return json == null || json.isBlank() ? null : JsonUtils.parseObject(json, Object.class);
    }

    private List<Map<String, Object>> parseReferenceList(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<?, ?> raw : JsonUtils.parseArray(json, Map.class)) {
            Map<String, Object> value = new LinkedHashMap<>();
            raw.forEach((key, item) -> value.put(String.valueOf(key), item));
            result.add(value);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMap(String json) {
        Map<String, Object> value = json == null ? null : JsonUtils.parseObject(json, Map.class);
        return value == null ? Map.of() : value;
    }

    private Long tenantId() {
        return TenantContextHolder.getRequiredTenantId();
    }

    private record ReferenceContext(MaterialDO material, MaterialVersionDO materialVersion,
                                    ContentVersionDO targetVersion) {
    }

    private record AppliedReference(Map<String, Object> before, Map<String, Object> after,
                                    Map<String, List<MaterialFileDO>> appendedFiles) {
    }
}
