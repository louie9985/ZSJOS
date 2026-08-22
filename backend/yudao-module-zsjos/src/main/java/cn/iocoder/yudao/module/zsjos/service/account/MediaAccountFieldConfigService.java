package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountDetailSnapshotVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountFieldConfigRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountFieldConfigSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountFieldConfigDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountFieldConfigMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.MEDIA_ACCOUNT_FIELD_CONFIG_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.MEDIA_ACCOUNT_FIELD_CONFIG_NOT_PUBLISHED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.MEDIA_ACCOUNT_FIELD_CONFIG_VERSION_CONFLICT;

@Service
public class MediaAccountFieldConfigService {
    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final Set<String> TYPES = Set.of("text", "textarea", "number", "date", "select", "multi_select", "boolean");

    @Resource private MediaAccountFieldConfigMapper mapper;
    @Resource private DictDataApi dictDataApi;

    public MediaAccountFieldConfigRespVO getConfig() {
        MediaAccountFieldConfigRespVO response = new MediaAccountFieldConfigRespVO();
        response.setPublished(convert(mapper.selectPublished()));
        response.setDraft(convert(mapper.selectDraft()));
        return response;
    }

    public MediaAccountFieldConfigRespVO.VersionVO getPublished() {
        MediaAccountFieldConfigRespVO.VersionVO published = convert(mapper.selectPublished());
        if (published == null) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_NOT_PUBLISHED);
        return published;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long copyDraft(Long publishedId, Integer publishedVersion) {
        MediaAccountFieldConfigDO existing = mapper.selectDraft();
        if (existing != null) return existing.getId();
        MediaAccountFieldConfigDO published = mapper.selectById(publishedId);
        if (published == null || !"published".equals(published.getStatus())
                || !publishedVersion.equals(published.getVersion())) {
            throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_VERSION_CONFLICT);
        }
        MediaAccountFieldConfigDO draft = new MediaAccountFieldConfigDO();
        draft.setVersionNo(published.getVersionNo() + 1);
        draft.setStatus("draft");
        draft.setFieldsJson(published.getFieldsJson());
        draft.setVersion(0);
        mapper.insert(draft);
        return draft.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateDraft(MediaAccountFieldConfigSaveReqVO request) {
        List<MediaAccountFieldConfigRespVO.FieldVO> fields = normalized(request.getFields());
        if (mapper.updateDraft(request.getId(), request.getVersion(), JsonUtils.toJsonString(fields)) != 1) {
            throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_VERSION_CONFLICT);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id, Integer version) {
        MediaAccountFieldConfigDO draft = mapper.selectById(id);
        if (draft == null || !"draft".equals(draft.getStatus())) {
            throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_VERSION_CONFLICT);
        }
        normalized(JsonUtils.parseArray(draft.getFieldsJson(), MediaAccountFieldConfigRespVO.FieldVO.class));
        if (mapper.publish(id, version, LocalDateTime.now()) != 1) {
            throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_VERSION_CONFLICT);
        }
        mapper.update(null, new LambdaUpdateWrapper<MediaAccountFieldConfigDO>()
                .eq(MediaAccountFieldConfigDO::getStatus, "published")
                .ne(MediaAccountFieldConfigDO::getId, id)
                .set(MediaAccountFieldConfigDO::getStatus, "archived"));
    }

    public DetailSnapshot validateAndSnapshot(Map<String, Object> requestedValues) {
        MediaAccountFieldConfigDO config = mapper.selectPublished();
        if (config == null) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_NOT_PUBLISHED);
        List<MediaAccountFieldConfigRespVO.FieldVO> fields = normalized(
                JsonUtils.parseArray(config.getFieldsJson(), MediaAccountFieldConfigRespVO.FieldVO.class));
        Map<String, Object> values = requestedValues == null ? Map.of() : requestedValues;
        Set<String> allowed = fields.stream().filter(MediaAccountFieldConfigRespVO.FieldVO::getEnabled)
                .map(MediaAccountFieldConfigRespVO.FieldVO::getKey).collect(java.util.stream.Collectors.toSet());
        if (values.keySet().stream().anyMatch(key -> !allowed.contains(key))) {
            throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
        }
        Map<String, Object> persisted = new LinkedHashMap<>();
        List<MediaAccountDetailSnapshotVO> snapshots = new ArrayList<>();
        for (MediaAccountFieldConfigRespVO.FieldVO field : fields) {
            if (!field.getEnabled()) continue;
            Object value = values.get(field.getKey());
            if (field.getRequired() && empty(value)) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
            if (empty(value)) continue;
            MediaAccountDetailSnapshotVO snapshot = snapshot(field, value);
            persisted.put(field.getKey(), snapshot.getValue());
            snapshots.add(snapshot);
        }
        return new DetailSnapshot(config.getId(), persisted, snapshots);
    }

    private MediaAccountDetailSnapshotVO snapshot(MediaAccountFieldConfigRespVO.FieldVO field, Object rawValue) {
        MediaAccountDetailSnapshotVO result = new MediaAccountDetailSnapshotVO();
        result.setKey(field.getKey()); result.setLabel(field.getLabel()); result.setType(field.getType());
        result.setDictType(field.getDictType());
        if ("select".equals(field.getType())) {
            String value = String.valueOf(rawValue);
            result.setValue(value); result.setDisplayValue(resolveDictLabel(field.getDictType(), value));
        } else if ("multi_select".equals(field.getType())) {
            if (!(rawValue instanceof Collection<?> collection)) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
            List<String> values = collection.stream().map(String::valueOf).toList();
            dictDataApi.validateDictDataList(field.getDictType(), values);
            Map<String, String> labels = new LinkedHashMap<>();
            dictDataApi.getDictDataList(field.getDictType()).forEach(item -> labels.put(item.getValue(), item.getLabel()));
            result.setValue(values); result.setDisplayValue(values.stream().map(labels::get).collect(java.util.stream.Collectors.joining("、")));
        } else if ("number".equals(field.getType())) {
            if (!(rawValue instanceof Number)) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
            result.setValue(rawValue); result.setDisplayValue(String.valueOf(rawValue));
        } else if ("boolean".equals(field.getType())) {
            if (!(rawValue instanceof Boolean)) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
            result.setValue(rawValue); result.setDisplayValue(Boolean.TRUE.equals(rawValue) ? "是" : "否");
        } else {
            String value = String.valueOf(rawValue).trim();
            if (value.length() > 2000) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
            result.setValue(value); result.setDisplayValue(value);
        }
        return result;
    }

    private String resolveDictLabel(String dictType, String value) {
        dictDataApi.validateDictDataList(dictType, List.of(value));
        return dictDataApi.getDictDataList(dictType).stream().filter(item -> value.equals(item.getValue()))
                .findFirst().map(item -> item.getLabel()).orElseThrow(() -> exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID));
    }

    private List<MediaAccountFieldConfigRespVO.FieldVO> normalized(List<MediaAccountFieldConfigRespVO.FieldVO> source) {
        if (source == null || source.isEmpty()) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
        Set<String> keys = new HashSet<>();
        for (MediaAccountFieldConfigRespVO.FieldVO field : source) {
            if (field == null || field.getKey() == null || !KEY_PATTERN.matcher(field.getKey()).matches()
                    || !keys.add(field.getKey()) || field.getLabel() == null || field.getLabel().isBlank()
                    || field.getType() == null || !TYPES.contains(field.getType()) || field.getSort() == null
                    || field.getRequired() == null || field.getEnabled() == null || field.getSearchable() == null
                    || (("select".equals(field.getType()) || "multi_select".equals(field.getType()))
                    && (field.getDictType() == null || field.getDictType().isBlank()))) {
                throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
            }
            field.setKey(field.getKey().trim()); field.setLabel(field.getLabel().trim());
            if (field.getDictType() != null) field.setDictType(field.getDictType().trim());
        }
        if (source.stream().noneMatch(MediaAccountFieldConfigRespVO.FieldVO::getEnabled)) {
            throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
        }
        return source.stream().sorted(Comparator.comparing(MediaAccountFieldConfigRespVO.FieldVO::getSort)
                .thenComparing(MediaAccountFieldConfigRespVO.FieldVO::getKey)).toList();
    }

    private boolean empty(Object value) {
        return value == null || value instanceof String text && text.isBlank()
                || value instanceof Collection<?> collection && collection.isEmpty();
    }

    private MediaAccountFieldConfigRespVO.VersionVO convert(MediaAccountFieldConfigDO source) {
        if (source == null) return null;
        MediaAccountFieldConfigRespVO.VersionVO result = new MediaAccountFieldConfigRespVO.VersionVO();
        result.setId(source.getId()); result.setVersionNo(source.getVersionNo()); result.setStatus(source.getStatus());
        result.setPublishedAt(source.getPublishedAt()); result.setVersion(source.getVersion());
        result.setFields(JsonUtils.parseArray(source.getFieldsJson(), MediaAccountFieldConfigRespVO.FieldVO.class));
        return result;
    }

    public record DetailSnapshot(Long configVersionId, Map<String, Object> values,
                                 List<MediaAccountDetailSnapshotVO> snapshots) {}
}
