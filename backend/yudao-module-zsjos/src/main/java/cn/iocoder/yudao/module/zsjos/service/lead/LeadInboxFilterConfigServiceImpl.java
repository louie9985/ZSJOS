package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter.LeadInboxFilterAdminRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter.LeadInboxFilterCapabilityRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter.LeadInboxFilterConfigVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter.LeadInboxFilterSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter.LeadInboxFilterVersionRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadInboxFilterSchemeDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadInboxFilterVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadInboxFilterSchemeMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadInboxFilterVersionMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderApprovalConfigMapper;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_INBOX_FILTER_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_INBOX_FILTER_NOT_EXISTS;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_INBOX_FILTER_VERSION_NOT_EXISTS;

@Service
public class LeadInboxFilterConfigServiceImpl implements LeadInboxFilterConfigService {

    private static final Set<String> AUDIENCES = Set.of(INBOX_AUDIENCE_SUBMITTER, INBOX_AUDIENCE_OWNER,
            INBOX_AUDIENCE_REVIEWER, INBOX_AUDIENCE_AGING_POOL, INBOX_AUDIENCE_MANAGEMENT);
    private static final Pattern CONFIG_KEY_PATTERN = Pattern.compile("[a-z][a-z0-9_]{1,63}");
    private static final Set<String> LEAD_FIELDS = Set.of(INBOX_FILTER_FIELD_STATUS,
            INBOX_FILTER_FIELD_ASSIGNMENT_STATUS, INBOX_FILTER_FIELD_HANDLING_STAGE,
            INBOX_FILTER_FIELD_SALES_PROGRESS, INBOX_FILTER_FIELD_SOURCE_TYPE,
            INBOX_FILTER_FIELD_FOLLOW_UP_CONDITION);
    private static final Map<String, Set<String>> ALLOWED_FIELDS_BY_AUDIENCE = Map.of(
            INBOX_AUDIENCE_SUBMITTER, LEAD_FIELDS,
            INBOX_AUDIENCE_OWNER, LEAD_FIELDS,
            INBOX_AUDIENCE_MANAGEMENT, LEAD_FIELDS,
            INBOX_AUDIENCE_REVIEWER, Set.of(INBOX_FILTER_FIELD_HANDLED, INBOX_FILTER_FIELD_TASK_DEFINITION_KEY),
            INBOX_AUDIENCE_AGING_POOL, Set.of(INBOX_FILTER_FIELD_POOL_STATUS));
    private static final Map<String, LinkedHashSet<String>> ALLOWED_VALUES = Map.of(
            // suspended 参与一级“待判定客资”：判定超时挂起的客资业务上仍属待判定。
            INBOX_FILTER_FIELD_STATUS, new LinkedHashSet<>(List.of("submitted", "suspended", "valid",
                    "invalid", "closed", "won")),
            // recycle_pending 是主管回收后的中间态，属于分配流程，可作二级“回收待处理”。
            INBOX_FILTER_FIELD_ASSIGNMENT_STATUS,
            new LinkedHashSet<>(List.of("unassigned", "pending_acceptance", "public_pool", "owned",
                    "recycle_pending")),
            INBOX_FILTER_FIELD_HANDLING_STAGE,
            new LinkedHashSet<>(List.of(LeadHandlingStage.FIRST_FOLLOW_PENDING,
                    LeadHandlingStage.QUALIFICATION_PENDING)),
            // 值域与 LeadSimpleStatusQuery 的成交分支一致：following 排除已进入成交审批或已成交的机会。
            INBOX_FILTER_FIELD_SALES_PROGRESS,
            new LinkedHashSet<>(List.of(FOLLOW_UP_FOLLOWING, FOLLOW_UP_DEAL_PENDING_APPROVAL, FOLLOW_UP_WON)),
            INBOX_FILTER_FIELD_SOURCE_TYPE,
            new LinkedHashSet<>(List.of(SOURCE_INTERNAL_NEW_MEDIA, SOURCE_PARTNER, SOURCE_SALES_SELF,
                    SOURCE_EDUCATION_SELF)),
            INBOX_FILTER_FIELD_FOLLOW_UP_CONDITION,
            new LinkedHashSet<>(List.of(FOLLOW_UP_CONDITION_TODAY, FOLLOW_UP_CONDITION_OVERDUE,
                    FOLLOW_UP_CONDITION_TRANSFERRED_PENDING)),
            INBOX_FILTER_FIELD_HANDLED, new LinkedHashSet<>(List.of("todo", "done")),
            INBOX_FILTER_FIELD_TASK_DEFINITION_KEY, new LinkedHashSet<>(List.of("registrationReview", "financeReview")),
            INBOX_FILTER_FIELD_POOL_STATUS, new LinkedHashSet<>(List.of(AGING_POOL_WAITING_ASSIGNMENT,
                    AGING_POOL_ASSIGNED, AGING_POOL_DEAL_PENDING)));

    @Resource
    private LeadInboxFilterSchemeMapper schemeMapper;
    @Resource
    private LeadInboxFilterVersionMapper versionMapper;
    @Resource private SalesOrderApprovalConfigMapper approvalConfigMapper;
    @Resource private DeptApi deptApi;

    @Override
    public LeadInboxFilterAdminRespVO getAdminConfig(String audience) {
        LeadInboxFilterSchemeDO scheme = requireScheme(audience);
        LeadInboxFilterConfigVO draft = parse(scheme.getDraftConfigJson());
        normalizeAndValidate(draft, audience);
        LeadInboxFilterConfigVO published = parseNullable(scheme.getPublishedConfigJson());
        if (!published.getGroups().isEmpty()) {
            normalizeAndValidate(published, audience);
        }
        return new LeadInboxFilterAdminRespVO(audience, audienceLabel(audience),
                draft.getGroups(), published.getGroups(),
                scheme.getPublishedVersion(), scheme.getPublishedAt(), scheme.getUpdateTime());
    }

    @Override
    public void saveDraft(LeadInboxFilterSaveReqVO reqVO) {
        validateAudience(reqVO.getAudience());
        LeadInboxFilterConfigVO config = new LeadInboxFilterConfigVO();
        config.setGroups(reqVO.getGroups());
        normalizeAndValidate(config, reqVO.getAudience());
        LeadInboxFilterSchemeDO scheme = requireScheme(reqVO.getAudience());
        LeadInboxFilterSchemeDO update = new LeadInboxFilterSchemeDO();
        update.setId(scheme.getId());
        update.setDraftConfigJson(JsonUtils.toJsonString(config));
        schemeMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer publish(String audience, Long userId) {
        LeadInboxFilterSchemeDO scheme = requireScheme(audience);
        LeadInboxFilterConfigVO config = parse(scheme.getDraftConfigJson());
        normalizeAndValidate(config, audience);
        return publishVersion(scheme, config, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer rollback(String audience, Integer versionNo, Long userId) {
        LeadInboxFilterSchemeDO scheme = requireScheme(audience);
        LeadInboxFilterVersionDO history = versionMapper.selectBySchemeIdAndVersion(scheme.getId(), versionNo);
        if (history == null) {
            throw exception(LEAD_INBOX_FILTER_VERSION_NOT_EXISTS);
        }
        LeadInboxFilterConfigVO config = parse(history.getConfigJson());
        normalizeAndValidate(config, scheme.getAudience());
        return publishVersion(scheme, config, userId);
    }

    @Override
    public List<LeadInboxFilterVersionRespVO> getVersions(String audience) {
        LeadInboxFilterSchemeDO scheme = requireScheme(audience);
        return versionMapper.selectListBySchemeId(scheme.getId()).stream()
                .map(item -> new LeadInboxFilterVersionRespVO(item.getVersionNo(), item.getPublishedBy(), item.getPublishedAt()))
                .toList();
    }

    @Override
    public List<LeadInboxFilterCapabilityRespVO> getCapabilities() {
        return getCapabilities(INBOX_AUDIENCE_SUBMITTER);
    }

    @Override
    public List<LeadInboxFilterCapabilityRespVO> getCapabilities(String audience) {
        validateAudience(audience);
        if (INBOX_AUDIENCE_REVIEWER.equals(audience)) {
            var config = approvalConfigMapper.selectCurrent();
            String registrationLabel = approvalLabel(config == null ? null : config.getRegistrationDeptId(), "报名履约中心");
            String financeLabel = approvalLabel(config == null ? null : config.getFinanceDeptId(), "财务结算中心");
            return List.of(
                    capability(INBOX_FILTER_FIELD_HANDLED, "处理状态", List.of(
                            value("todo", "待处理"), value("done", "已处理"))),
                    capability(INBOX_FILTER_FIELD_TASK_DEFINITION_KEY, "审批环节", List.of(
                            value("registrationReview", registrationLabel + "审批"),
                            value("financeReview", financeLabel + "审批"))));
        }
        if (INBOX_AUDIENCE_AGING_POOL.equals(audience)) {
            return List.of(capability(INBOX_FILTER_FIELD_POOL_STATUS, "公海状态", List.of(
                    value(AGING_POOL_WAITING_ASSIGNMENT, "待指派"), value(AGING_POOL_ASSIGNED, "协同跟进中"),
                    value(AGING_POOL_DEAL_PENDING, "成交审批中"))));
        }
        return List.of(
                capability(INBOX_FILTER_FIELD_STATUS, "客资主状态", List.of(
                        value(STATUS_SUBMITTED, "已提交"), value(STATUS_SUSPENDED, "已挂起"),
                        value(STATUS_VALID, "已判有效"), value(STATUS_INVALID, "已判无效"),
                        value(STATUS_CLOSED, "已关闭"), value(STATUS_WON, "已成交"))),
                capability(INBOX_FILTER_FIELD_ASSIGNMENT_STATUS, "分配状态", List.of(
                        value(ASSIGNMENT_UNASSIGNED, "待分配"), value(ASSIGNMENT_PENDING, "待接单"),
                        value(ASSIGNMENT_PUBLIC_POOL, "抢单池"), value(ASSIGNMENT_OWNED, "已归属"),
                        value(ASSIGNMENT_RECYCLE_PENDING, "回收待处理"))),
                capability(INBOX_FILTER_FIELD_HANDLING_STAGE, "处理阶段", List.of(
                        value(LeadHandlingStage.FIRST_FOLLOW_PENDING, "待首跟"),
                        value(LeadHandlingStage.QUALIFICATION_PENDING, "待判定"))),
                capability(INBOX_FILTER_FIELD_SALES_PROGRESS, "销售推进", List.of(
                        value(FOLLOW_UP_FOLLOWING, "正常推进"),
                        value(FOLLOW_UP_DEAL_PENDING_APPROVAL, "成交待审核"),
                        value(FOLLOW_UP_WON, "已成交"))),
                capability(INBOX_FILTER_FIELD_SOURCE_TYPE, "客资来源", List.of(
                        value(SOURCE_INTERNAL_NEW_MEDIA, "新媒体提交"), value(SOURCE_PARTNER, "兼职提交"),
                        value(SOURCE_SALES_SELF, "销售自拓录"), value(SOURCE_EDUCATION_SELF, "教务自拓录"))),
                capability(INBOX_FILTER_FIELD_FOLLOW_UP_CONDITION, "快捷条件", List.of(
                        value(FOLLOW_UP_CONDITION_TODAY, "今日待跟进"),
                        value(FOLLOW_UP_CONDITION_OVERDUE, "跟进已逾期"),
                        value(FOLLOW_UP_CONDITION_TRANSFERRED_PENDING, "有效转派待跟进"))));
    }

    @Override
    public LeadInboxFilterConfigVO getPublishedConfig(String audience) {
        LeadInboxFilterSchemeDO scheme = requireScheme(audience);
        if (scheme.getPublishedConfigJson() == null || scheme.getPublishedConfigJson().isBlank()) {
            throw exception(LEAD_INBOX_FILTER_NOT_EXISTS);
        }
        LeadInboxFilterConfigVO config = parse(scheme.getPublishedConfigJson());
        normalizeAndValidate(config, audience);
        return config;
    }

    @Override
    public LeadInboxFilterQuery resolveQuery(LeadInboxFilterConfigVO config, String groupKey, String optionKey) {
        // 兼容入口：调用方只有一个选中项，不知道它属于哪一行，因此按行顺序查找。
        String effectiveGroup = groupKey == null ? "all" : groupKey;
        LeadInboxFilterConfigVO.GroupVO group = config.getGroups().stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()) && effectiveGroup.equals(item.getKey()))
                .findFirst().orElseThrow(() -> exception(LEAD_INBOX_FILTER_INVALID));
        List<LeadInboxFilterConfigVO.ConditionVO> conditions = new ArrayList<>(group.getConditions());
        if (optionKey != null && !"all".equals(optionKey)) {
            conditions.addAll(findOption(group, optionKey).getConditions());
        }
        return compile(conditions);
    }

    /** 在各二级行内查找选中项；兼容尚未归一化的旧结构（仅有 {@code options}）。 */
    private static LeadInboxFilterConfigVO.OptionVO findOption(LeadInboxFilterConfigVO.GroupVO group,
                                                               String optionKey) {
        List<LeadInboxFilterConfigVO.OptionVO> candidates = new ArrayList<>();
        for (LeadInboxFilterConfigVO.SectionVO section : group.getSections()) {
            candidates.addAll(section.getOptions());
        }
        candidates.addAll(group.getOptions());
        return candidates.stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()) && optionKey.equals(item.getKey()))
                .findFirst().orElseThrow(() -> exception(LEAD_INBOX_FILTER_INVALID));
    }

    /**
     * 解析一级归类与各二级行当前选中项。每一行独立取一个选项，各行的条件与一级条件取交集。
     * 传空 Map 表示各行都停在“全部”。
     */
    @Override
    public LeadInboxFilterQuery resolveQuery(LeadInboxFilterConfigVO config, String groupKey,
                                             Map<String, String> sectionOptionKeys) {
        String effectiveGroup = groupKey == null ? "all" : groupKey;
        LeadInboxFilterConfigVO.GroupVO group = config.getGroups().stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()) && effectiveGroup.equals(item.getKey()))
                .findFirst().orElseThrow(() -> exception(LEAD_INBOX_FILTER_INVALID));
        List<LeadInboxFilterConfigVO.ConditionVO> conditions = new ArrayList<>(group.getConditions());
        for (LeadInboxFilterConfigVO.SectionVO section : group.getSections()) {
            String selected = sectionOptionKeys.get(section.getKey());
            if (selected == null || "all".equals(selected)) continue;
            LeadInboxFilterConfigVO.OptionVO option = section.getOptions().stream()
                    .filter(item -> Boolean.TRUE.equals(item.getEnabled()) && selected.equals(item.getKey()))
                    .findFirst().orElseThrow(() -> exception(LEAD_INBOX_FILTER_INVALID));
            conditions.addAll(option.getConditions());
        }
        return compile(conditions);
    }

    private Integer publishVersion(LeadInboxFilterSchemeDO scheme, LeadInboxFilterConfigVO config, Long userId) {
        int versionNo = scheme.getPublishedVersion() == null ? 1 : scheme.getPublishedVersion() + 1;
        String json = JsonUtils.toJsonString(config);
        LocalDateTime now = LocalDateTime.now();
        int expectedVersion = scheme.getPublishedVersion() == null ? 0 : scheme.getPublishedVersion();
        if (schemeMapper.updatePublished(scheme.getId(), expectedVersion, json, versionNo, userId, now) != 1) {
            throw exception(LEAD_INBOX_FILTER_INVALID);
        }

        LeadInboxFilterVersionDO version = new LeadInboxFilterVersionDO();
        version.setSchemeId(scheme.getId());
        version.setVersionNo(versionNo);
        version.setConfigJson(json);
        version.setPublishedBy(userId);
        version.setPublishedAt(now);
        versionMapper.insert(version);
        return versionNo;
    }

    private LeadInboxFilterSchemeDO requireScheme(String audience) {
        validateAudience(audience);
        LeadInboxFilterSchemeDO scheme = schemeMapper.selectByAudience(audience);
        if (scheme == null) {
            throw exception(LEAD_INBOX_FILTER_NOT_EXISTS);
        }
        return scheme;
    }

    private static void validateAudience(String audience) {
        if (!AUDIENCES.contains(audience)) {
            throw exception(LEAD_INBOX_FILTER_INVALID);
        }
    }

    private static LeadInboxFilterConfigVO parse(String json) {
        if (json == null || json.isBlank()) {
            throw exception(LEAD_INBOX_FILTER_NOT_EXISTS);
        }
        try {
            LeadInboxFilterConfigVO config = JsonUtils.parseObject(json, LeadInboxFilterConfigVO.class);
            if (config == null) {
                throw exception(LEAD_INBOX_FILTER_INVALID);
            }
            return config;
        } catch (RuntimeException ex) {
            throw exception(LEAD_INBOX_FILTER_INVALID);
        }
    }

    private static LeadInboxFilterConfigVO parseNullable(String json) {
        if (json == null || json.isBlank()) {
            return new LeadInboxFilterConfigVO();
        }
        return parse(json);
    }

    private static void normalizeAndValidate(LeadInboxFilterConfigVO config, String audience) {
        validateAudience(audience);
        if (config.getGroups() == null || config.getGroups().isEmpty() || config.getGroups().size() > 20) {
            throw exception(LEAD_INBOX_FILTER_INVALID);
        }
        config.setGroups(new ArrayList<>(config.getGroups()));
        if (config.getGroups().stream().anyMatch(group -> group == null || group.getSort() == null)) {
            throw exception(LEAD_INBOX_FILTER_INVALID);
        }
        config.getGroups().sort(Comparator.comparing(LeadInboxFilterConfigVO.GroupVO::getSort));
        Set<String> groupKeys = new HashSet<>();
        boolean hasAll = false;
        for (LeadInboxFilterConfigVO.GroupVO group : config.getGroups()) {
            if (!isValidKey(group.getKey()) || !groupKeys.add(group.getKey()) || isInvalidLabel(group.getLabel())
                    || group.getSort() == null || group.getEnabled() == null
                    || group.getSectionLabel() != null && group.getSectionLabel().length() > 20) {
                throw exception(LEAD_INBOX_FILTER_INVALID);
            }
            group.setConditions(nonNull(group.getConditions()));
            normalizeLegacySections(group, audience);
            if (group.getConditions().size() > 2 || group.getSections().size() > 3) {
                throw exception(LEAD_INBOX_FILTER_INVALID);
            }
            validateConditions(group.getConditions(), audience);
            if ("all".equals(group.getKey())) {
                hasAll = Boolean.TRUE.equals(group.getEnabled()) && group.getConditions().isEmpty();
            }
            if (group.getSections().stream().anyMatch(section -> section == null || section.getSort() == null)) {
                throw exception(LEAD_INBOX_FILTER_INVALID);
            }
            group.getSections().sort(Comparator.comparing(LeadInboxFilterConfigVO.SectionVO::getSort));
            Set<String> sectionKeys = new HashSet<>();
            for (LeadInboxFilterConfigVO.SectionVO section : group.getSections()) {
                if (!isValidKey(section.getKey()) || !sectionKeys.add(section.getKey())
                        || isInvalidLabel(section.getLabel())) {
                    throw exception(LEAD_INBOX_FILTER_INVALID);
                }
                section.setOptions(nonNull(section.getOptions()));
                if (section.getOptions().size() > 20) {
                    throw exception(LEAD_INBOX_FILTER_INVALID);
                }
                if (section.getOptions().stream().anyMatch(option -> option == null || option.getSort() == null)) {
                    throw exception(LEAD_INBOX_FILTER_INVALID);
                }
                section.getOptions().sort(Comparator.comparing(LeadInboxFilterConfigVO.OptionVO::getSort));
                Set<String> optionKeys = new HashSet<>();
                for (LeadInboxFilterConfigVO.OptionVO option : section.getOptions()) {
                    normalizeLegacyOptionKey(option, audience);
                    if (!isValidKey(option.getKey()) || !optionKeys.add(option.getKey())
                            || isInvalidLabel(option.getLabel())
                            || option.getSort() == null || option.getEnabled() == null) {
                        throw exception(LEAD_INBOX_FILTER_INVALID);
                    }
                    option.setConditions(nonNull(option.getConditions()));
                    if (option.getConditions().size() > 2) {
                        throw exception(LEAD_INBOX_FILTER_INVALID);
                    }
                    validateConditions(option.getConditions(), audience);
                    LeadInboxFilterQuery combined = compileCombined(group.getConditions(), option.getConditions());
                    if (combined.matchNone()) {
                        throw exception(LEAD_INBOX_FILTER_INVALID);
                    }
                }
                if (!section.getOptions().isEmpty() && section.getOptions().stream().noneMatch(item ->
                        "all".equals(item.getKey()) && Boolean.TRUE.equals(item.getEnabled())
                                && item.getConditions().isEmpty())) {
                    throw exception(LEAD_INBOX_FILTER_INVALID);
                }
            }
        }
        if (!INBOX_AUDIENCE_REVIEWER.equals(audience) && !hasAll) {
            throw exception(LEAD_INBOX_FILTER_INVALID);
        }
    }

    /**
     * 兼容单行时代的配置：只有 {@code sectionLabel} 时归一化为一行。
     * 已发布配置因此无需改库即可继续提供筛选项。
     */
    private static void normalizeLegacySections(LeadInboxFilterConfigVO.GroupVO group, String audience) {
        List<LeadInboxFilterConfigVO.SectionVO> sections = nonNull(group.getSections());
        if (sections.isEmpty() && group.getOptions() != null && !group.getOptions().isEmpty()) {
            boolean agingPool = INBOX_AUDIENCE_AGING_POOL.equals(audience);
            LeadInboxFilterConfigVO.SectionVO migrated = new LeadInboxFilterConfigVO.SectionVO();
            migrated.setKey(agingPool ? INBOX_FILTER_SECTION_POOL_STATUS : INBOX_FILTER_SECTION_CURRENT_STAGE);
            migrated.setLabel(group.getSectionLabel() == null || group.getSectionLabel().isBlank()
                    ? agingPool ? "公海状态" : "当前环节" : group.getSectionLabel());
            migrated.setSort(0);
            migrated.setOptions(new ArrayList<>(group.getOptions()));
            sections = new ArrayList<>(List.of(migrated));
        }
        group.setSections(sections);
        group.setOptions(new ArrayList<>());
    }

    private static void validateConditions(List<LeadInboxFilterConfigVO.ConditionVO> conditions, String audience) {
        Set<String> fields = new HashSet<>();
        for (LeadInboxFilterConfigVO.ConditionVO condition : conditions) {
            if (condition == null || !ALLOWED_FIELDS_BY_AUDIENCE.get(audience).contains(condition.getField())) {
                throw exception(LEAD_INBOX_FILTER_INVALID);
            }
            normalizeLegacyLeadStatus(condition, audience);
            Set<String> allowed = ALLOWED_VALUES.get(condition.getField());
            if (allowed == null || !fields.add(condition.getField()) || condition.getValues() == null
                    || condition.getValues().isEmpty() || condition.getValues().size() > 20
                    || condition.getValues().stream().anyMatch(value -> value == null || value.isBlank())
                    || !allowed.containsAll(condition.getValues())) {
                throw exception(LEAD_INBOX_FILTER_INVALID);
            }
            condition.setValues(condition.getValues().stream().distinct().toList());
        }
    }

    private static LeadInboxFilterQuery compileCombined(List<LeadInboxFilterConfigVO.ConditionVO> first,
                                                         List<LeadInboxFilterConfigVO.ConditionVO> second) {
        List<LeadInboxFilterConfigVO.ConditionVO> conditions = new ArrayList<>(first);
        conditions.addAll(second);
        return compile(conditions);
    }

    private static LeadInboxFilterQuery compile(List<LeadInboxFilterConfigVO.ConditionVO> conditions) {
        Map<String, Set<String>> valuesByField = new HashMap<>();
        boolean matchNone = false;
        for (LeadInboxFilterConfigVO.ConditionVO condition : conditions) {
            Set<String> values = new LinkedHashSet<>(condition.getValues());
            if (valuesByField.containsKey(condition.getField())) {
                values.retainAll(valuesByField.get(condition.getField()));
                matchNone |= values.isEmpty();
            }
            valuesByField.put(condition.getField(), values);
        }
        return new LeadInboxFilterQuery(
                valuesByField.getOrDefault(INBOX_FILTER_FIELD_STATUS, Set.of()),
                valuesByField.getOrDefault(INBOX_FILTER_FIELD_ASSIGNMENT_STATUS, Set.of()),
                valuesByField.getOrDefault(INBOX_FILTER_FIELD_HANDLING_STAGE, Set.of()), matchNone,
                Map.copyOf(valuesByField));
    }

    private static void normalizeLegacyOptionKey(LeadInboxFilterConfigVO.OptionVO option, String audience) {
        if ("registrationReview".equals(option.getKey())) {
            option.setKey("registration_review");
        } else if ("financeReview".equals(option.getKey())) {
            option.setKey("finance_review");
        } else if ((INBOX_AUDIENCE_SUBMITTER.equals(audience) || INBOX_AUDIENCE_OWNER.equals(audience))
                && "converted".equals(option.getKey()) && option.getConditions() != null
                && option.getConditions().stream().anyMatch(condition -> condition != null
                && INBOX_FILTER_FIELD_STATUS.equals(condition.getField()) && condition.getValues() != null
                && condition.getValues().contains("converted"))) {
            option.setKey("won");
            if ("已进入转化".equals(option.getLabel())) {
                option.setLabel("已成交");
            }
        }
    }

    private static void normalizeLegacyLeadStatus(LeadInboxFilterConfigVO.ConditionVO condition, String audience) {
        if (!(INBOX_AUDIENCE_SUBMITTER.equals(audience) || INBOX_AUDIENCE_OWNER.equals(audience))
                || !INBOX_FILTER_FIELD_STATUS.equals(condition.getField()) || condition.getValues() == null) {
            return;
        }
        condition.setValues(condition.getValues().stream()
                .map(value -> "converted".equals(value) ? "won" : value)
                .distinct().toList());
    }

    private static boolean isValidKey(String key) {
        return key != null && CONFIG_KEY_PATTERN.matcher(key).matches();
    }

    private static boolean isInvalidLabel(String label) {
        return label == null || label.isBlank() || label.length() > 20;
    }

    private static <T> List<T> nonNull(List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private static String audienceLabel(String audience) {
        return INBOX_AUDIENCE_SUBMITTER.equals(audience) ? "提交人视角"
                : INBOX_AUDIENCE_OWNER.equals(audience) ? "负责人视角"
                : INBOX_AUDIENCE_REVIEWER.equals(audience) ? "审批人视角" : "公海视角";
    }

    private static LeadInboxFilterCapabilityRespVO capability(String field, String label,
                                                               List<LeadInboxFilterCapabilityRespVO.ValueVO> values) {
        return new LeadInboxFilterCapabilityRespVO(field, label, values);
    }

    private static LeadInboxFilterCapabilityRespVO.ValueVO value(String value, String label) {
        return new LeadInboxFilterCapabilityRespVO.ValueVO(value, label);
    }

    private String approvalLabel(Long deptId, String fallback) {
        DeptRespDTO dept = deptId == null ? null : deptApi.getDept(deptId);
        return dept == null || dept.getName() == null || dept.getName().isBlank() ? fallback : dept.getName();
    }
}
