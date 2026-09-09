package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialSaveReqVO;
import cn.iocoder.yudao.module.zsjos.service.material.MaterialSchemaService;
import cn.iocoder.yudao.module.zsjos.service.material.MaterialService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.DICT_ACCOUNT_STAGE;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.DICT_ACCOUNT_TYPE;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.DICT_PROFESSION;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.CONTENT_REVIEW_COLLECTION_INVALID;

@Service
public class ContentReviewMaterialService {

    @Resource private MaterialService materialService;

    public MaterialService.AutoCollectionSnapshot buildAndValidate(String materialTypeCode,
                                                                   Long schemaVersionId,
                                                                   String schemaHash,
                                                                   Map<String, String> mapping,
                                                                   Map<String, Object> defaults,
                                                                   Map<String, Object> contentSnapshot,
                                                                   Map<String, Object> accountSnapshot,
                                                                   Set<Long> trustedContentFileIds,
                                                                   Long ownerUserId) {
        try {
            Map<String, Object> sources = sourceValues(contentSnapshot, accountSnapshot);
            Map<String, MaterialSchemaService.DictionarySnapshotValue> sourceDictionarySnapshots =
                    sourceDictionarySnapshots(accountSnapshot);
            Map<String, MaterialSchemaService.DictionarySnapshotValue> targetDictionarySnapshots =
                    new LinkedHashMap<>();
            Map<String, Object> values = new LinkedHashMap<>(defaults == null ? Map.of() : defaults);
            Long coverFileId = firstFileId(values.remove("__cover__"));
            for (Map.Entry<String, String> entry : mapping.entrySet()) {
                Object value = sources.get(entry.getValue());
                if ("__cover__".equals(entry.getKey())) {
                    Long mappedCover = firstFileId(value);
                    if (mappedCover != null) coverFileId = mappedCover;
                } else if (value != null) {
                    values.put(entry.getKey(), value);
                    MaterialSchemaService.DictionarySnapshotValue dictionarySnapshot =
                            sourceDictionarySnapshots.get(entry.getValue());
                    if (dictionarySnapshot != null) {
                        targetDictionarySnapshots.put(entry.getKey(), dictionarySnapshot);
                    }
                }
            }
            MaterialSaveReqVO request = new MaterialSaveReqVO();
            request.setTitle(firstText(contentSnapshot.get("titleSnapshot"), contentSnapshot.get("title"),
                    contentSnapshot.get("topicSnapshot"), contentSnapshot.get("topic"),
                    "生产内容 " + contentSnapshot.get("contentNo")));
            request.setSummary(limit(firstText(contentSnapshot.get("scriptText"),
                    contentSnapshot.get("topicSnapshot"), contentSnapshot.get("topic")), 2000));
            request.setCoverFileId(coverFileId);
            request.setValues(values);
            request.setPinned(false);
            request.setPriority(0);
            return materialService.prepareAutoCollection(materialTypeCode, schemaVersionId, schemaHash, request,
                    trustedContentFileIds, ownerUserId, targetDictionarySnapshots);
        } catch (RuntimeException error) {
            if (error instanceof cn.iocoder.yudao.framework.common.exception.ServiceException serviceError
                    && serviceError.getCode() == CONTENT_REVIEW_COLLECTION_INVALID.getCode()) {
                throw error;
            }
            throw exception(CONTENT_REVIEW_COLLECTION_INVALID, "字段映射或文件快照无效");
        }
    }

    public MaterialService.AutoCollectionSnapshot readAndValidate(String snapshotJson, String materialTypeCode,
                                                                   Long schemaVersionId, String schemaHash) {
        return materialService.parseAndValidateAutoCollectionSnapshot(snapshotJson, materialTypeCode,
                schemaVersionId, schemaHash);
    }

    private Map<String, Object> sourceValues(Map<String, Object> content, Map<String, Object> account) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("title", firstNonNull(content.get("titleSnapshot"), content.get("title")));
        values.put("topic", firstNonNull(content.get("topicSnapshot"), content.get("topic")));
        values.put("scriptText", content.get("scriptText"));
        values.put("deliverableUrl", content.get("deliverableUrl"));
        values.put("leadResourceUrl", content.get("leadResourceUrl"));
        values.put("plannedPublishAt", scalar(content.get("plannedPublishAt")));
        List<Long> coverIds = fileIds(content.get("coverSnapshot"));
        List<Long> deliverableIds = fileIds(content.get("deliverableSnapshot"));
        values.put("coverFileId", coverIds.isEmpty() ? null : coverIds.getFirst());
        values.put("coverFileIds", coverIds);
        values.put("deliverableFileIds", deliverableIds);
        values.put("accountType", account.get("accountTypePrimaryValue"));
        values.put("profession", account.get("trackPrimaryValue"));
        values.put("accountStage", account.get("sStage"));
        return values;
    }

    private Map<String, MaterialSchemaService.DictionarySnapshotValue> sourceDictionarySnapshots(
            Map<String, Object> account) {
        Map<String, MaterialSchemaService.DictionarySnapshotValue> snapshots = new LinkedHashMap<>();
        putDictionarySnapshot(snapshots, "accountType", DICT_ACCOUNT_TYPE,
                account.get("accountTypePrimaryValue"), account.get("accountTypePrimaryLabel"));
        putDictionarySnapshot(snapshots, "profession", DICT_PROFESSION,
                account.get("trackPrimaryValue"), account.get("trackPrimaryLabel"));
        putDictionarySnapshot(snapshots, "accountStage", DICT_ACCOUNT_STAGE,
                account.get("sStage"), account.get("sStageLabel"));
        return snapshots;
    }

    private void putDictionarySnapshot(Map<String, MaterialSchemaService.DictionarySnapshotValue> snapshots,
                                       String source, String dictType, Object codeValue, Object labelValue) {
        String code = text(codeValue);
        String label = text(labelValue);
        if (code != null && label != null) {
            snapshots.put(source, new MaterialSchemaService.DictionarySnapshotValue(dictType, code, label));
        }
    }

    private String text(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).trim();
    }

    private List<Long> fileIds(Object raw) {
        List<Long> result = new ArrayList<>();
        collectFileIds(raw, result);
        return result.stream().distinct().toList();
    }

    @SuppressWarnings("unchecked")
    private void collectFileIds(Object raw, List<Long> result) {
        if (raw == null) return;
        if (raw instanceof String text) {
            if (text.isBlank()) return;
            try {
                collectFileIds(JsonUtils.parseTree(text), result);
            } catch (RuntimeException ignored) {
                addLong(text, result);
            }
            return;
        }
        if (raw instanceof Number number) {
            result.add(number.longValue());
            return;
        }
        if (raw instanceof tools.jackson.databind.JsonNode node) {
            if (node.isNumber()) result.add(node.longValue());
            else if (node.isTextual()) addLong(node.textValue(), result);
            else if (node.isArray()) node.forEach(item -> collectFileIds(item, result));
            else if (node.isObject()) {
                if (node.hasNonNull("fileId")) collectFileIds(node.get("fileId"), result);
                else if (node.hasNonNull("id")) collectFileIds(node.get("id"), result);
            }
            return;
        }
        if (raw instanceof Collection<?> collection) {
            collection.forEach(item -> collectFileIds(item, result));
            return;
        }
        if (raw instanceof Map<?, ?> map) {
            Object id = map.containsKey("fileId") ? map.get("fileId") : map.get("id");
            collectFileIds(id, result);
        }
    }

    private void addLong(String value, List<Long> result) {
        try {
            result.add(Long.valueOf(value));
        } catch (NumberFormatException ignored) {
            // A URL or label in a file snapshot is not a stable Infra file identifier.
        }
    }

    private Long firstFileId(Object raw) {
        List<Long> ids = fileIds(raw);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private Object scalar(Object value) {
        return value instanceof TemporalAccessor ? value.toString() : value;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) if (value != null) return value;
        return null;
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value).trim();
        }
        return null;
    }

    private String limit(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
