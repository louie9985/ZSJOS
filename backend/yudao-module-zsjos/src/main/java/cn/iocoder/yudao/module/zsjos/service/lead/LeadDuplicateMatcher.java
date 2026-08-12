package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.hutool.core.util.StrUtil;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.STATUS_CLOSED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.STATUS_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.STATUS_WON;

@Service
public class LeadDuplicateMatcher {
    public static final String SAME_MOBILE = "same_mobile";
    public static final String SAME_WECHAT = "same_wechat";
    public static final String CROSS_CONTACT = "cross_contact";
    public static final String NAME_REGION_PRIMARY_PRODUCT = "name_region_primary_product";
    public static final String NAME_MOBILE_LAST4 = "name_mobile_last4";

    @Resource private PersonMapper personMapper;
    @Resource private LeadMapper leadMapper;
    @Resource private LeadIntendedProductMapper productMapper;

    public MatchResult match(LeadCreateReqVO request, Long excludedPersonId) {
        String mobile = StrUtil.trimToNull(request.getMobile());
        String wechat = StrUtil.trimToNull(request.getWechatId());
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
            if (mobile != null && mobile.equals(person.getMobile())) rules.add(SAME_MOBILE);
            if (wechat != null && wechat.equals(person.getWechatId())) rules.add(SAME_WECHAT);
            if (mobile != null && mobile.equals(person.getWechatId())
                    || wechat != null && wechat.equals(person.getMobile())) rules.add(CROSS_CONTACT);
            addCandidate(candidates, person, latestByPerson.get(person.getId()), rules);
        }

        String name = request.getName().trim();
        String last4 = mobile != null && mobile.length() >= 4 ? mobile.substring(mobile.length() - 4) : null;
        String primaryRef = primaryProductRef(request.getEffectiveProducts());
        for (LeadDO lead : leadMapper.selectByName(name)) {
            if (Objects.equals(lead.getPersonId(), excludedPersonId)) continue;
            Set<String> rules = new LinkedHashSet<>();
            if (last4 != null && lead.getSubmittedMobile() != null && lead.getSubmittedMobile().endsWith(last4)) {
                rules.add(NAME_MOBILE_LAST4);
            }
            if (Objects.equals(request.getProvinceCode(), lead.getProvinceCode())
                    && Objects.equals(request.getCityCode(), lead.getCityCode())
                    && primaryRef != null && Objects.equals(primaryRef, primaryProductRef(lead.getId()))) {
                rules.add(NAME_REGION_PRIMARY_PRODUCT);
            }
            if (!rules.isEmpty()) {
                PersonDO person = people.computeIfAbsent(lead.getPersonId(), personMapper::selectById);
                addCandidate(candidates, person, lead, rules);
            }
        }

        Candidate strong = candidates.values().stream()
                .filter(Candidate::activeLead)
                .filter(candidate -> candidate.rules().contains(SAME_MOBILE)
                        || candidate.rules().contains(SAME_WECHAT))
                .findFirst().orElse(null);
        return new MatchResult(strong, new ArrayList<>(candidates.values()));
    }

    private void addCandidate(Map<Long, Candidate> candidates, PersonDO person, LeadDO lead, Set<String> rules) {
        if (person == null || rules.isEmpty()) return;
        Candidate existing = candidates.get(person.getId());
        if (existing == null) {
            candidates.put(person.getId(), new Candidate(person.getId(), lead == null ? null : lead.getId(),
                    person.getName(), lead == null ? null : lead.getStatus(),
                    lead == null ? null : lead.getAssignmentStatus(), rules));
        } else {
            existing.rules().addAll(rules);
        }
    }

    private String primaryProductRef(List<LeadProductReqVO> products) {
        if (products == null) return null;
        return products.stream().filter(item -> Boolean.TRUE.equals(item.getPrimary()))
                .findFirst().map(item -> Boolean.TRUE.equals(item.getSpuUnknown()) ? "UNKNOWN"
                        : item.effectiveSpuRef()).orElse(null);
    }

    private String primaryProductRef(Long leadId) {
        LeadIntendedProductDO product = productMapper.selectPrimaryByLeadId(leadId);
        if (product == null) return null;
        return Boolean.TRUE.equals(product.getSpuUnknown()) ? "UNKNOWN" : product.getSpuRef();
    }

    public record MatchResult(Candidate strongActiveMatch, List<Candidate> candidates) {
        public boolean hasMatches() { return !candidates.isEmpty(); }
    }

    public record Candidate(Long personId, Long leadId, String personName, String leadStatus,
                            String assignmentStatus, Set<String> rules) {
        public boolean activeLead() {
            return leadId != null && !Set.of(STATUS_INVALID, STATUS_CLOSED, STATUS_WON).contains(leadStatus);
        }
    }
}
