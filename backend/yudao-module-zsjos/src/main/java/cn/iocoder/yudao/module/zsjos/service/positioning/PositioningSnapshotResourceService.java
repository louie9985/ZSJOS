package cn.iocoder.yudao.module.zsjos.service.positioning;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardSubmissionMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

/** Reads only resources frozen in the authorized card scope; library permissions are not inherited. */
@Service
public class PositioningSnapshotResourceService {
    @Resource private PositioningCardMapper cardMapper;
    @Resource private PositioningCardSubmissionMapper submissionMapper;
    @Resource private FileApi fileApi;

    public String snapshot(Long cardId, Long submissionId) {
        var card = cardMapper.selectById(cardId);
        if (card == null) throw exception(POSITIONING_CARD_NOT_EXISTS);
        if (submissionId == null) return card.getDictSnapshotJson();
        var submission = submissionMapper.selectById(submissionId);
        if (submission == null || !Objects.equals(submission.getCardId(), cardId)) throw exception(POSITIONING_REFERENCE_INVALID);
        return submission.getDictSnapshotJson();
    }

    @ZsjosPermission(bizType = "positioning-card", bizId = "#cardId", action = "read")
    public Map<String, Object> material(Long cardId, Long submissionId, Long versionId) {
        return materialFromSnapshot(snapshot(cardId, submissionId), versionId);
    }

    @ZsjosPermission(bizType = "positioning-card", bizId = "#cardId", action = "read")
    public PositioningCardService.CardFile attachment(Long cardId, Long submissionId, Long fileId) {
        return attachmentFromSnapshot(snapshot(cardId, submissionId), fileId);
    }

    public PositioningCardService.CardFile attachmentFromSnapshot(String json, Long fileId) {
        for (Object value : parse(json).values()) if (value instanceof List<?> list) {
            for (Object item : list) if (item instanceof Map<?, ?> file && Objects.equals(number(file.get("id")), fileId)
                    && file.containsKey("name") && !file.containsKey("materialVersionId")) {
                var info = fileApi.getFileInfo(fileId);
                if (info == null) throw exception(POSITIONING_REFERENCE_INVALID);
                return new PositioningCardService.CardFile(fileId, String.valueOf(file.get("name")),
                        info.getType(), info.getSize(), fileApi.presignGetUrl(fileId, 300));
            }
        }
        throw exception(POSITIONING_REFERENCE_INVALID);
    }

    public Map<String, Object> materialFromSnapshot(String json, Long versionId) {
        for (Object value : parse(json).values()) if (value instanceof List<?> list) {
            for (Object item : list) if (item instanceof Map<?, ?> source
                    && Objects.equals(number(source.get("materialVersionId")), versionId)) {
                if (!(source.get("fields") instanceof List<?>)) throw exception(POSITIONING_REFERENCE_INVALID);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("id", versionId); result.put("materialId", source.get("materialId"));
                result.put("title", source.get("titleSnapshot")); result.put("fields", source.get("fields"));
                result.put("values", source.get("values")); result.put("dictSnapshot", source.get("dictSnapshot"));
                List<Map<String, Object>> files = new ArrayList<>();
                if (source.get("files") instanceof List<?> rawFiles) for (Object raw : rawFiles) {
                    if (!(raw instanceof Map<?, ?> file)) continue;
                    Map<String, Object> copy = new LinkedHashMap<>();
                    for (String key : List.of("id", "fieldKey", "groupIndex", "fileId", "name", "contentType", "size")) copy.put(key, file.get(key));
                    Long id = number(file.get("fileId"));
                    if (id != null && fileApi.getFileInfo(id) != null) copy.put("previewUrl", fileApi.presignGetUrl(id, 300));
                    files.add(copy);
                }
                result.put("files", files);
                return result;
            }
        }
        throw exception(POSITIONING_REFERENCE_INVALID);
    }

    private static Long number(Object value) { return value instanceof Number n ? n.longValue() : null; }
    private static Map<String, Object> parse(String json) {
        return json == null || json.isBlank() ? Map.of() : JsonUtils.parseObject(json, Map.class);
    }
}
