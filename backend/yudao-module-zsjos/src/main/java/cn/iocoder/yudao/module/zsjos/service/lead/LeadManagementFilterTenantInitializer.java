package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.system.api.tenant.dto.TenantCreatedEvent;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter.LeadInboxFilterConfigVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadInboxFilterSchemeDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadInboxFilterVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadInboxFilterSchemeMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadInboxFilterVersionMapper;
import jakarta.annotation.Resource;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

/**
 * 为新租户创建统一客资管理页的分级筛选方案。
 *
 * <p>既有租户由 V278 迁移补齐；新租户无法回放该迁移，因此在租户创建时按同一默认结构初始化，
 * 否则新租户的客资管理页不会出现任何服务端筛选行。已存在方案时不覆盖，保留管理员配置。
 */
@Component
public class LeadManagementFilterTenantInitializer {

    @Resource private LeadInboxFilterSchemeMapper schemeMapper;
    @Resource private LeadInboxFilterVersionMapper versionMapper;

    @EventListener
    public void onTenantCreated(TenantCreatedEvent event) {
        TenantUtils.execute(event.getTenantId(), this::initializeFilterScheme);
    }

    private void initializeFilterScheme() {
        if (schemeMapper.selectByAudience(INBOX_AUDIENCE_MANAGEMENT) != null) return;
        LeadInboxFilterConfigVO config = new LeadInboxFilterConfigVO();
        List<LeadInboxFilterConfigVO.ConditionVO> noConditions = List.of();
        config.setGroups(List.of(
                group("all", "全部客资", 0, noConditions, FULL_STAGE),
                group("pending_qualification", "待判定客资", 10,
                        condition(INBOX_FILTER_FIELD_STATUS, STATUS_SUBMITTED, STATUS_SUSPENDED),
                        PENDING_STAGE),
                group("valid", "有效客资", 20,
                        condition(INBOX_FILTER_FIELD_STATUS, STATUS_VALID, STATUS_WON), VALID_STAGE),
                group("invalid", "无效客资", 30,
                        condition(INBOX_FILTER_FIELD_STATUS, STATUS_INVALID), INVALID_STAGE),
                group("closed", "已关闭客资", 40,
                        condition(INBOX_FILTER_FIELD_STATUS, STATUS_CLOSED), CLOSED_STAGE),
                // 手动录入＝销售/教务自拓录，与 dispatch_mode=self 等价，不新增表字段。
                group("manual", "手动录入客资", 50,
                        condition(INBOX_FILTER_FIELD_SOURCE_TYPE,
                                SOURCE_SALES_SELF, SOURCE_EDUCATION_SELF), FULL_STAGE)));

        String json = JsonUtils.toJsonString(config);
        LocalDateTime now = LocalDateTime.now();
        LeadInboxFilterSchemeDO scheme = new LeadInboxFilterSchemeDO();
        scheme.setAudience(INBOX_AUDIENCE_MANAGEMENT);
        scheme.setName("统一客资管理视角");
        scheme.setDraftConfigJson(json);
        scheme.setPublishedConfigJson(json);
        scheme.setPublishedVersion(1);
        scheme.setPublishedBy(0L);
        scheme.setPublishedAt(now);
        scheme.setVersion(0);
        schemeMapper.insert(scheme);

        LeadInboxFilterVersionDO version = new LeadInboxFilterVersionDO();
        version.setSchemeId(scheme.getId());
        version.setVersionNo(1);
        version.setConfigJson(json);
        version.setPublishedBy(0L);
        version.setPublishedAt(now);
        versionMapper.insert(version);
    }

    private static final List<LeadInboxFilterConfigVO.OptionVO> FULL_STAGE = List.of(
            option("all", "全部", 0, null),
            option("unassigned", "待分配", 10, condition(INBOX_FILTER_FIELD_ASSIGNMENT_STATUS, ASSIGNMENT_UNASSIGNED)),
            option("pending_acceptance", "待接单", 20, condition(INBOX_FILTER_FIELD_ASSIGNMENT_STATUS, ASSIGNMENT_PENDING)),
            option("public_pool", "抢单池", 30, condition(INBOX_FILTER_FIELD_ASSIGNMENT_STATUS, ASSIGNMENT_PUBLIC_POOL)),
            option("recycle_pending", "回收待处理", 40, condition(INBOX_FILTER_FIELD_ASSIGNMENT_STATUS, ASSIGNMENT_RECYCLE_PENDING)),
            option("first_follow_pending", "待首跟", 50, condition(INBOX_FILTER_FIELD_HANDLING_STAGE, FOLLOW_UP_FIRST_PENDING)),
            option("qualification_pending", "待判定", 60, condition(INBOX_FILTER_FIELD_HANDLING_STAGE, QUALIFICATION_PENDING)),
            option("following", "正常推进", 70, condition(INBOX_FILTER_FIELD_SALES_PROGRESS, FOLLOW_UP_FOLLOWING)),
            option("deal_pending_approval", "成交待审核", 80, condition(INBOX_FILTER_FIELD_SALES_PROGRESS, FOLLOW_UP_DEAL_PENDING_APPROVAL)),
            option("won", "已成交", 90, condition(INBOX_FILTER_FIELD_SALES_PROGRESS, FOLLOW_UP_WON)),
            option("suspended", "已挂起", 100, condition(INBOX_FILTER_FIELD_STATUS, STATUS_SUSPENDED)));

    private static final List<LeadInboxFilterConfigVO.OptionVO> PENDING_STAGE = List.of(
            option("all", "全部", 0, null),
            option("first_follow_pending", "待首跟", 10, condition(INBOX_FILTER_FIELD_HANDLING_STAGE, FOLLOW_UP_FIRST_PENDING)),
            option("qualification_pending", "待判定", 20, condition(INBOX_FILTER_FIELD_HANDLING_STAGE, QUALIFICATION_PENDING)),
            option("suspended", "已挂起", 30, condition(INBOX_FILTER_FIELD_STATUS, STATUS_SUSPENDED)));

    private static final List<LeadInboxFilterConfigVO.OptionVO> VALID_STAGE = List.of(
            option("all", "全部", 0, null),
            option("following", "正常推进", 10, condition(INBOX_FILTER_FIELD_SALES_PROGRESS, FOLLOW_UP_FOLLOWING)),
            option("deal_pending_approval", "成交待审核", 20, condition(INBOX_FILTER_FIELD_SALES_PROGRESS, FOLLOW_UP_DEAL_PENDING_APPROVAL)),
            option("won", "已成交", 30, condition(INBOX_FILTER_FIELD_SALES_PROGRESS, FOLLOW_UP_WON)));

    private static final List<LeadInboxFilterConfigVO.OptionVO> INVALID_STAGE = List.of(
            option("all", "全部", 0, null),
            option("invalid", "已判无效", 10, condition(INBOX_FILTER_FIELD_STATUS, STATUS_INVALID)));

    private static final List<LeadInboxFilterConfigVO.OptionVO> CLOSED_STAGE = List.of(
            option("all", "全部", 0, null),
            option("closed", "已关闭", 10, condition(INBOX_FILTER_FIELD_STATUS, STATUS_CLOSED)));

    private static final List<LeadInboxFilterConfigVO.OptionVO> QUICK_CONDITION = List.of(
            option("all", "全部", 0, null),
            option("today", "今日待跟进", 10, condition(INBOX_FILTER_FIELD_FOLLOW_UP_CONDITION, FOLLOW_UP_CONDITION_TODAY)),
            option("overdue", "跟进已逾期", 20, condition(INBOX_FILTER_FIELD_FOLLOW_UP_CONDITION, FOLLOW_UP_CONDITION_OVERDUE)),
            option("transferred_pending", "有效转派待跟进", 30,
                    condition(INBOX_FILTER_FIELD_FOLLOW_UP_CONDITION, FOLLOW_UP_CONDITION_TRANSFERRED_PENDING)));

    private static LeadInboxFilterConfigVO.GroupVO group(String key, String label, int sort,
                                                          List<LeadInboxFilterConfigVO.ConditionVO> conditions,
                                                          List<LeadInboxFilterConfigVO.OptionVO> stageOptions) {
        LeadInboxFilterConfigVO.GroupVO group = new LeadInboxFilterConfigVO.GroupVO();
        group.setKey(key);
        group.setLabel(label);
        group.setSort(sort);
        group.setEnabled(true);
        group.setConditions(new ArrayList<>(conditions));
        group.setSections(List.of(
                section(INBOX_FILTER_SECTION_CURRENT_STAGE, "当前环节", 0, stageOptions),
                section(INBOX_FILTER_SECTION_QUICK_CONDITION, "快捷条件", 10, QUICK_CONDITION)));
        return group;
    }

    private static LeadInboxFilterConfigVO.SectionVO section(String key, String label, int sort,
                                                              List<LeadInboxFilterConfigVO.OptionVO> options) {
        LeadInboxFilterConfigVO.SectionVO section = new LeadInboxFilterConfigVO.SectionVO();
        section.setKey(key);
        section.setLabel(label);
        section.setSort(sort);
        section.setOptions(new ArrayList<>(options));
        return section;
    }

    private static LeadInboxFilterConfigVO.OptionVO option(String key, String label, int sort,
                                                            List<LeadInboxFilterConfigVO.ConditionVO> conditions) {
        LeadInboxFilterConfigVO.OptionVO option = new LeadInboxFilterConfigVO.OptionVO();
        option.setKey(key);
        option.setLabel(label);
        option.setSort(sort);
        option.setEnabled(true);
        option.setConditions(conditions == null ? new ArrayList<>() : new ArrayList<>(conditions));
        return option;
    }

    private static List<LeadInboxFilterConfigVO.ConditionVO> condition(String field, String... values) {
        LeadInboxFilterConfigVO.ConditionVO condition = new LeadInboxFilterConfigVO.ConditionVO();
        condition.setField(field);
        condition.setValues(List.of(values));
        return List.of(condition);
    }
}
