package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.api.notify.NotifyActionType;
import cn.iocoder.yudao.module.system.api.notify.NotifyRuleApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyDefaultRuleReqDTO;
import cn.iocoder.yudao.module.system.api.tenant.dto.TenantCreatedEvent;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter.LeadInboxFilterConfigVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadInboxFilterSchemeDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadInboxFilterVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadInboxFilterSchemeMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadInboxFilterVersionMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.*;

@Component
public class LeadAgingPoolTenantInitializer {

    @Resource private NotifyRuleApi notifyRuleApi;
    @Resource private LeadInboxFilterSchemeMapper schemeMapper;
    @Resource private LeadInboxFilterVersionMapper versionMapper;

    @EventListener
    public void onTenantCreated(TenantCreatedEvent event) {
        TenantUtils.execute(event.getTenantId(), () -> {
            initializeFilterScheme();
            notifyRuleApi.initializeDefaultRules(defaultNotifyRules());
        });
    }

    private void initializeFilterScheme() {
        if (schemeMapper.selectByAudience(INBOX_AUDIENCE_AGING_POOL) != null) return;
        LeadInboxFilterConfigVO config = new LeadInboxFilterConfigVO();
        LeadInboxFilterConfigVO.GroupVO group = new LeadInboxFilterConfigVO.GroupVO();
        group.setKey("all"); group.setLabel("全部公海客资"); group.setSort(0); group.setEnabled(true);
        group.setSectionLabel("公海状态"); group.setConditions(List.of());
        group.setOptions(List.of(option("all", "全部", 0, null),
                option(AGING_POOL_WAITING_ASSIGNMENT, "待指派", 10, AGING_POOL_WAITING_ASSIGNMENT),
                option(AGING_POOL_ASSIGNED, "协同跟进中", 20, AGING_POOL_ASSIGNED),
                option(AGING_POOL_DEAL_PENDING, "成交审批中", 30, AGING_POOL_DEAL_PENDING)));
        config.setGroups(List.of(group));
        String json = JsonUtils.toJsonString(config);
        LocalDateTime now = LocalDateTime.now();
        LeadInboxFilterSchemeDO scheme = new LeadInboxFilterSchemeDO();
        scheme.setAudience(INBOX_AUDIENCE_AGING_POOL); scheme.setName("超期公海视角");
        scheme.setDraftConfigJson(json); scheme.setPublishedConfigJson(json); scheme.setPublishedVersion(1);
        scheme.setPublishedBy(0L); scheme.setPublishedAt(now); scheme.setVersion(0);
        schemeMapper.insert(scheme);
        LeadInboxFilterVersionDO version = new LeadInboxFilterVersionDO();
        version.setSchemeId(scheme.getId()); version.setVersionNo(1); version.setConfigJson(json);
        version.setPublishedBy(0L); version.setPublishedAt(now); versionMapper.insert(version);
    }

    private static LeadInboxFilterConfigVO.OptionVO option(String key, String label, int sort, String status) {
        LeadInboxFilterConfigVO.OptionVO option = new LeadInboxFilterConfigVO.OptionVO();
        option.setKey(key); option.setLabel(label); option.setSort(sort); option.setEnabled(true);
        if (status == null) option.setConditions(List.of());
        else {
            LeadInboxFilterConfigVO.ConditionVO condition = new LeadInboxFilterConfigVO.ConditionVO();
            condition.setField(INBOX_FILTER_FIELD_POOL_STATUS); condition.setValues(List.of(status));
            option.setConditions(List.of(condition));
        }
        return option;
    }

    private static List<NotifyDefaultRuleReqDTO> defaultNotifyRules() {
        return List.of(rule("超期公海-提前7天", AGING_POOL_REMINDER, "ZSJOS_AGING_POOL_REMINDER",
                        List.of("owner", "frozen_dept_leader"), "advance", 10080),
                rule("超期公海到期通知", AGING_POOL_DUE, "ZSJOS_AGING_POOL_DUE",
                        List.of("owner", "frozen_dept_leader"), null, null),
                rule("超期公海指派通知", AGING_POOL_ASSIGNED_NOTICE, "ZSJOS_AGING_POOL_ASSIGNED",
                        List.of("owner", "collaborator", "frozen_dept_leader"), null, null),
                rule("超期公海换派通知", AGING_POOL_REASSIGNED_NOTICE, "ZSJOS_AGING_POOL_REASSIGNED",
                        List.of("owner", "previous_collaborator", "collaborator", "frozen_dept_leader"), null, null),
                rule("超期公海待重派通知", AGING_POOL_REASSIGN_REQUIRED_NOTICE,
                        "ZSJOS_AGING_POOL_REASSIGN_REQUIRED",
                        List.of("owner", "previous_collaborator", "frozen_dept_leader"), null, null),
                rule("超期公海退出通知", AGING_POOL_EXITED_NOTICE, "ZSJOS_AGING_POOL_EXITED",
                        List.of("owner", "previous_collaborator", "frozen_dept_leader"), null, null));
    }

    private static NotifyDefaultRuleReqDTO rule(String name, String scene, String template,
                                                 List<String> roles, String stage, Integer offset) {
        return NotifyDefaultRuleReqDTO.builder().name(name).sceneCode(scene).templateCode(template)
                .recipientRoles(roles).actionType(NotifyActionType.BUSINESS_DETAIL)
                .timingStage(stage).timingOffsetMinutes(offset).build();
    }
}
