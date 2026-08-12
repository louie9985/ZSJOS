package cn.iocoder.yudao.module.zsjos.service.workplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workplan.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.WorkPlanConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.WORK_PLAN_FIELD_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.WORK_PLAN_PERIOD_INVALID;

@Service
public class WorkPlanTemplateServiceImpl implements WorkPlanTemplateService {
    private static final List<String> FIELD_TYPES = List.of("text", "textarea", "integer", "decimal", "money", "date", "datetime",
            "single_select", "multi_select", "user", "dept", "dict", "attachment", "link");

    @Resource private WorkPlanTypeMapper typeMapper;
    @Resource private WorkPlanTemplateMapper templateMapper;
    @Resource private WorkPlanTemplateVersionMapper versionMapper;
    @Resource private WorkPlanTemplateFieldMapper fieldMapper;
    @Resource private WorkPlanTemplateItemMapper taskMapper;
    @Resource private WorkPlanTemplateScopeMapper scopeMapper;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;

    @Override
    public List<WorkPlanTypeRespVO> getTypes() {
        return typeMapper.selectEnabledList().stream().map(this::convertType).toList();
    }

    @Override
    public Long createType(WorkPlanTypeSaveReqVO reqVO) {
        WorkPlanTypeDO type = new WorkPlanTypeDO().setCode(systemCode("type")).setName(reqVO.getName())
                .setDescription(reqVO.getDescription()).setSort(reqVO.getSort() == null ? 0 : reqVO.getSort()).setStatus(0);
        typeMapper.insert(type);
        return type.getId();
    }

    @Override
    public void updateType(Long id, WorkPlanTypeSaveReqVO reqVO) {
        WorkPlanTypeDO type = requireType(id);
        type.setName(reqVO.getName()).setDescription(reqVO.getDescription()).setSort(reqVO.getSort() == null ? 0 : reqVO.getSort());
        typeMapper.updateById(type);
    }

    @Override
    public List<WorkPlanTemplateRespVO> getTemplates(Long typeId) {
        List<WorkPlanTemplateDO> templates = typeId == null ? templateMapper.selectList()
                : templateMapper.selectList(new LambdaQueryWrapperX<WorkPlanTemplateDO>().eq(WorkPlanTemplateDO::getTypeId, typeId));
        return templates.stream().map(template -> convertTemplate(template, true)).toList();
    }

    @Override
    public List<WorkPlanTemplateRespVO> getAvailableTemplates(Long userId) {
        Long deptId = Optional.ofNullable(adminUserApi.getUser(userId)).map(user -> user.getDeptId()).orElse(null);
        return templateMapper.selectList(new LambdaQueryWrapperX<WorkPlanTemplateDO>()
                        .eq(WorkPlanTemplateDO::getStatus, "published").orderByAsc(WorkPlanTemplateDO::getName))
                .stream().filter(template -> isApplicable(template.getId(), deptId))
                .map(template -> convertTemplate(template, false)).toList();
    }

    @Override
    public WorkPlanTemplateRespVO getTemplate(Long id) {
        return convertTemplate(requireTemplate(id), true);
    }

    @Override
    @Transactional
    public Long createTemplate(WorkPlanTemplateSaveReqVO reqVO, Long userId) {
        validateTemplate(reqVO);
        WorkPlanTemplateDO template = new WorkPlanTemplateDO().setTypeId(reqVO.getTypeId()).setCode(systemCode("template"))
                .setName(reqVO.getName()).setDescription(reqVO.getDescription()).setStatus("draft").setCurrentVersionNo(1);
        templateMapper.insert(template);
        WorkPlanTemplateVersionDO version = new WorkPlanTemplateVersionDO().setTemplateId(template.getId())
                .setVersionNo(1).setStatus("draft").setPeriodMode(reqVO.getPeriodMode());
        versionMapper.insert(version);
        saveVersionConfiguration(template.getId(), version.getId(), reqVO, Map.of());
        return template.getId();
    }

    @Override
    @Transactional
    public void updateTemplate(Long id, WorkPlanTemplateSaveReqVO reqVO, Long userId) {
        validateTemplate(reqVO);
        WorkPlanTemplateDO template = requireTemplate(id);
        WorkPlanTemplateVersionDO draft = requireDraft(id);
        Map<Long, String> existingKeys = fieldMapper.selectListByVersionId(draft.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(WorkPlanTemplateFieldDO::getId, WorkPlanTemplateFieldDO::getFieldKey));
        template.setTypeId(reqVO.getTypeId()).setName(reqVO.getName()).setDescription(reqVO.getDescription());
        templateMapper.updateById(template);
        draft.setPeriodMode(reqVO.getPeriodMode());
        versionMapper.updateById(draft);
        fieldMapper.deleteHardByVersionId(draft.getId());
        taskMapper.deleteHardByVersionId(draft.getId());
        scopeMapper.deleteHardByTemplateId(id);
        saveVersionConfiguration(id, draft.getId(), reqVO, existingKeys);
    }

    @Override
    @Transactional
    public Long copyTemplateVersion(Long id, Long userId) {
        WorkPlanTemplateDO template = requireTemplate(id);
        if (versionMapper.selectOne(new LambdaQueryWrapperX<WorkPlanTemplateVersionDO>()
                .eq(WorkPlanTemplateVersionDO::getTemplateId, id).eq(WorkPlanTemplateVersionDO::getStatus, "draft").last("LIMIT 1")) != null) {
            throw exception(WORK_PLAN_FIELD_INVALID);
        }
        WorkPlanTemplateVersionDO source = versionMapper.selectPublished(id);
        if (source == null) throw exception(WORK_PLAN_FIELD_INVALID);
        int nextVersion = versionMapper.selectListByTemplateId(id).stream().mapToInt(WorkPlanTemplateVersionDO::getVersionNo).max().orElse(0) + 1;
        WorkPlanTemplateVersionDO draft = new WorkPlanTemplateVersionDO().setTemplateId(id).setVersionNo(nextVersion)
                .setStatus("draft").setPeriodMode(source.getPeriodMode());
        versionMapper.insert(draft);
        fieldMapper.selectListByVersionId(source.getId()).forEach(field -> fieldMapper.insert(copyField(field, draft.getId())));
        taskMapper.selectListByVersionId(source.getId()).forEach(task -> taskMapper.insert(copyTask(task, draft.getId())));
        template.setCurrentVersionNo(nextVersion);
        templateMapper.updateById(template);
        return draft.getId();
    }

    @Override
    @Transactional
    public void publishTemplate(Long id, Long userId) {
        WorkPlanTemplateDO template = requireTemplate(id);
        WorkPlanTemplateVersionDO version = requireDraft(id);
        version.setStatus("published").setPublishedAt(LocalDateTime.now());
        versionMapper.updateById(version);
        template.setStatus("published").setCurrentVersionNo(version.getVersionNo());
        templateMapper.updateById(template);
    }

    @Override
    public void disableTemplate(Long id, Long userId) {
        WorkPlanTemplateDO template = requireTemplate(id);
        template.setStatus("disabled");
        templateMapper.updateById(template);
    }

    private void validateTemplate(WorkPlanTemplateSaveReqVO reqVO) {
        requireType(reqVO.getTypeId());
        if (!PERIOD_TYPES.contains(reqVO.getPeriodMode())) throw exception(WORK_PLAN_PERIOD_INVALID);
        List<WorkPlanTemplateFieldSaveReqVO> fields = reqVO.getFields() == null ? List.of() : reqVO.getFields();
        Set<String> clientKeys = new HashSet<>();
        for (WorkPlanTemplateFieldSaveReqVO field : fields) {
            if (!FIELD_SECTIONS.contains(field.getSection()) || !FIELD_TYPES.contains(field.getFieldType())) throw exception(WORK_PLAN_FIELD_INVALID);
            if (field.getFieldKey() != null && !field.getFieldKey().isBlank() && !clientKeys.add(field.getFieldKey())) throw exception(WORK_PLAN_FIELD_INVALID);
            if (Boolean.TRUE.equals(field.getFilterable()) && (!SECTION_PLAN.equals(field.getSection()) || "attachment".equals(field.getFieldType()))) {
                throw exception(WORK_PLAN_FIELD_INVALID);
            }
            validateOptions(field);
        }
        if (reqVO.getApplicableDeptIds() != null && !reqVO.getApplicableDeptIds().isEmpty()) {
            deptApi.validateDeptList(reqVO.getApplicableDeptIds());
        }
    }

    private void validateOptions(WorkPlanTemplateFieldSaveReqVO field) {
        try {
            if (Set.of("single_select", "multi_select").contains(field.getFieldType())
                    && (field.getOptionsJson() == null || JsonUtils.parseArray(field.getOptionsJson(), Object.class).isEmpty())) {
                throw exception(WORK_PLAN_FIELD_INVALID);
            }
            if ("dict".equals(field.getFieldType())) {
                @SuppressWarnings("unchecked") Map<String, Object> options = JsonUtils.parseObject(field.getOptionsJson(), Map.class);
                if (options == null || String.valueOf(options.getOrDefault("dictType", "")).isBlank()) throw exception(WORK_PLAN_FIELD_INVALID);
            }
        } catch (RuntimeException ex) {
            throw exception(WORK_PLAN_FIELD_INVALID);
        }
    }

    private void saveVersionConfiguration(Long templateId, Long versionId, WorkPlanTemplateSaveReqVO reqVO,
                                          Map<Long, String> existingKeys) {
        List<WorkPlanTemplateFieldSaveReqVO> fields = reqVO.getFields() == null ? List.of() : reqVO.getFields();
        Set<String> used = new HashSet<>();
        for (WorkPlanTemplateFieldSaveReqVO request : fields) {
            String key = request.getId() == null ? request.getFieldKey() : existingKeys.get(request.getId());
            if (key == null || key.isBlank()) key = fieldKey();
            if (!used.add(key)) throw exception(WORK_PLAN_FIELD_INVALID);
            fieldMapper.insert(new WorkPlanTemplateFieldDO().setTemplateVersionId(versionId).setFieldKey(key)
                    .setLabel(request.getLabel()).setSection(request.getSection()).setFieldType(request.getFieldType())
                    .setRequired(Boolean.TRUE.equals(request.getRequired())).setUnit(request.getUnit()).setPlaceholder(request.getPlaceholder())
                    .setFilterable(Boolean.TRUE.equals(request.getFilterable())).setExportable(!Boolean.FALSE.equals(request.getExportable()))
                    .setOptionsJson(request.getOptionsJson()).setDefaultValueJson(request.getDefaultValueJson())
                    .setSort(request.getSort() == null ? 0 : request.getSort()));
        }
        List<WorkPlanTemplateItemSaveReqVO> tasks = reqVO.getPresetItems() == null ? List.of() : reqVO.getPresetItems();
        for (WorkPlanTemplateItemSaveReqVO request : tasks) {
            taskMapper.insert(new WorkPlanTemplateItemDO().setTemplateVersionId(versionId).setTitle(request.getTitle())
                    .setDescription(request.getDescription()).setDeliverableRequirement(request.getDeliverableRequirement())
                    .setDueOffsetDays(request.getDueOffsetDays()).setDueOffsetBasis(request.getDueOffsetBasis())
                    .setConfirmationRequired(Boolean.TRUE.equals(request.getConfirmationRequired()))
                    .setSort(request.getSort() == null ? 0 : request.getSort()));
        }
        List<Long> deptIds = reqVO.getApplicableDeptIds() == null ? List.of() : reqVO.getApplicableDeptIds().stream().distinct().toList();
        if (deptIds.isEmpty()) scopeMapper.insert(new WorkPlanTemplateScopeDO().setTemplateId(templateId).setDeptId(null).setIncludeChildren(true));
        else deptIds.forEach(deptId -> scopeMapper.insert(new WorkPlanTemplateScopeDO().setTemplateId(templateId).setDeptId(deptId)
                .setIncludeChildren(!Boolean.FALSE.equals(reqVO.getIncludeChildDepartments()))));
    }

    private WorkPlanTemplateRespVO convertTemplate(WorkPlanTemplateDO template, boolean preferDraft) {
        List<WorkPlanTemplateVersionDO> versions = versionMapper.selectListByTemplateId(template.getId());
        WorkPlanTemplateVersionDO version = preferDraft ? versions.stream().filter(item -> "draft".equals(item.getStatus())).findFirst().orElse(null) : null;
        if (version == null) version = versions.stream().filter(item -> "published".equals(item.getStatus()))
                .max(Comparator.comparing(WorkPlanTemplateVersionDO::getVersionNo)).orElse(null);
        WorkPlanTemplateRespVO response = new WorkPlanTemplateRespVO().setId(template.getId()).setTypeId(template.getTypeId())
                .setCode(template.getCode()).setName(template.getName()).setDescription(template.getDescription())
                .setStatus(template.getStatus()).setCurrentVersionNo(template.getCurrentVersionNo());
        if (version != null) {
            response.setVersionId(version.getId()).setVersionStatus(version.getStatus()).setPeriodMode(version.getPeriodMode())
                    .setFields(fieldMapper.selectListByVersionId(version.getId()).stream().map(this::convertField).toList())
                    .setPresetItems(taskMapper.selectListByVersionId(version.getId()).stream().map(this::convertTask).toList());
        }
        List<WorkPlanTemplateScopeDO> scopes = scopeMapper.selectListByTemplateId(template.getId());
        response.setApplicableDeptIds(scopes.stream().map(WorkPlanTemplateScopeDO::getDeptId).filter(Objects::nonNull).toList());
        response.setIncludeChildDepartments(scopes.stream().allMatch(scope -> Boolean.TRUE.equals(scope.getIncludeChildren())));
        return response;
    }

    private WorkPlanTemplateFieldSaveReqVO convertField(WorkPlanTemplateFieldDO field) {
        return new WorkPlanTemplateFieldSaveReqVO().setId(field.getId()).setFieldKey(field.getFieldKey()).setLabel(field.getLabel())
                .setSection(field.getSection()).setFieldType(field.getFieldType()).setRequired(field.getRequired())
                .setUnit(field.getUnit()).setPlaceholder(field.getPlaceholder()).setFilterable(field.getFilterable())
                .setExportable(field.getExportable()).setOptionsJson(field.getOptionsJson())
                .setDefaultValueJson(field.getDefaultValueJson()).setSort(field.getSort());
    }

    private WorkPlanTemplateItemSaveReqVO convertTask(WorkPlanTemplateItemDO task) {
        return new WorkPlanTemplateItemSaveReqVO().setTitle(task.getTitle()).setDescription(task.getDescription())
                .setDeliverableRequirement(task.getDeliverableRequirement()).setDueOffsetDays(task.getDueOffsetDays())
                .setDueOffsetBasis(task.getDueOffsetBasis()).setConfirmationRequired(task.getConfirmationRequired()).setSort(task.getSort());
    }

    private WorkPlanTemplateFieldDO copyField(WorkPlanTemplateFieldDO field, Long versionId) {
        return new WorkPlanTemplateFieldDO().setTemplateVersionId(versionId).setFieldKey(field.getFieldKey()).setLabel(field.getLabel())
                .setSection(field.getSection()).setFieldType(field.getFieldType()).setRequired(field.getRequired())
                .setUnit(field.getUnit()).setPlaceholder(field.getPlaceholder()).setFilterable(field.getFilterable())
                .setExportable(field.getExportable()).setOptionsJson(field.getOptionsJson())
                .setDefaultValueJson(field.getDefaultValueJson()).setSort(field.getSort());
    }

    private WorkPlanTemplateItemDO copyTask(WorkPlanTemplateItemDO task, Long versionId) {
        return new WorkPlanTemplateItemDO().setTemplateVersionId(versionId).setTitle(task.getTitle())
                .setDescription(task.getDescription()).setDeliverableRequirement(task.getDeliverableRequirement())
                .setDueOffsetDays(task.getDueOffsetDays()).setDueOffsetBasis(task.getDueOffsetBasis())
                .setConfirmationRequired(task.getConfirmationRequired()).setSort(task.getSort());
    }

    private boolean isApplicable(Long templateId, Long userDeptId) {
        List<WorkPlanTemplateScopeDO> scopes = scopeMapper.selectListByTemplateId(templateId);
        if (scopes.isEmpty() || scopes.stream().anyMatch(scope -> scope.getDeptId() == null)) return true;
        if (userDeptId == null) return false;
        return scopes.stream().anyMatch(scope -> Objects.equals(scope.getDeptId(), userDeptId)
                || Boolean.TRUE.equals(scope.getIncludeChildren()) && deptApi.getChildDeptList(scope.getDeptId()).stream()
                .anyMatch(dept -> Objects.equals(dept.getId(), userDeptId)));
    }

    private WorkPlanTypeDO requireType(Long id) {
        WorkPlanTypeDO type = typeMapper.selectById(id);
        if (type == null) throw exception(WORK_PLAN_PERIOD_INVALID);
        return type;
    }

    private WorkPlanTemplateDO requireTemplate(Long id) {
        WorkPlanTemplateDO template = templateMapper.selectById(id);
        if (template == null) throw exception(WORK_PLAN_PERIOD_INVALID);
        return template;
    }

    private WorkPlanTemplateVersionDO requireDraft(Long templateId) {
        WorkPlanTemplateVersionDO draft = versionMapper.selectOne(new LambdaQueryWrapperX<WorkPlanTemplateVersionDO>()
                .eq(WorkPlanTemplateVersionDO::getTemplateId, templateId).eq(WorkPlanTemplateVersionDO::getStatus, "draft")
                .orderByDesc(WorkPlanTemplateVersionDO::getVersionNo).last("LIMIT 1"));
        if (draft == null) throw exception(WORK_PLAN_FIELD_INVALID);
        return draft;
    }

    private WorkPlanTypeRespVO convertType(WorkPlanTypeDO source) {
        return new WorkPlanTypeRespVO().setId(source.getId()).setCode(source.getCode()).setName(source.getName())
                .setDescription(source.getDescription()).setStatus(source.getStatus()).setSort(source.getSort());
    }

    private String systemCode(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private String fieldKey() {
        return "f_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }
}
