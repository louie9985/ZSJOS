package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.qualification.LeadJudgeValidReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class LeadSelfSourcedAutomationService {
    @Resource private PermissionApi permissionApi;
    @Resource private DictDataApi dictDataApi;
    @Resource private LeadFollowUpRuleService followUpRuleService;
    @Resource private LeadFollowUpService followUpService;
    @Resource private LeadQualificationService qualificationService;
    @Resource private LeadMapper leadMapper;
    @Resource private BusinessEventMapper eventMapper;
    @Resource private BusinessTaskCommandService taskCommandService;

    public void validate(LeadCreateReqVO request, Long userId, String sourceType) {
        String createPermission = switch (sourceType) {
            case SOURCE_SALES_SELF -> "zsjos:lead:self-sourced:create";
            case SOURCE_EDUCATION_SELF -> "zsjos:lead:education-self-sourced:create";
            default -> throw exception(LEAD_FOLLOW_UP_STATE_INVALID);
        };
        for (String permission : new String[]{createPermission, "zsjos:lead-follow-up:create", "zsjos:lead:qualify"}) {
            if (!permissionApi.hasAnyPermissions(userId, permission)) throw exception(LEAD_PERMISSION_DENIED);
        }
        if (request.getRemark() == null || request.getRemark().isBlank()) throw exception(LEAD_SELF_SOURCED_REMARK_REQUIRED);
        request.setRemark(request.getRemark().trim());
        if (request.getSelfSourcedNextFollowUpAt() != null
                && !request.getSelfSourcedNextFollowUpAt().isAfter(LocalDateTime.now(ZoneId.of("Asia/Shanghai")))) {
            throw exception(LEAD_FOLLOW_UP_TIME_INVALID);
        }
        requireDictionary(DICT_FOLLOW_UP_METHOD, LeadAutomaticGeneration.METHOD);
        requireDictionary(DICT_FOLLOW_UP_RESULT, LeadAutomaticGeneration.RESULT);
        followUpRuleService.requireEnabledRule();
    }

    private void requireDictionary(String type, String value) {
        if (dictDataApi.getDictDataList(type).stream().noneMatch(item -> value.equals(item.getValue())
                && CommonStatusEnum.ENABLE.getStatus().equals(item.getStatus())
                && item.getLabel() != null && !item.getLabel().isBlank())) throw exception(LEAD_FOLLOW_UP_DICT_INVALID);
    }

    /** Must join creation: a failed follow-up, qualification, or provenance write rolls back the new Lead. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    @cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission(bizType = "lead", bizId = "#leadId", action = "follow-up-create")
    public void complete(Long leadId, Long userId, LocalDateTime nextFollowUpAt) {
        var followUp = followUpService.createSelfSourcedAutomatic(leadId, userId, nextFollowUpAt);
        LeadDO lead = leadMapper.selectByIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        LeadJudgeValidReqVO command = new LeadJudgeValidReqVO();
        command.setLeadCategory(lead.getLeadCategory()); command.setRemark(lead.getRemark().trim());
        command.setIdempotencyKey(LeadAutomaticGeneration.qualificationKey(leadId, lead.getSourceType()));
        qualificationService.judgeValid(leadId, userId, command);
        Map<String, Object> provenance = new LinkedHashMap<>();
        String generationSource = LeadAutomaticGeneration.sourceForLead(lead.getSourceType());
        provenance.put(LeadAutomaticGeneration.FIELD, generationSource);
        provenance.put("followUpRecordId", followUp.getId());
        provenance.put("assignmentHistoryId", lead.getCurrentAssignmentHistoryId());
        provenance.put("roundNo", lead.getQualificationRoundNo());
        markEvent(EVENT_LEAD_FOLLOW_UP_RECORDED + ":" + followUp.getId(), provenance);
        markEvent(LeadAutomaticGeneration.qualificationEventKey(leadId, lead.getSourceType()), provenance);
        taskCommandService.markCompletedGenerationSource("lead-first-follow-up:" + lead.getCurrentAssignmentHistoryId(), generationSource);
        taskCommandService.markCompletedGenerationSource("lead-qualification:" + leadId + ":" + lead.getQualificationRoundNo(), generationSource);
    }

    @SuppressWarnings("unchecked")
    private void markEvent(String key, Map<String, Object> provenance) {
        var event = eventMapper.selectByIdempotencyKey(key);
        if (event == null) throw new IllegalStateException("Automatic Lead event missing: " + key);
        Map<String, Object> refs = event.getRelatedObjectRefs() == null ? new LinkedHashMap<>()
                : new LinkedHashMap<>(JsonUtils.parseObject(event.getRelatedObjectRefs(), Map.class));
        refs.putAll(provenance); event.setRelatedObjectRefs(JsonUtils.toJsonString(refs));
        if (eventMapper.updateById(event) != 1) throw new IllegalStateException("Automatic Lead event update failed");
    }
}
