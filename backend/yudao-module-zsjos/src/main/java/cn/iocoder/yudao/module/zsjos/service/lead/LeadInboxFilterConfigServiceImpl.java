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
            INBOX_AUDIENCE_REVIEWER, INBOX_AUDIENCE_AGING_POOL);
    private static final Pattern CONFIG_KEY_PATTERN = Pattern.compile("[a-z][a-z0-9_]{1,63}");
    private static final Map<String, Set<String>> ALLOWED_FIELDS_BY_AUDIENCE = Map.of(
            INBOX_AUDIENCE_SUBMITTER, Set.of(INBOX_FILTER_FIELD_STATUS, INBOX_FILTER_FIELD_ASSIGNMENT_STATUS),
            INBOX_AUDIENCE_OWNER, Set.of(INBOX_FILTER_FIELD_STATUS, INBOX_FILTER_FIELD_ASSIGNMENT_STATUS),
            INBOX_AUDIENCE_REVIEWER, Set.of(INBOX_FILTER_FIELD_HANDLED, INBOX_FILTER_FIELD_TASK_DEFINITION_KEY),
            INBOX_AUDIENCE_AGING_POOL, Set.of(INBOX_FILTER_FIELD_POOL_STATUS));
    private static final Map<String, LinkedHashSet<String>> ALLOWED_VALUES = Map.of(
            INBOX_FILTER_FIELD_STATUS, new LinkedHashSet<>(List.of("submitted", "valid", "invalid", "closed", "won")),
            INBOX_FILTER_FIELD_ASSIGNMENT_STATUS,
            new LinkedHashSet<>(List.of("unassigned", "pending_acceptance", "public_pool", "owned")),
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
                        value("submitted", "已提交"), value("valid", "已判有效"), value("invalid", "已判无效"),
                        value("closed", "已关闭"), value("won", "已成交"))),
                capability(INBOX_FILTER_FIELD_ASSIGNMENT_STATUS, "分配状态", List.of(
                        value("unassigned", "待分配"), value("pending_acceptance", "待接单"),
                        value("public_pool", "抢单池"), value("owned", "已归属"))));
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
        String effectiveGroup = groupKey == null ? "all" : groupKey;
        String effectiveOption = optionKey == null ? "all" : optionKey;
        LeadInboxFilterConfigVO.GroupVO group = config.getGroups().stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()) && effectiveGroup.equals(item.getKey()))
                .findFirst().orElseThrow(() -> exception(LEAD_INBOX_FILTER_INVALID));
        List<LeadInboxFilterConfigVO.ConditionVO> conditions = new ArrayList<>(group.getConditions());
        if (!"all".equals(effectiveOption)) {
            LeadInboxFilterConfigVO.OptionVO option = group.getOptions().stream()
                    .filter(item -> Boolean.TRUE.equals(item.getEnabled()) && effectiveOption.equals(item.getKey()))
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
            group.setOptions(nonNull(group.getOptions()));
            if (group.getConditions().size() > 2 || group.getOptions().size() > 20) {
                throw exception(LEAD_INBOX_FILTER_INVALID);
            }
            validateConditions(group.getConditions(), audience);
            if ("all".equals(group.getKey())) {
                hasAll = Boolean.TRUE.equals(group.getEnabled()) && group.getConditions().isEmpty();
            }
            if (group.getOptions().stream().anyMatch(option -> option == null || option.getSort() == null)) {
                throw exception(LEAD_INBOX_FILTER_INVALID);
            }
            group.getOptions().sort(Comparator.comparing(LeadInboxFilterConfigVO.OptionVO::getSort));
            Set<String> optionKeys = new HashSet<>();
            for (LeadInboxFilterConfigVO.OptionVO option : group.getOptions()) {
                normalizeLegacyOptionKey(option);
                if (!isValidKey(option.getKey()) || !optionKeys.add(option.getKey()) || isInvalidLabel(option.getLabel())
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
            if (!group.getOptions().isEmpty() && group.getOptions().stream().noneMatch(item ->
                    "all".equals(item.getKey()) && Boolean.TRUE.equals(item.getEnabled()) && item.getConditions().isEmpty())) {
                throw exception(LEAD_INBOX_FILTER_INVALID);
            }
        }
        if (!INBOX_AUDIENCE_REVIEWER.equals(audience) && !hasAll) {
            throw exception(LEAD_INBOX_FILTER_INVALID);
        }
    }

    private static void validateConditions(List<LeadInboxFilterConfigVO.ConditionVO> conditions, String audience) {
        Set<String> fields = new HashSet<>();
        for (LeadInboxFilterConfigVO.ConditionVO condition : conditions) {
            if (condition == null || !ALLOWED_FIELDS_BY_AUDIENCE.get(audience).contains(condition.getField())) {
                throw exception(LEAD_INBOX_FILTER_INVALID);
            }
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
                valuesByField.getOrDefault(INBOX_FILTER_FIELD_ASSIGNMENT_STATUS, Set.of()), matchNone,
                Map.copyOf(valuesByField));
    }

    private static void normalizeLegacyOptionKey(LeadInboxFilterConfigVO.OptionVO option) {
        if ("registrationReview".equals(option.getKey())) {
            option.setKey("registration_review");
        } else if ("financeReview".equals(option.getKey())) {
            option.setKey("finance_review");
        }
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
                : INBOX_AUDIENCE_REVIEWER.equals(audience) ? "审批人视角" : "超期公海视角";
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
