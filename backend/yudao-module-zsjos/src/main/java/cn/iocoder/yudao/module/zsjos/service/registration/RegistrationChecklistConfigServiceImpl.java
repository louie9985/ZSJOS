package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.RegistrationChecklistConfigRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.RegistrationChecklistDraftSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationChecklistTemplateDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationChecklistTemplateItemDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationChecklistVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.RegistrationRouteOptionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationChecklistTemplateItemMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationChecklistTemplateMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationChecklistVersionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.RegistrationRouteOptionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.REGISTRATION_CHECKLIST_CONFIG_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.REGISTRATION_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.zsjos.service.registration.RegistrationConstants.*;

@Service
public class RegistrationChecklistConfigServiceImpl implements RegistrationChecklistConfigService {
    @Resource private RegistrationChecklistTemplateMapper templateMapper;
    @Resource private RegistrationChecklistVersionMapper versionMapper;
    @Resource private RegistrationChecklistTemplateItemMapper itemMapper;
    @Resource private RegistrationRouteOptionMapper routeOptionMapper;
    @Resource private DeptApi deptApi;

    @Override
    public RegistrationChecklistConfigRespVO getConfig() {
        RegistrationChecklistTemplateDO template = requireTemplate();
        RegistrationChecklistConfigRespVO result = new RegistrationChecklistConfigRespVO();
        result.setTemplateId(template.getId());
        result.setTemplateVersion(template.getVersion());
        result.setPublished(convertVersion(template.getPublishedVersionId(), "published"));
        result.setDraft(convertVersion(template.getDraftVersionId(), "draft"));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long copyPublishedToDraft(Integer templateVersion) {
        RegistrationChecklistTemplateDO template = lockTemplate(templateVersion);
        RegistrationChecklistVersionDO existingDraft = template.getDraftVersionId() == null ? null
                : versionMapper.selectById(template.getDraftVersionId());
        if (existingDraft != null && "draft".equals(existingDraft.getStatus())) return existingDraft.getId();
        RegistrationChecklistVersionDO published = versionMapper.selectById(template.getPublishedVersionId());
        if (published == null) throw exception(REGISTRATION_CHECKLIST_CONFIG_INVALID);
        RegistrationChecklistVersionDO draft = new RegistrationChecklistVersionDO();
        draft.setTemplateId(template.getId());
        draft.setVersionNo(versionMapper.selectLatest(template.getId()).getVersionNo() + 1);
        draft.setStatus("draft");
        versionMapper.insert(draft);
        for (RegistrationChecklistTemplateItemDO source : itemMapper.selectByVersionId(published.getId())) {
            RegistrationChecklistTemplateItemDO copy = new RegistrationChecklistTemplateItemDO();
            copy.setVersionId(draft.getId()); copy.setItemKey(source.getItemKey()); copy.setItemType(source.getItemType());
            copy.setTitle(source.getTitle()); copy.setSort(source.getSort()); copy.setEnabled(source.getEnabled());
            copy.setSystemRequired(source.getSystemRequired()); copy.setAttachmentRequired(source.getAttachmentRequired());
            itemMapper.insert(copy);
        }
        for (RegistrationRouteOptionDO source : routeOptionMapper.selectByVersionId(published.getId())) {
            RegistrationRouteOptionDO copy = new RegistrationRouteOptionDO();
            copy.setVersionId(draft.getId()); copy.setOptionKey(source.getOptionKey());
            copy.setDepartmentId(source.getDepartmentId()); copy.setDepartmentNameSnapshot(source.getDepartmentNameSnapshot());
            copy.setAssigneeType(source.getAssigneeType()); copy.setSort(source.getSort());
            copy.setEnabled(source.getEnabled()); copy.setSystemRequired(source.getSystemRequired());
            routeOptionMapper.insert(copy);
        }
        template.setDraftVersionId(draft.getId()); template.setVersion(template.getVersion() + 1); templateMapper.updateById(template);
        return draft.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDraft(RegistrationChecklistDraftSaveReqVO reqVO) {
        RegistrationChecklistTemplateDO template = lockTemplate(reqVO.getTemplateVersion());
        RegistrationChecklistVersionDO draft = versionMapper.selectById(template.getDraftVersionId());
        if (draft == null || !"draft".equals(draft.getStatus())) throw exception(REGISTRATION_CHECKLIST_CONFIG_INVALID);
        long plannerCount = reqVO.getItems().stream().filter(item -> ITEM_KEY_STUDY_PLANNER.equals(item.getItemKey())).count();
        RegistrationChecklistDraftSaveReqVO.ItemReqVO planner = reqVO.getItems().stream()
                .filter(item -> ITEM_KEY_STUDY_PLANNER.equals(item.getItemKey())).findFirst().orElse(null);
        if (plannerCount != 1 || planner == null || !Boolean.TRUE.equals(planner.getEnabled())) {
            throw exception(REGISTRATION_CHECKLIST_CONFIG_INVALID);
        }
        if (reqVO.getItems().isEmpty() || reqVO.getItems().stream()
                .anyMatch(item -> item.getTitle() == null || item.getTitle().isBlank())) {
            throw exception(REGISTRATION_CHECKLIST_CONFIG_INVALID);
        }
        Set<String> requestedItemKeys = reqVO.getItems().stream()
                .map(RegistrationChecklistDraftSaveReqVO.ItemReqVO::getItemKey)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        long suppliedItemKeyCount = reqVO.getItems().stream().filter(item -> item.getItemKey() != null).count();
        if (requestedItemKeys.size() != suppliedItemKeyCount) {
            throw exception(REGISTRATION_CHECKLIST_CONFIG_INVALID);
        }
        if (reqVO.getRouteOptions() == null || reqVO.getRouteOptions().stream().noneMatch(item -> Boolean.TRUE.equals(item.getEnabled()))) {
            throw exception(REGISTRATION_CHECKLIST_CONFIG_INVALID);
        }
        Set<String> routeKeys = reqVO.getRouteOptions().stream().map(RegistrationChecklistDraftSaveReqVO.RouteOptionReqVO::getOptionKey)
                .collect(Collectors.toSet());
        if (routeKeys.size() != reqVO.getRouteOptions().size() || reqVO.getRouteOptions().stream()
                .anyMatch(item -> !Set.of(ASSIGNEE_STUDY_PLANNER, ASSIGNEE_CONTENT_DIRECTOR).contains(item.getAssigneeType()))) {
            throw exception(REGISTRATION_CHECKLIST_CONFIG_INVALID);
        }
        Set<Long> deptIds = reqVO.getRouteOptions().stream().map(RegistrationChecklistDraftSaveReqVO.RouteOptionReqVO::getDepartmentId)
                .collect(Collectors.toSet());
        if (deptIds.size() != reqVO.getRouteOptions().size()) {
            throw exception(REGISTRATION_CHECKLIST_CONFIG_INVALID);
        }
        deptApi.validateDeptList(deptIds);
        Map<Long, DeptRespDTO> departments = deptApi.getDeptList(deptIds).stream()
                .collect(Collectors.toMap(DeptRespDTO::getId, Function.identity()));
        if (departments.size() != deptIds.size()) throw exception(REGISTRATION_CHECKLIST_CONFIG_INVALID);
        itemMapper.deleteByVersionId(draft.getId());
        for (RegistrationChecklistDraftSaveReqVO.ItemReqVO requested : reqVO.getItems()) {
            RegistrationChecklistTemplateItemDO item = new RegistrationChecklistTemplateItemDO();
            item.setVersionId(draft.getId());
            boolean system = ITEM_KEY_STUDY_PLANNER.equals(requested.getItemKey());
            item.setItemKey(system ? ITEM_KEY_STUDY_PLANNER : normalizeKey(requested.getItemKey()));
            item.setItemType(system ? ITEM_TYPE_STUDY_PLANNER
                    : ITEM_TYPE_ATTACHMENT.equals(requested.getItemType()) ? ITEM_TYPE_ATTACHMENT : ITEM_TYPE_CHECKBOX);
            item.setTitle(system ? "配置学习规划师" : requested.getTitle().trim());
            item.setSort(requested.getSort()); item.setEnabled(requested.getEnabled()); item.setSystemRequired(system);
            item.setAttachmentRequired(ITEM_TYPE_ATTACHMENT.equals(item.getItemType())
                    && Boolean.TRUE.equals(requested.getAttachmentRequired()));
            itemMapper.insert(item);
        }
        routeOptionMapper.deleteByVersionId(draft.getId());
        for (RegistrationChecklistDraftSaveReqVO.RouteOptionReqVO requested : reqVO.getRouteOptions()) {
            DeptRespDTO department = departments.get(requested.getDepartmentId());
            RegistrationRouteOptionDO option = new RegistrationRouteOptionDO();
            option.setVersionId(draft.getId()); option.setOptionKey(requested.getOptionKey());
            option.setDepartmentId(department.getId()); option.setDepartmentNameSnapshot(department.getName());
            option.setAssigneeType(requested.getAssigneeType()); option.setSort(requested.getSort());
            option.setEnabled(requested.getEnabled()); option.setSystemRequired(Boolean.TRUE.equals(requested.getSystemRequired()));
            routeOptionMapper.insert(option);
        }
        template.setVersion(template.getVersion() + 1); templateMapper.updateById(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Integer templateVersion) {
        RegistrationChecklistTemplateDO template = lockTemplate(templateVersion);
        RegistrationChecklistVersionDO draft = versionMapper.selectById(template.getDraftVersionId());
        List<RegistrationChecklistTemplateItemDO> items = draft == null ? List.of()
                : itemMapper.selectByVersionId(draft.getId());
        long plannerCount = items.stream().filter(item -> ITEM_KEY_STUDY_PLANNER.equals(item.getItemKey())
                && ITEM_TYPE_STUDY_PLANNER.equals(item.getItemType())
                && Boolean.TRUE.equals(item.getEnabled())
                && Boolean.TRUE.equals(item.getSystemRequired())).count();
        List<RegistrationRouteOptionDO> routes = draft == null ? List.of() : routeOptionMapper.selectByVersionId(draft.getId());
        if (draft == null || !"draft".equals(draft.getStatus()) || items.isEmpty() || plannerCount != 1
                || routes.stream().noneMatch(item -> Boolean.TRUE.equals(item.getEnabled()))) {
            throw exception(REGISTRATION_CHECKLIST_CONFIG_INVALID);
        }
        draft.setStatus("published"); draft.setPublishedAt(LocalDateTime.now()); versionMapper.updateById(draft);
        if (templateMapper.publishDraft(template.getId(), draft.getId(), draft.getId(), template.getVersion()) != 1) {
            throw exception(REGISTRATION_VERSION_CONFLICT);
        }
    }

    private RegistrationChecklistTemplateDO requireTemplate() {
        RegistrationChecklistTemplateDO template = templateMapper.selectCurrent();
        if (template == null || template.getPublishedVersionId() == null) throw exception(REGISTRATION_CHECKLIST_CONFIG_INVALID);
        return template;
    }

    private RegistrationChecklistTemplateDO lockTemplate(Integer expectedVersion) {
        RegistrationChecklistTemplateDO current = requireTemplate();
        RegistrationChecklistTemplateDO locked = templateMapper.selectByIdForUpdate(current.getId(), TenantContextHolder.getRequiredTenantId());
        if (locked == null) throw exception(REGISTRATION_CHECKLIST_CONFIG_INVALID);
        if (!Objects.equals(locked.getVersion(), expectedVersion)) throw exception(REGISTRATION_VERSION_CONFLICT);
        return locked;
    }

    private RegistrationChecklistConfigRespVO.VersionVO convertVersion(Long versionId, String expectedStatus) {
        if (versionId == null) return null;
        RegistrationChecklistVersionDO version = versionMapper.selectById(versionId);
        if (version == null || !expectedStatus.equals(version.getStatus())) return null;
        RegistrationChecklistConfigRespVO.VersionVO result = new RegistrationChecklistConfigRespVO.VersionVO();
        result.setId(version.getId()); result.setVersionNo(version.getVersionNo()); result.setStatus(version.getStatus());
        result.setPublishedAt(version.getPublishedAt());
        result.setItems(itemMapper.selectByVersionId(versionId).stream().map(item -> {
            RegistrationChecklistConfigRespVO.ItemVO row = new RegistrationChecklistConfigRespVO.ItemVO();
            row.setId(item.getId()); row.setItemKey(item.getItemKey()); row.setItemType(item.getItemType());
            row.setTitle(item.getTitle()); row.setSort(item.getSort()); row.setEnabled(item.getEnabled());
            row.setSystemRequired(item.getSystemRequired()); row.setAttachmentRequired(item.getAttachmentRequired()); return row;
        }).toList());
        result.setRouteOptions(routeOptionMapper.selectByVersionId(versionId).stream().map(item -> {
            RegistrationChecklistConfigRespVO.RouteOptionVO row = new RegistrationChecklistConfigRespVO.RouteOptionVO();
            row.setId(item.getId()); row.setOptionKey(item.getOptionKey()); row.setDepartmentId(item.getDepartmentId());
            row.setDepartmentName(item.getDepartmentNameSnapshot()); row.setAssigneeType(item.getAssigneeType());
            row.setAssigneeTypeLabel(ASSIGNEE_STUDY_PLANNER.equals(item.getAssigneeType()) ? "学习规划师" : "编导");
            row.setSort(item.getSort()); row.setEnabled(item.getEnabled()); row.setSystemRequired(item.getSystemRequired());
            return row;
        }).toList());
        return result;
    }

    private String normalizeKey(String key) {
        if (key != null && key.matches("[a-z][a-z0-9_]{1,63}") && !ITEM_KEY_STUDY_PLANNER.equals(key)) return key;
        return "custom_" + UUID.randomUUID().toString().replace("-", "");
    }
}
