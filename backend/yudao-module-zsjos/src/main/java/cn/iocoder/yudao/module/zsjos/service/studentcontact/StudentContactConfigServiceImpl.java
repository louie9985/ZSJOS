package cn.iocoder.yudao.module.zsjos.service.studentcontact;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.StudentContactConfigRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.StudentContactConfigSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentContactConfigVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.StudentContactConfigVersionMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.STUDENT_CONTACT_CONFIG_INVALID;

@Service
public class StudentContactConfigServiceImpl implements StudentContactConfigService {
    private static final Set<String> TABS = Set.of("first-contact", "study-plan", "contacts");
    @Resource private StudentContactConfigVersionMapper mapper;

    @Override public StudentContactConfigRespVO get() {
        StudentContactConfigRespVO result = new StudentContactConfigRespVO();
        result.setPublished(convert(mapper.selectPublished()));
        result.setDraft(convert(mapper.selectDraft()));
        return result;
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public Long copyDraft() {
        StudentContactConfigVersionDO draft = mapper.selectDraft();
        if (draft != null) return draft.getId();
        StudentContactConfigVersionDO published = requirePublished();
        StudentContactConfigVersionDO copy = new StudentContactConfigVersionDO();
        copy.setVersionNo(published.getVersionNo() + 1); copy.setStatus("draft");
        copy.setFirstContactTimeoutMinutes(published.getFirstContactTimeoutMinutes());
        copy.setStudyPlanTimeoutMinutes(published.getStudyPlanTimeoutMinutes());
        copy.setChecklistJson(published.getChecklistJson()); copy.setQuickNotesJson(published.getQuickNotesJson());
        copy.setCollaboratorTabsJson(published.getCollaboratorTabsJson()); copy.setVersion(0);
        mapper.insert(copy); return copy.getId();
    }

    @Override public void updateDraft(StudentContactConfigSaveReqVO request) {
        validate(request);
        StudentContactConfigVersionDO draft = mapper.selectDraft();
        if (draft == null || !draft.getId().equals(request.getId()) || !draft.getVersion().equals(request.getVersion())) {
            throw exception(STUDENT_CONTACT_CONFIG_INVALID);
        }
        draft.setFirstContactTimeoutMinutes(request.getFirstContactTimeoutMinutes());
        draft.setStudyPlanTimeoutMinutes(request.getStudyPlanTimeoutMinutes());
        draft.setChecklistJson(JsonUtils.toJsonString(request.getChecklist()));
        draft.setQuickNotesJson(JsonUtils.toJsonString(request.getQuickNotes()));
        draft.setCollaboratorTabsJson(JsonUtils.toJsonString(request.getCollaboratorTabs()));
        draft.setVersion(draft.getVersion() + 1); mapper.updateById(draft);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void publish(Long id, Integer version) {
        StudentContactConfigVersionDO draft = mapper.selectDraft();
        if (draft == null || !draft.getId().equals(id) || !draft.getVersion().equals(version)) {
            throw exception(STUDENT_CONTACT_CONFIG_INVALID);
        }
        mapper.update(null, new LambdaUpdateWrapper<StudentContactConfigVersionDO>()
                .eq(StudentContactConfigVersionDO::getStatus, "published")
                .set(StudentContactConfigVersionDO::getStatus, "archived"));
        draft.setStatus("published"); draft.setVersion(draft.getVersion() + 1); mapper.updateById(draft);
    }

    @Override public StudentContactConfigVersionDO requirePublished() {
        StudentContactConfigVersionDO value = mapper.selectPublished();
        if (value == null) throw exception(STUDENT_CONTACT_CONFIG_INVALID);
        return value;
    }

    private void validate(StudentContactConfigSaveReqVO request) {
        long enabled = request.getChecklist().stream().filter(StudentContactConfigSaveReqVO.ChecklistItemReqVO::getEnabled).count();
        long unique = request.getChecklist().stream().map(StudentContactConfigSaveReqVO.ChecklistItemReqVO::getKey).distinct().count();
        if (enabled == 0 || unique != request.getChecklist().size()
                || !request.getCollaboratorTabs().keySet().equals(Set.of("content_director", "career_planner"))
                || request.getCollaboratorTabs().values().stream().flatMap(List::stream).anyMatch(tab -> !TABS.contains(tab))) {
            throw exception(STUDENT_CONTACT_CONFIG_INVALID);
        }
    }

    @SuppressWarnings("unchecked")
    private StudentContactConfigRespVO.VersionVO convert(StudentContactConfigVersionDO source) {
        if (source == null) return null;
        StudentContactConfigRespVO.VersionVO result = new StudentContactConfigRespVO.VersionVO();
        result.setId(source.getId()); result.setVersionNo(source.getVersionNo()); result.setVersion(source.getVersion());
        result.setFirstContactTimeoutMinutes(source.getFirstContactTimeoutMinutes());
        result.setStudyPlanTimeoutMinutes(source.getStudyPlanTimeoutMinutes());
        result.setChecklist(JsonUtils.parseArray(source.getChecklistJson(), StudentContactConfigRespVO.ChecklistItemVO.class));
        result.setQuickNotes(JsonUtils.parseArray(source.getQuickNotesJson(), String.class));
        Map<String, List<String>> tabs = JsonUtils.parseObject(source.getCollaboratorTabsJson(), Map.class);
        result.setCollaboratorTabs(tabs == null ? Map.of() : tabs); return result;
    }
}
