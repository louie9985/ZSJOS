package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadProductReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadIntendedProductDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadIntendedProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

@Service
public class LeadDuplicateMatcher {
    public static final String SAME_MOBILE = DUPLICATE_RULE_STRONG_MOBILE;
    public static final String SAME_WECHAT = DUPLICATE_RULE_STRONG_WECHAT;
    public static final String WEAK_MOBILE_TO_WECHAT = DUPLICATE_RULE_WEAK_MOBILE_TO_WECHAT;
    public static final String WEAK_WECHAT_TO_MOBILE = DUPLICATE_RULE_WEAK_WECHAT_TO_MOBILE;
    public static final String NAME_REGION_PRIMARY_PRODUCT = DUPLICATE_RULE_WEAK_NAME_CITY_PRODUCT;
    public static final String NAME_MOBILE_LAST4 = DUPLICATE_RULE_WEAK_NAME_MOBILE_SUFFIX;
    private static final Set<String> PLACEHOLDER_NAMES = Set.of("客户", "微信用户", "未知", "未知客户",
            "未提供", "未透露", "匿名", "无");
    private static final Set<String> UNCERTAIN_PRODUCT_CODES = Set.of("unknown", "pending_confirm");
    private static final List<String> UNCERTAIN_PRODUCT_MARKERS = List.of("待确认", "未知", "未确定",
            "不确定", "暂不确定", "其他");

    @Resource private PersonMapper personMapper;
    @Resource private LeadMapper leadMapper;
    @Resource private LeadIntendedProductMapper productMapper;

    public MatchResult match(LeadCreateReqVO request, Long excludedPersonId) {
        String mobile = StrUtil.trimToNull(request.getMobile());
        String wechat = normalizeWechat(request.getWechatId());
        Map<Long, Candidate> candidates = new LinkedHashMap<>();
        List<PersonDO> contactPeople = personMapper.selectDuplicateCandidates(mobile, wechat).stream()
                .filter(person -> !Objects.equals(person.getId(), excludedPersonId)).toList();
        Map<Long, PersonDO> people = new LinkedHashMap<>();
        contactPeople.forEach(person -> people.put(person.getId(), person));
        List<LeadDO> contactLeads = leadMapper.selectByPersonIds(new ArrayList<>(people.keySet()));
        Map<Long, LeadDO> latestByPerson = new LinkedHashMap<>();
        contactLeads.forEach(lead -> latestByPerson.putIfAbsent(lead.getPersonId(), lead));
        for (PersonDO person : contactPeople) {
            Set<String> rules = new LinkedHashSet<>();
            String personMobile = StrUtil.trimToNull(person.getMobile());
            String personWechat = normalizeWechat(person.getWechatId());
            if (mobile != null && mobile.equals(personMobile)) rules.add(SAME_MOBILE);
            if (wechat != null && wechat.equals(personWechat)) rules.add(SAME_WECHAT);
            if (mobile != null && personWechat != null
                    && normalizeWechat(mobile).equals(personWechat)) rules.add(WEAK_MOBILE_TO_WECHAT);
            if (wechat != null && personMobile != null && wechat.equals(personMobile)) rules.add(WEAK_WECHAT_TO_MOBILE);
            addCandidate(candidates, person, latestByPerson.get(person.getId()), rules);
        }

        String name = StrUtil.trimToNull(request.getName());
        String last4 = mobile != null && mobile.length() >= 4 ? mobile.substring(mobile.length() - 4) : null;
        String primaryRef = primaryProductRef(request.getEffectiveProducts());
        if (mobile != null && ordinaryWeakName(name)) {
            for (LeadDO lead : leadMapper.selectByName(name)) {
                if (Objects.equals(lead.getPersonId(), excludedPersonId)) continue;
                Set<String> rules = new LinkedHashSet<>();
                if (last4 != null && lead.getSubmittedMobile() != null && lead.getSubmittedMobile().endsWith(last4)) {
                    rules.add(NAME_MOBILE_LAST4);
                }
                if (explicitRegion(request) && Objects.equals(request.getProvinceCode(), lead.getProvinceCode())
                        && Objects.equals(request.getCityCode(), lead.getCityCode())
                        && primaryRef != null && Objects.equals(primaryRef, primaryProductRef(lead.getId()))) {
                    rules.add(NAME_REGION_PRIMARY_PRODUCT);
                }
                if (!rules.isEmpty()) {
                    PersonDO person = people.computeIfAbsent(lead.getPersonId(), personMapper::selectById);
                    addCandidate(candidates, person, lead, rules);
                }
            }
        }

        List<Candidate> all = new ArrayList<>(candidates.values());
        Candidate strong = all.stream()
                .filter(candidate -> candidate.rules().contains(SAME_MOBILE)
                        || candidate.rules().contains(SAME_WECHAT))
                .findFirst().orElse(null);
        String duplicateFlag = strong == null
                ? (all.isEmpty() ? DUPLICATE_FLAG_NONE : DUPLICATE_FLAG_SUSPECTED)
                : DUPLICATE_FLAG_STRONG;
        String result = strong == null
                ? (all.isEmpty() ? DUPLICATE_FLAG_NONE : DUPLICATE_RESULT_SUSPECTED_CREATED)
                : DUPLICATE_RESULT_STRONG_REJECTED;
        List<String> rules = all.stream().flatMap(candidate -> candidate.rules().stream())
                .distinct().sorted().toList();
        String primaryRule = strong == null ? (rules.isEmpty() ? null : rules.getFirst())
                : (strong.rules().contains(SAME_MOBILE) ? SAME_MOBILE : SAME_WECHAT);
        return new MatchResult(strong, all, duplicateFlag, result, primaryRule,
                fingerprint(request, duplicateFlag, rules, all));
    }

    private void addCandidate(Map<Long, Candidate> candidates, PersonDO person, LeadDO lead, Set<String> rules) {
        if (person == null || rules.isEmpty()) return;
        Candidate existing = candidates.get(person.getId());
        if (existing == null) {
            candidates.put(person.getId(), new Candidate(person.getId(), lead == null ? null : lead.getId(),
                    lead == null ? null : lead.getLeadNo(),
                    person.getName(), lead == null ? null : lead.getStatus(),
                    lead == null ? null : lead.getAssignmentStatus(), rules));
        } else {
            existing.rules().addAll(rules);
        }
    }

    private String primaryProductRef(List<LeadProductReqVO> products) {
        if (products == null) return null;
        return products.stream().filter(item -> Boolean.TRUE.equals(item.getPrimary()))
                .findFirst().map(item -> Boolean.TRUE.equals(item.getSpuUnknown()) ? null
                        : explicitProductRef(item.effectiveSpuRef(), null)).orElse(null);
    }

    private String primaryProductRef(Long leadId) {
        LeadIntendedProductDO product = productMapper.selectPrimaryByLeadId(leadId);
        if (product == null) return null;
        return Boolean.TRUE.equals(product.getSpuUnknown()) ? null
                : explicitProductRef(product.getSpuRef(), product.getSpuNameSnapshot());
    }

    private boolean ordinaryWeakName(String name) {
        return name != null && !PLACEHOLDER_NAMES.contains(name);
    }

    private boolean explicitRegion(LeadCreateReqVO request) {
        return !REGION_OTHER.equals(request.getProvinceCode()) && !REGION_OTHER.equals(request.getCityCode());
    }

    private String explicitProductRef(String rawRef, String nameSnapshot) {
        String ref = StrUtil.trimToNull(rawRef);
        if (ref == null) return null;
        String normalizedRef = ref.toLowerCase(Locale.ROOT);
        if (UNCERTAIN_PRODUCT_CODES.contains(normalizedRef) || REGION_OTHER.equalsIgnoreCase(ref)) return null;
        if (nameSnapshot != null && UNCERTAIN_PRODUCT_MARKERS.stream().anyMatch(nameSnapshot::contains)) return null;
        return ref;
    }

    private static String normalizeWechat(String value) {
        String trimmed = StrUtil.trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String fingerprint(LeadCreateReqVO request, String duplicateFlag, List<String> rules,
                               List<Candidate> candidates) {
        if (DUPLICATE_FLAG_NONE.equals(duplicateFlag)) return null;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("flag", duplicateFlag);
        payload.put("rules", rules);
        payload.put("name", StrUtil.trimToNull(request.getName()));
        payload.put("mobile", StrUtil.trimToNull(request.getMobile()));
        payload.put("wechatId", normalizeWechat(request.getWechatId()));
        payload.put("provinceCode", request.getProvinceCode());
        payload.put("cityCode", request.getCityCode());
        payload.put("primarySpuRef", primaryProductRef(request.getEffectiveProducts()));
        payload.put("matched", candidates.stream()
                .map(candidate -> List.of(candidate.personId(), candidate.leadId() == null ? 0L : candidate.leadId()))
                .sorted(Comparator.comparing((List<Long> item) -> item.get(0)).thenComparing(item -> item.get(1)))
                .collect(Collectors.toList()));
        return DigestUtil.sha256Hex(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(payload));
    }

    public record MatchResult(Candidate strongActiveMatch, List<Candidate> candidates, String duplicateFlag,
                              String duplicateResult, String primaryRuleCode, String reviewFingerprint) {
        public boolean hasMatches() { return !candidates.isEmpty(); }
        public boolean strongDuplicate() { return DUPLICATE_FLAG_STRONG.equals(duplicateFlag); }
        public boolean suspectedDuplicate() { return DUPLICATE_FLAG_SUSPECTED.equals(duplicateFlag); }
        public boolean crossContactOnly() {
            return suspectedDuplicate() && candidates.stream().flatMap(candidate -> candidate.rules().stream())
                    .allMatch(rule -> WEAK_MOBILE_TO_WECHAT.equals(rule) || WEAK_WECHAT_TO_MOBILE.equals(rule));
        }
    }

    public record Candidate(Long personId, Long leadId, String leadNo, String personName, String leadStatus,
                            String assignmentStatus, Set<String> rules) {
        public boolean activeLead() {
            return leadId != null && !Set.of(STATUS_INVALID, STATUS_CLOSED, STATUS_WON).contains(leadStatus);
        }
    }
}
