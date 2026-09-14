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
import java.util.Locale;
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
    private static final Set<String> TYPES = Set.of("text", "textarea", "number", "date", "select", "multi_select", "boolean", "image", "record", "url", "attachment", "materials");

    @Resource private MediaAccountFieldConfigMapper mapper;
    @Resource private DictDataApi dictDataApi;
    @Resource private org.springframework.beans.factory.ObjectProvider<cn.iocoder.yudao.module.zsjos.service.material.MaterialService> materials;
    @Resource private cn.iocoder.yudao.module.zsjos.service.material.MaterialTypeService materialTypes;

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
        MediaAccountFieldConfigDO published = mapper.selectPublished();
        if (published != null && draft.getVersionNo() <= published.getVersionNo()) {
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

    /** Explicitly reconcile an older draft without discarding its labels, types or custom fields. */
    @Transactional(rollbackFor = Exception.class)
    public void reconcileDraft(Long id, Integer version) {
        MediaAccountFieldConfigDO draft = mapper.selectById(id);
        MediaAccountFieldConfigDO published = mapper.selectPublished();
        if (draft == null || published == null || !"draft".equals(draft.getStatus())
                || !java.util.Objects.equals(draft.getVersion(), version)) {
            throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_VERSION_CONFLICT);
        }
        Map<String, MediaAccountFieldConfigRespVO.FieldVO> merged = new LinkedHashMap<>();
        normalized(JsonUtils.parseArray(published.getFieldsJson(), MediaAccountFieldConfigRespVO.FieldVO.class))
                .forEach(field -> merged.put(field.getKey(), field));
        for (var field : JsonUtils.parseArray(draft.getFieldsJson(), MediaAccountFieldConfigRespVO.FieldVO.class)) {
            var current = merged.get(field.getKey());
            if (current != null && (field.getOwnerType() == null || "UNASSIGNED".equals(field.getOwnerType()))) {
                field.setOwnerType(current.getOwnerType()); field.setGroup(current.getGroup());
                field.setSourceType(current.getSourceType()); field.setRequiredForComplete(current.getRequiredForComplete());
            }
            merged.put(field.getKey(), field);
        }
        if (mapper.update(null, new LambdaUpdateWrapper<MediaAccountFieldConfigDO>()
                .eq(MediaAccountFieldConfigDO::getId, id).eq(MediaAccountFieldConfigDO::getStatus, "draft")
                .eq(MediaAccountFieldConfigDO::getVersion, version)
                .set(MediaAccountFieldConfigDO::getFieldsJson, JsonUtils.toJsonString(normalized(new ArrayList<>(merged.values()))))
                .set(MediaAccountFieldConfigDO::getVersionNo, Math.max(draft.getVersionNo(), published.getVersionNo()) + 1)
                .set(MediaAccountFieldConfigDO::getVersion, version + 1)) != 1) {
            throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_VERSION_CONFLICT);
        }
    }

    public DetailSnapshot validateAndSnapshot(Map<String, Object> requestedValues) {
        return validateAndSnapshot(requestedValues, List.of());
    }

    public DetailSnapshot validateAndSnapshot(Map<String, Object> requestedValues,
                                               List<MediaAccountDetailSnapshotVO> previousSnapshots) {
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
            // Completeness is a reminder, never a creation or partial-save precondition.
            if (empty(value)) continue;
            MediaAccountDetailSnapshotVO snapshot = previousSnapshots.stream()
                    .filter(previous -> field.getKey().equals(previous.getKey())
                            && java.util.Objects.equals(normalizeComparable(value), normalizeComparable(previous.getValue())))
                    .findFirst().orElseGet(() -> snapshot(field, value));
            persisted.put(field.getKey(), snapshot.getValue());
            snapshots.add(snapshot);
        }
        return new DetailSnapshot(config.getId(), persisted, snapshots);
    }

    private Object normalizeComparable(Object value) {
        if (value instanceof Collection<?> collection) return collection.stream().map(String::valueOf).toList();
        return value == null ? null : String.valueOf(value);
    }

    private MediaAccountDetailSnapshotVO snapshot(MediaAccountFieldConfigRespVO.FieldVO field, Object rawValue) {
        MediaAccountDetailSnapshotVO result = new MediaAccountDetailSnapshotVO();
        result.setKey(field.getKey()); result.setLabel(field.getLabel()); result.setType(field.getType());
        result.setDictType(field.getDictType());
        result.setOwnerType(field.getOwnerType()); result.setGroup(field.getGroup());
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
        } else if ("image".equals(field.getType())) {
            if (!(rawValue instanceof Number number) || number.longValue() <= 0) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
            result.setValue(number.longValue()); result.setDisplayValue("已上传图片");
        } else if ("materials".equals(field.getType())) {
            if (!(rawValue instanceof Collection<?> ids) || ids.size()>100
                    || ids.stream().anyMatch(v -> !(v instanceof Number n) || n.longValue()<=0 || n.doubleValue()!=n.longValue())) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
            Long userId=cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId();
            var service=materials.getObject();
            var versions=((Collection<?>)rawValue).stream().map(v -> ((Number)v).longValue()).distinct().map(id -> {
                var version=service.getVersion(id,userId);
                var material=service.get(version.getMaterialId(),userId);
                if (!"EFFECTIVE".equals(version.getStatus()) || !"EFFECTIVE".equals(material.getStatus())
                        || !java.util.Objects.equals(material.getCurrentEffectiveVersionId(),id)
                        || !java.util.Objects.equals(field.getMaterialTypeCode(),materialTypes.getType(material.getMaterialTypeId()).getCode()))
                    throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
                version.setCoverPreviewUrl(null);
                if(version.getFiles()!=null)version.getFiles().forEach(file -> file.setPreviewUrl(null));
                return version;
            }).toList();
            result.setValue(versions.stream().map(v -> v.getId()).toList());result.setMaterialVersions(versions);
            result.setDisplayValue(versions.stream().map(v -> v.getTitle()).collect(java.util.stream.Collectors.joining("、")));
        } else if ("attachment".equals(field.getType())) {
            if (!(rawValue instanceof Collection<?> values) || values.size()>20
                    || values.stream().anyMatch(v -> !(v instanceof Number n) || n.longValue()<=0 || n.doubleValue()!=n.longValue())) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
            var ids=((Collection<?>)rawValue).stream().map(v -> ((Number)v).longValue()).distinct().toList();
            result.setValue(ids);result.setDisplayValue("已上传 " + ids.size() + " 份附件");
        } else if ("record".equals(field.getType())) {
            throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
        } else if ("number".equals(field.getType())) {
            if (!(rawValue instanceof Number)) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
            result.setValue(rawValue); result.setDisplayValue(String.valueOf(rawValue));
        } else if ("boolean".equals(field.getType())) {
            if (!(rawValue instanceof Boolean)) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
            result.setValue(rawValue); result.setDisplayValue(Boolean.TRUE.equals(rawValue) ? "是" : "否");
        } else {
            if (!(rawValue instanceof String)) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
            String value = String.valueOf(rawValue).trim();
            if (value.length() > (Set.of("nickname", "uid").contains(field.getKey()) ? 255 : 2000)) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
            if ("date".equals(field.getType())) {
                try { java.time.LocalDate.parse(value); } catch (java.time.format.DateTimeParseException invalid) { throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID); }
            }
            if ("url".equals(field.getType())) {
                try {
                    java.net.URI uri = java.net.URI.create(value);
                    if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null) throw new IllegalArgumentException();
                } catch (IllegalArgumentException invalid) { throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID); }
            }
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
            if (field.getDescription()!=null && field.getDescription().length()>2000) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
            if (field.getDictType() != null) field.setDictType(field.getDictType().trim());
            if (field.getOwnerType() == null || field.getOwnerType().isBlank()) field.setOwnerType("UNASSIGNED");
            field.setOwnerType(field.getOwnerType().trim().toUpperCase(Locale.ROOT));
            if (!Set.of("AUTO", "DIRECTOR", "OPERATOR", "UNASSIGNED").contains(field.getOwnerType()))
                throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
            field.setRequiredForCreate(false);
            if (field.getRequiredForComplete() == null) field.setRequiredForComplete(field.getRequired());
            if (field.getGroup() == null) field.setGroup("PROFILE");
            if (!Set.of("PROFILE", "POSITIONING", "STATUS", "METRICS", "REVIEW").contains(field.getGroup())) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
            if (field.getSourceType() == null) field.setSourceType("MANUAL");
            if (!Set.of("MANUAL", "ACCOUNT", "STUDENT", "PENDING").contains(field.getSourceType())) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
            field.setSnapshotPolicy("ON_SELECTION");
            if (!"MANUAL".equals(field.getSourceType()) && !"AUTO".equals(field.getOwnerType())) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
            if ("AUTO".equals(field.getOwnerType()) || "UNASSIGNED".equals(field.getOwnerType()) || "record".equals(field.getType())) field.setRequiredForComplete(false);
        }
        if (source.stream().noneMatch(MediaAccountFieldConfigRespVO.FieldVO::getEnabled)) {
            throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
        }
        for(var field:source)if("materials".equals(field.getType())) {
            if(field.getReferenceFor()==null || field.getReferenceFor().equals(field.getKey())
                    || source.stream().noneMatch(target -> target.getKey().equals(field.getReferenceFor()) && "textarea".equals(target.getType())
                        && java.util.Objects.equals(target.getGroup(),field.getGroup()) && java.util.Objects.equals(target.getOwnerType(),field.getOwnerType()))
                    || !Set.of("viral_account","viral_content").contains(field.getMaterialTypeCode()==null?"":field.getMaterialTypeCode())) throw exception(MEDIA_ACCOUNT_FIELD_CONFIG_INVALID);
            field.setRequired(false);field.setRequiredForComplete(false);
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
        result.setFields(normalized(JsonUtils.parseArray(source.getFieldsJson(), MediaAccountFieldConfigRespVO.FieldVO.class)));
        return result;
    }

    public record DetailSnapshot(Long configVersionId, Map<String, Object> values,
                                 List<MediaAccountDetailSnapshotVO> snapshots) {}
}
