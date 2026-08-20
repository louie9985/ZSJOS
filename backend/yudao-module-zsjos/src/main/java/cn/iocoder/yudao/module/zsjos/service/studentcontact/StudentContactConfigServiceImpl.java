package cn.iocoder.yudao.module.zsjos.service.studentcontact;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.StudentContactConfigRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.StudentContactConfigSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentContactConfigVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.StudentContactConfigCommandDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.StudentContactConfigCommandMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.StudentContactConfigVersionMapper;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.STUDENT_CONTACT_CONFIG_INVALID;

@Service
public class StudentContactConfigServiceImpl implements StudentContactConfigService {
    private static final Set<String> TABS = Set.of("first-contact", "study-plan", "contacts");
    @Resource private StudentContactConfigVersionMapper mapper;
    @Resource private StudentContactConfigCommandMapper commandMapper;

    @Override public StudentContactConfigRespVO get() {
        StudentContactConfigRespVO result = new StudentContactConfigRespVO();
        result.setPublished(convert(mapper.selectPublished()));
        result.setDraft(convert(mapper.selectDraft()));
        return result;
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public Long copyDraft(Long publishedId, Integer publishedVersion, String idempotencyKey) {
        String fingerprint = fingerprint(Map.of("publishedId", publishedId, "publishedVersion", publishedVersion));
        CommandClaim claim = claimCommand("copy", idempotencyKey, publishedId, publishedVersion, fingerprint);
        if (!claim.created()) return claim.command().getResultConfigId();
        StudentContactConfigVersionDO published = mapper.selectPublishedByIdForUpdate(
                publishedId, TenantContextHolder.getRequiredTenantId());
        if (published == null || !published.getVersion().equals(publishedVersion)) {
            throw exception(STUDENT_CONTACT_CONFIG_INVALID);
        }
        StudentContactConfigVersionDO draft = mapper.selectDraft();
        if (draft != null) {
            if (commandMapper.updateResult(claim.command().getId(), draft.getId()) != 1) {
                throw exception(STUDENT_CONTACT_CONFIG_INVALID);
            }
            return draft.getId();
        }
        StudentContactConfigVersionDO copy = new StudentContactConfigVersionDO();
        copy.setVersionNo(published.getVersionNo() + 1); copy.setStatus("draft");
        copy.setFirstContactTimeoutMinutes(published.getFirstContactTimeoutMinutes());
        copy.setStudyPlanTimeoutMinutes(published.getStudyPlanTimeoutMinutes());
        copy.setChecklistJson(published.getChecklistJson()); copy.setQuickNotesJson(published.getQuickNotesJson());
        copy.setCollaboratorTabsJson(published.getCollaboratorTabsJson()); copy.setVersion(0);
        mapper.insert(copy);
        if (commandMapper.updateResult(claim.command().getId(), copy.getId()) != 1) {
            throw exception(STUDENT_CONTACT_CONFIG_INVALID);
        }
        return copy.getId();
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void updateDraft(StudentContactConfigSaveReqVO request) {
        validate(request);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("firstContactTimeoutMinutes", request.getFirstContactTimeoutMinutes());
        payload.put("studyPlanTimeoutMinutes", request.getStudyPlanTimeoutMinutes());
        payload.put("checklist", request.getChecklist());
        payload.put("quickNotes", request.getQuickNotes());
        payload.put("collaboratorTabs", new TreeMap<>(request.getCollaboratorTabs()));
        CommandClaim claim = claimCommand("update", request.getIdempotencyKey(), request.getId(),
                request.getVersion(), fingerprint(payload));
        if (!claim.created()) return;
        StudentContactConfigVersionDO draft = new StudentContactConfigVersionDO();
        draft.setId(request.getId());
        draft.setFirstContactTimeoutMinutes(request.getFirstContactTimeoutMinutes());
        draft.setStudyPlanTimeoutMinutes(request.getStudyPlanTimeoutMinutes());
        draft.setChecklistJson(JsonUtils.toJsonString(request.getChecklist()));
        draft.setQuickNotesJson(JsonUtils.toJsonString(request.getQuickNotes()));
        draft.setCollaboratorTabsJson(JsonUtils.toJsonString(request.getCollaboratorTabs()));
        if (mapper.updateDraft(draft, request.getVersion()) != 1) {
            throw exception(STUDENT_CONTACT_CONFIG_INVALID);
        }
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void publish(Long id, Integer version, String idempotencyKey) {
        String fingerprint = fingerprint(Map.of("id", id, "version", version));
        CommandClaim claim = claimCommand("publish", idempotencyKey, id, version, fingerprint);
        if (!claim.created()) return;
        if (mapper.publishDraft(id, version) != 1) {
            throw exception(STUDENT_CONTACT_CONFIG_INVALID);
        }
        mapper.update(null, new LambdaUpdateWrapper<StudentContactConfigVersionDO>()
                .eq(StudentContactConfigVersionDO::getStatus, "published")
                .ne(StudentContactConfigVersionDO::getId, id)
                .set(StudentContactConfigVersionDO::getStatus, "archived"));
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

    private CommandClaim claimCommand(String operation, String idempotencyKey, Long configId,
                                      Integer expectedVersion, String requestFingerprint) {
        StudentContactConfigCommandDO command = new StudentContactConfigCommandDO();
        command.setOperation(operation); command.setIdempotencyKey(idempotencyKey); command.setConfigId(configId);
        command.setExpectedVersion(expectedVersion); command.setRequestFingerprint(requestFingerprint);
        command.setResultConfigId(configId);
        try {
            commandMapper.insert(command);
            return new CommandClaim(command, true);
        } catch (DuplicateKeyException duplicate) {
            StudentContactConfigCommandDO existing = commandMapper.selectByIdempotencyKey(idempotencyKey);
            validateReplay(existing, operation, configId, expectedVersion, requestFingerprint);
            return new CommandClaim(existing, false);
        }
    }

    private void validateReplay(StudentContactConfigCommandDO command, String operation,
                                Long configId, Integer expectedVersion, String requestFingerprint) {
        if (command == null || !operation.equals(command.getOperation()) || !configId.equals(command.getConfigId())
                || !expectedVersion.equals(command.getExpectedVersion())
                || !requestFingerprint.equals(command.getRequestFingerprint())) {
            throw exception(STUDENT_CONTACT_CONFIG_INVALID);
        }
    }

    private String fingerprint(Object value) { return DigestUtil.sha256Hex(JsonUtils.toJsonString(value)); }

    private record CommandClaim(StudentContactConfigCommandDO command, boolean created) {}

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
