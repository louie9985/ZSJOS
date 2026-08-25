package cn.iocoder.yudao.module.zsjos.service.director;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.ip.AreaApi;
import cn.iocoder.yudao.module.system.api.ip.dto.AreaRespDTO;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.director.vo.DirectorFormTemplateVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.director.DirectorFormTemplateDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.director.DirectorFormTemplateVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.director.DirectorFormTemplateMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.director.DirectorFormTemplateVersionMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class DirectorFormTemplateService {
    public static final String SCENE_INTERVIEW = "director_interview";
    public static final String SCENE_POSITIONING = "positioning_card";
    private static final Set<String> ENUM_TYPES = Set.of("select", "multi_select", "radio", "checkbox_group");
    private static final Map<String, String> INTERVIEW_SYSTEM_FIELDS = orderedMap(new String[][]{
            {"certificates", "checkbox_group"}, {"certificatePractice", "radio"}, {"examPreparation", "text"},
            {"age", "number"}, {"gender", "radio"}, {"region", "region"}, {"currentOccupation", "text"},
            {"workTime", "text"}, {"workExperience", "textarea"}, {"familyMembers", "textarea"},
            {"hobbies", "textarea"}, {"videoEditing", "radio"}, {"videoShooting", "radio"},
            {"liveExperience", "radio"}, {"shootingEquipment", "checkbox_group"}, {"equipmentModel", "text"},
            {"mediaTime", "radio"}, {"continuousTime", "text"}, {"appearanceWillingness", "radio"},
            {"purchaseMotivations", "checkbox_group"}, {"deliveryRisks", "checkbox_group"},
            {"sixDimensionCommunicated", "checkbox"}
    });
    private static final Map<String, String> POSITIONING_SYSTEM_FIELDS = orderedMap(new String[][]{
            {"identityTags", "checkbox_group"}, {"strongStoryHook", "text"}, {"existingMaterials", "checkbox_group"},
            {"timeInvestment", "radio"}, {"appearanceWillingness", "radio"}, {"expressionAbility", "radio"},
            {"executionStability", "radio"}, {"riskTags", "checkbox_group"}, {"commercialPositioning", "textarea"},
            {"personaTypes", "checkbox_group"}, {"targetAudience", "radio"}, {"contentPillars", "checkbox_group"},
            {"videoFormats", "checkbox_group"}, {"imageTextFormats", "checkbox_group"}, {"recommendedMatchRate", "number"}
    });

    @Resource private DirectorFormTemplateMapper templateMapper;
    @Resource private DirectorFormTemplateVersionMapper versionMapper;
    @Resource private DictDataApi dictDataApi;
    @Resource private AreaApi areaApi;

    public List<DirectorFormTemplateVO.TemplateResp> list(String scene) {
        requireScene(scene);
        return templateMapper.selectByScene(scene).stream().map(this::convert).toList();
    }

    public DirectorFormTemplateVO.TemplateResp get(Long id, String scene) {
        DirectorFormTemplateDO template = requireTemplate(id, scene);
        return convert(template);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createPositioning(DirectorFormTemplateVO.CreateReq request) {
        List<DirectorFormTemplateVO.Field> fields = normalize(SCENE_POSITIONING, request.getFields());
        if (templateMapper.selectByScene(SCENE_POSITIONING).stream()
                .anyMatch(row -> row.getTemplateCode().equals(request.getTemplateCode()))) {
            throw exception(DIRECTOR_FORM_TEMPLATE_INVALID);
        }
        if (Boolean.TRUE.equals(request.getDefaultTemplate())) clearDefault(SCENE_POSITIONING, null);
        DirectorFormTemplateDO template = new DirectorFormTemplateDO().setScene(SCENE_POSITIONING)
                .setTemplateCode(request.getTemplateCode()).setName(request.getName().trim())
                .setDefaultTemplate(request.getDefaultTemplate()).setStatus("enabled").setVersion(0);
        templateMapper.insert(template);
        DirectorFormTemplateVersionDO draft = new DirectorFormTemplateVersionDO().setTemplateId(template.getId())
                .setVersionNo(1).setStatus("draft").setFieldsJson(JsonUtils.toJsonString(fields)).setVersion(0);
        versionMapper.insert(draft);
        return template.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long copyDraft(Long templateId, Integer templateVersion, String scene) {
        DirectorFormTemplateDO template = requireTemplate(templateId, scene);
        if (!Objects.equals(template.getVersion(), templateVersion)) throw exception(DIRECTOR_FORM_TEMPLATE_VERSION_CONFLICT);
        DirectorFormTemplateVersionDO existing = versionMapper.selectDraft(templateId);
        if (existing != null) return existing.getId();
        if (template.getPublishedVersionId() == null) throw exception(DIRECTOR_FORM_TEMPLATE_NOT_EXISTS);
        DirectorFormTemplateVersionDO published = versionMapper.selectById(template.getPublishedVersionId());
        if (published == null) throw exception(DIRECTOR_FORM_TEMPLATE_NOT_EXISTS);
        int next = versionMapper.selectByTemplate(templateId).stream().mapToInt(DirectorFormTemplateVersionDO::getVersionNo)
                .max().orElse(0) + 1;
        DirectorFormTemplateVersionDO draft = new DirectorFormTemplateVersionDO().setTemplateId(templateId)
                .setVersionNo(next).setStatus("draft").setFieldsJson(published.getFieldsJson()).setVersion(0);
        versionMapper.insert(draft);
        return draft.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateDraft(Long templateId, DirectorFormTemplateVO.SaveDraftReq request, String scene) {
        DirectorFormTemplateDO template = requireTemplate(templateId, scene);
        DirectorFormTemplateVersionDO draft = versionMapper.selectById(request.getVersionId());
        if (draft == null || !Objects.equals(draft.getTemplateId(), templateId) || !"draft".equals(draft.getStatus())) {
            throw exception(DIRECTOR_FORM_TEMPLATE_VERSION_CONFLICT);
        }
        List<DirectorFormTemplateVO.Field> fields = normalize(scene, request.getFields());
        int changed = versionMapper.update(null, new LambdaUpdateWrapper<DirectorFormTemplateVersionDO>()
                .eq(DirectorFormTemplateVersionDO::getId, draft.getId()).eq(DirectorFormTemplateVersionDO::getVersion, request.getVersion())
                .eq(DirectorFormTemplateVersionDO::getStatus, "draft")
                .set(DirectorFormTemplateVersionDO::getFieldsJson, JsonUtils.toJsonString(fields))
                .set(DirectorFormTemplateVersionDO::getVersion, request.getVersion() + 1));
        if (changed != 1) throw exception(DIRECTOR_FORM_TEMPLATE_VERSION_CONFLICT);
        if (Boolean.TRUE.equals(request.getDefaultTemplate())) clearDefault(scene, templateId);
        template.setName(request.getName().trim()).setDefaultTemplate(request.getDefaultTemplate());
        template.setVersion(template.getVersion() + 1);
        templateMapper.updateById(template);
    }

    @Transactional(rollbackFor = Exception.class)
    public void publish(Long templateId, DirectorFormTemplateVO.PublishReq request, Long userId, String scene) {
        DirectorFormTemplateDO template = requireTemplate(templateId, scene);
        DirectorFormTemplateVersionDO draft = versionMapper.selectById(request.getVersionId());
        if (draft == null || !Objects.equals(draft.getTemplateId(), templateId) || !"draft".equals(draft.getStatus())) {
            throw exception(DIRECTOR_FORM_TEMPLATE_VERSION_CONFLICT);
        }
        normalize(scene, JsonUtils.parseArray(draft.getFieldsJson(), DirectorFormTemplateVO.Field.class));
        int changed = versionMapper.update(null, new LambdaUpdateWrapper<DirectorFormTemplateVersionDO>()
                .eq(DirectorFormTemplateVersionDO::getId, draft.getId()).eq(DirectorFormTemplateVersionDO::getVersion, request.getVersion())
                .eq(DirectorFormTemplateVersionDO::getStatus, "draft")
                .set(DirectorFormTemplateVersionDO::getStatus, "published")
                .set(DirectorFormTemplateVersionDO::getPublishedByUserId, userId)
                .set(DirectorFormTemplateVersionDO::getPublishedAt, LocalDateTime.now())
                .set(DirectorFormTemplateVersionDO::getVersion, request.getVersion() + 1));
        if (changed != 1) throw exception(DIRECTOR_FORM_TEMPLATE_VERSION_CONFLICT);
        if (template.getPublishedVersionId() != null) {
            versionMapper.update(null, new LambdaUpdateWrapper<DirectorFormTemplateVersionDO>()
                    .eq(DirectorFormTemplateVersionDO::getId, template.getPublishedVersionId())
                    .set(DirectorFormTemplateVersionDO::getStatus, "archived"));
        }
        template.setPublishedVersionId(draft.getId()).setStatus("enabled").setVersion(template.getVersion() + 1);
        templateMapper.updateById(template);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteUnusedPositioning(Long id) {
        DirectorFormTemplateDO template = requireTemplate(id, SCENE_POSITIONING);
        if (template.getPublishedVersionId() != null || Boolean.TRUE.equals(template.getDefaultTemplate())) {
            throw exception(DIRECTOR_FORM_TEMPLATE_INVALID);
        }
        versionMapper.deleteByIds(versionMapper.selectByTemplate(id).stream().map(DirectorFormTemplateVersionDO::getId).toList());
        templateMapper.deleteById(id);
    }

    public DirectorFormTemplateVersionDO requirePublished(String scene, Long templateId) {
        DirectorFormTemplateDO template = templateId == null ? templateMapper.selectDefault(scene) : requireTemplate(templateId, scene);
        if (template == null || !"enabled".equals(template.getStatus()) || template.getPublishedVersionId() == null) {
            throw exception(DIRECTOR_FORM_TEMPLATE_NOT_EXISTS);
        }
        DirectorFormTemplateVersionDO version = versionMapper.selectById(template.getPublishedVersionId());
        if (version == null || !"published".equals(version.getStatus())) throw exception(DIRECTOR_FORM_TEMPLATE_NOT_EXISTS);
        return version;
    }

    public List<DirectorFormTemplateVO.Field> fields(DirectorFormTemplateVersionDO version) {
        List<DirectorFormTemplateVO.Field> fields = JsonUtils.parseArray(version.getFieldsJson(), DirectorFormTemplateVO.Field.class);
        if (fields == null || fields.isEmpty()) throw exception(DIRECTOR_FORM_TEMPLATE_INVALID);
        return fields.stream().sorted(Comparator.comparing(DirectorFormTemplateVO.Field::getSort,
                Comparator.nullsLast(Integer::compareTo)).thenComparing(DirectorFormTemplateVO.Field::getKey)).toList();
    }

    public DirectorFormTemplateVO.Snapshot validateAndSnapshot(String scene, Long templateId,
            Map<String, Object> requestedValues, boolean submit) {
        DirectorFormTemplateVersionDO version = requirePublished(scene, templateId);
        return validateAndSnapshotVersion(scene, version.getId(), requestedValues, submit, Map.of());
    }

    /** Existing business drafts must remain bound to their original immutable template version. */
    public DirectorFormTemplateVO.Snapshot validateAndSnapshotVersion(String scene, Long templateVersionId,
            Map<String, Object> requestedValues, boolean submit, Map<String, Object> previousDictSnapshots) {
        requireScene(scene);
        DirectorFormTemplateVersionDO version = versionMapper.selectById(templateVersionId);
        DirectorFormTemplateDO template = version == null ? null : templateMapper.selectById(version.getTemplateId());
        if (version == null || template == null || !scene.equals(template.getScene())
                || !Set.of("published", "archived").contains(version.getStatus())) {
            throw exception(DIRECTOR_FORM_TEMPLATE_NOT_EXISTS);
        }
        List<DirectorFormTemplateVO.Field> fields = fields(version).stream().filter(DirectorFormTemplateVO.Field::getEnabled).toList();
        Map<String, Object> values = requestedValues == null ? Map.of() : requestedValues;
        Map<String, Object> priorSnapshots = previousDictSnapshots == null ? Map.of() : previousDictSnapshots;
        Set<String> allowed = fields.stream().map(DirectorFormTemplateVO.Field::getKey).collect(Collectors.toSet());
        if (values.keySet().stream().anyMatch(key -> !allowed.contains(key))) throw exception(DIRECTOR_FORM_VALUE_INVALID);
        Map<String, Object> persisted = new LinkedHashMap<>();
        Map<String, Object> dictSnapshots = new LinkedHashMap<>();
        for (DirectorFormTemplateVO.Field field : fields) {
            Object raw = values.get(field.getKey());
            if (submit && Boolean.TRUE.equals(field.getRequired()) && empty(raw)) throw exception(DIRECTOR_FORM_VALUE_INVALID);
            if (empty(raw)) continue;
            validateScalar(field, raw);
            Object normalized = "region".equals(field.getType()) ? normalizeRegion(raw, submit) : raw;
            persisted.put(field.getKey(), normalized);
            if (field.getDictType() != null) {
                dictSnapshots.put(field.getKey(), snapshotDictionary(field, raw, priorSnapshots.get(field.getKey())));
            }
        }
        validateBusinessRules(scene, persisted, submit);
        DirectorFormTemplateVO.Snapshot result = new DirectorFormTemplateVO.Snapshot();
        result.setTemplateId(version.getTemplateId()); result.setTemplateVersionId(version.getId());
        result.setTemplateVersionNo(version.getVersionNo()); result.setFields(fields);
        result.setValues(persisted); result.setDictSnapshots(dictSnapshots);
        return result;
    }

    private Object snapshotDictionary(DirectorFormTemplateVO.Field field, Object raw, Object previousSnapshot) {
        List<String> selected = raw instanceof Collection<?> collection ? collection.stream().map(String::valueOf).toList()
                : List.of(String.valueOf(raw));
        Map<String, Map<String, String>> historical = historicalDictionaryEntries(previousSnapshot);
        List<String> currentSelections = selected.stream().filter(value -> !historical.containsKey(value)).toList();
        Map<String, String> labels = new HashMap<>();
        if (!currentSelections.isEmpty()) {
            List<DictDataRespDTO> options;
            try { options = dictDataApi.getDictDataList(field.getDictType()); }
            catch (RuntimeException ex) { throw exception(DIRECTOR_FORM_VALUE_INVALID); }
            labels = options.stream().filter(row -> row.getStatus() == null || row.getStatus() == 0)
                    .collect(Collectors.toMap(DictDataRespDTO::getValue, DictDataRespDTO::getLabel, (a, b) -> a));
            if (!labels.keySet().containsAll(currentSelections)) {
                throw exception(DIRECTOR_FORM_VALUE_INVALID);
            }
        }
        Map<String, String> currentLabels = labels;
        List<Map<String, String>> entries = selected.stream().map(value -> historical.containsKey(value)
                ? historical.get(value) : Map.of("value", value, "labelSnapshot", currentLabels.get(value),
                "dictType", field.getDictType())).toList();
        return raw instanceof Collection<?> ? entries : entries.get(0);
    }

    private Map<String, Map<String, String>> historicalDictionaryEntries(Object snapshot) {
        Collection<?> entries = snapshot instanceof Collection<?> collection ? collection
                : snapshot instanceof Map<?, ?> ? List.of(snapshot) : List.of();
        Map<String, Map<String, String>> result = new HashMap<>();
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> values) || values.get("value") == null
                    || values.get("labelSnapshot") == null || values.get("dictType") == null) continue;
            result.put(String.valueOf(values.get("value")), Map.of(
                    "value", String.valueOf(values.get("value")),
                    "labelSnapshot", String.valueOf(values.get("labelSnapshot")),
                    "dictType", String.valueOf(values.get("dictType"))));
        }
        return result;
    }

    private void validateScalar(DirectorFormTemplateVO.Field field, Object raw) {
        if ("region".equals(field.getType())) {
            if (!(raw instanceof Map<?, ?>) && !(raw instanceof String && !((String) raw).isBlank())) {
                throw exception(DIRECTOR_FORM_VALUE_INVALID);
            }
            return;
        }
        if (Set.of("multi_select", "checkbox_group").contains(field.getType())
                && !(raw instanceof Collection<?>)) throw exception(DIRECTOR_FORM_VALUE_INVALID);
        if (Set.of("select", "radio").contains(field.getType())
                && raw instanceof Collection<?>) throw exception(DIRECTOR_FORM_VALUE_INVALID);
        if (raw instanceof Collection<?> collection) {
            int size = collection.size();
            if (field.getMinSelections() != null && size < field.getMinSelections()
                    || field.getMaxSelections() != null && size > field.getMaxSelections()) throw exception(DIRECTOR_FORM_VALUE_INVALID);
        }
        if ("number".equals(field.getType())) {
            if (!(raw instanceof Number number)) throw exception(DIRECTOR_FORM_VALUE_INVALID);
            if (field.getMinValue() != null && number.doubleValue() < field.getMinValue()
                    || field.getMaxValue() != null && number.doubleValue() > field.getMaxValue()) throw exception(DIRECTOR_FORM_VALUE_INVALID);
        }
        if (field.getMaxLength() != null && String.valueOf(raw).length() > field.getMaxLength()) {
            throw exception(DIRECTOR_FORM_VALUE_INVALID);
        }
        if ("checkbox".equals(field.getType()) && !(raw instanceof Boolean)) throw exception(DIRECTOR_FORM_VALUE_INVALID);
    }

    /** Historical drafts may retain their raw text, but a new submission must use a current System area. */
    private Object normalizeRegion(Object raw, boolean submit) {
        if (raw instanceof String text) {
            if (submit) throw exception(DIRECTOR_FORM_VALUE_INVALID);
            return text;
        }
        if (!(raw instanceof Map<?, ?> values)) throw exception(DIRECTOR_FORM_VALUE_INVALID);
        Object codeValue = values.get("code");
        Integer code;
        try {
            code = codeValue instanceof Number number ? number.intValue() : Integer.valueOf(String.valueOf(codeValue));
        } catch (RuntimeException ex) {
            throw exception(DIRECTOR_FORM_VALUE_INVALID);
        }
        AreaRespDTO area;
        try {
            area = areaApi.getArea(code);
        } catch (RuntimeException ex) {
            throw exception(DIRECTOR_FORM_VALUE_INVALID);
        }
        if (area == null || area.getName() == null || area.getName().isBlank()
                || area.getStatus() != null && area.getStatus() != 0) {
            throw exception(DIRECTOR_FORM_VALUE_INVALID);
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", area.getId());
        snapshot.put("labelSnapshot", area.getName());
        return snapshot;
    }

    private void validateBusinessRules(String scene, Map<String, Object> values, boolean submit) {
        if (SCENE_INTERVIEW.equals(scene)) {
            mutuallyExclusive(values.get("certificates"), "none");
            mutuallyExclusive(values.get("shootingEquipment"), "none");
            if (submit && values.get("certificates") instanceof Collection<?> certificates
                    && !certificates.isEmpty() && !certificates.contains("none")
                    && empty(values.get("certificatePractice"))) throw exception(DIRECTOR_FORM_VALUE_INVALID);
            if (submit && !Boolean.TRUE.equals(values.get("sixDimensionCommunicated"))) {
                throw exception(DIRECTOR_FORM_VALUE_INVALID);
            }
        }
    }

    private void mutuallyExclusive(Object raw, String exclusiveValue) {
        if (raw instanceof Collection<?> values && values.size() > 1 && values.contains(exclusiveValue)) {
            throw exception(DIRECTOR_FORM_VALUE_INVALID);
        }
    }

    private List<DirectorFormTemplateVO.Field> normalize(String scene, List<DirectorFormTemplateVO.Field> source) {
        requireScene(scene);
        if (source == null || source.isEmpty()) throw exception(DIRECTOR_FORM_TEMPLATE_INVALID);
        Map<String, String> system = SCENE_INTERVIEW.equals(scene) ? INTERVIEW_SYSTEM_FIELDS : POSITIONING_SYSTEM_FIELDS;
        Set<String> keys = new HashSet<>();
        for (DirectorFormTemplateVO.Field field : source) {
            if (field == null || !keys.add(field.getKey()) || field.getSort() == null || field.getEnabled() == null
                    || field.getRequired() == null || field.getTitle() == null || field.getTitle().isBlank()) {
                throw exception(DIRECTOR_FORM_TEMPLATE_INVALID);
            }
            String systemType = system.get(field.getKey());
            if (systemType != null && !systemType.equals(field.getType())) throw exception(DIRECTOR_FORM_TEMPLATE_INVALID);
            field.setSystemField(systemType != null);
            boolean enumField = ENUM_TYPES.contains(field.getType());
            if (enumField && (field.getDictType() == null || field.getDictType().isBlank())) throw exception(DIRECTOR_FORM_TEMPLATE_INVALID);
            if (enumField) {
                List<DictDataRespDTO> options;
                try { options = dictDataApi.getDictDataList(field.getDictType()); }
                catch (RuntimeException ex) { throw exception(DIRECTOR_FORM_TEMPLATE_INVALID); }
                if (options == null || options.stream().noneMatch(row -> row.getStatus() == null || row.getStatus() == 0)) {
                    throw exception(DIRECTOR_FORM_TEMPLATE_INVALID);
                }
            }
            if (Set.of("multi_select", "checkbox_group").contains(field.getType())) field.setMultiple(true);
            if (Set.of("select", "radio").contains(field.getType())) field.setMultiple(false);
        }
        if (!keys.containsAll(system.keySet())) throw exception(DIRECTOR_FORM_TEMPLATE_INVALID);
        return source.stream().sorted(Comparator.comparing(DirectorFormTemplateVO.Field::getSort)
                .thenComparing(DirectorFormTemplateVO.Field::getKey)).toList();
    }

    private DirectorFormTemplateVO.TemplateResp convert(DirectorFormTemplateDO template) {
        DirectorFormTemplateVO.TemplateResp result = new DirectorFormTemplateVO.TemplateResp();
        result.setId(template.getId()); result.setScene(template.getScene()); result.setTemplateCode(template.getTemplateCode());
        result.setName(template.getName()); result.setDefaultTemplate(template.getDefaultTemplate()); result.setStatus(template.getStatus());
        result.setVersion(template.getVersion());
        List<DirectorFormTemplateVO.VersionResp> versions = versionMapper.selectByTemplate(template.getId()).stream().map(this::convert).toList();
        result.setVersions(versions); result.setDraft(versions.stream().filter(row -> "draft".equals(row.getStatus())).findFirst().orElse(null));
        result.setPublished(versions.stream().filter(row -> Objects.equals(row.getId(), template.getPublishedVersionId())).findFirst().orElse(null));
        return result;
    }

    private DirectorFormTemplateVO.VersionResp convert(DirectorFormTemplateVersionDO version) {
        DirectorFormTemplateVO.VersionResp result = new DirectorFormTemplateVO.VersionResp();
        result.setId(version.getId()); result.setTemplateId(version.getTemplateId()); result.setVersionNo(version.getVersionNo());
        result.setStatus(version.getStatus()); result.setFields(JsonUtils.parseArray(version.getFieldsJson(), DirectorFormTemplateVO.Field.class));
        result.setPublishedByUserId(version.getPublishedByUserId()); result.setPublishedAt(version.getPublishedAt()); result.setVersion(version.getVersion());
        return result;
    }

    private DirectorFormTemplateDO requireTemplate(Long id, String scene) {
        requireScene(scene);
        DirectorFormTemplateDO template = templateMapper.selectById(id);
        if (template == null || !scene.equals(template.getScene())) throw exception(DIRECTOR_FORM_TEMPLATE_NOT_EXISTS);
        return template;
    }
    private void requireScene(String scene) {
        if (!Set.of(SCENE_INTERVIEW, SCENE_POSITIONING).contains(scene)) throw exception(DIRECTOR_FORM_TEMPLATE_INVALID);
    }
    private void clearDefault(String scene, Long except) {
        LambdaUpdateWrapper<DirectorFormTemplateDO> update = new LambdaUpdateWrapper<DirectorFormTemplateDO>()
                .eq(DirectorFormTemplateDO::getScene, scene).eq(DirectorFormTemplateDO::getDefaultTemplate, true)
                .set(DirectorFormTemplateDO::getDefaultTemplate, false);
        if (except != null) update.ne(DirectorFormTemplateDO::getId, except);
        templateMapper.update(null, update);
    }
    private boolean empty(Object value) { return value == null || value instanceof String text && text.isBlank()
            || value instanceof Collection<?> values && values.isEmpty(); }
    private static Map<String, String> orderedMap(String[][] values) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String[] value : values) result.put(value[0], value[1]);
        return Collections.unmodifiableMap(result);
    }
}
