package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.calendar.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadCalendarMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadCalendarMapper.DueLead;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

@Service
@Validated
public class LeadCalendarService {
    @Resource private LeadCalendarMapper mapper;
    @Resource private LeadManagementService managementService;
    @Resource private LeadFollowUpService followUpService;
    @Resource private LeadObjectPermissionService objectPermissionService;
    @Resource private SecurityFrameworkService security;
    @Resource private DictDataApi dictDataApi;

    private List<DueLead> due(LeadCalendarQueryReqVO query, Long userId) {
        return mapper.selectDueLeads(TenantContextHolder.getRequiredTenantId(), Objects.requireNonNull(userId),
                query.getStart().atStartOfDay(), query.getEnd().atStartOfDay());
    }

    public List<LeadCalendarDayRespVO> days(LeadCalendarQueryReqVO query, Long userId) {
        return due(query, userId).stream().collect(Collectors.groupingBy(row -> row.getDeadline().toLocalDate(),
                TreeMap::new, Collectors.counting())).entrySet().stream()
                .map(entry -> new LeadCalendarDayRespVO(entry.getKey(), entry.getValue())).toList();
    }

    public PageResult<LeadCalendarCardRespVO> cards(LeadCalendarQueryReqVO query, Long userId) {
        List<DueLead> rows = new ArrayList<>(due(query, userId));
        Map<String, Integer> categoryOrder = new HashMap<>();
        if ("category".equals(query.getSort())) {
            var categories = dictDataApi.getDictDataList(DICT_CATEGORY);
            for (int i = 0; i < categories.size(); i++) {
                var category = categories.get(i);
                // S priority is the requested sorting rule, not a replacement dictionary option list.
                categoryOrder.put(category.getValue(), isPriorityCategory(category.getValue(), category.getLabel()) ? -1 : i);
            }
        }
        rows.sort(comparator(query.getSort(), query.getDirection(), categoryOrder));
        int from = Math.min((query.getPageNo() - 1) * query.getPageSize(), rows.size());
        int to = Math.min(from + query.getPageSize(), rows.size());
        boolean canReadFollowUp = security.hasPermission(PERMISSION_DETAIL_FOLLOW_UP_READ);
        List<LeadCalendarCardRespVO> result = rows.subList(from, to).stream().map(row -> {
            var lead = managementService.getLead(row.getId(), userId);
            var last = canReadFollowUp ? latest(row.getId(), userId) : null;
            return new LeadCalendarCardRespVO(lead, row.getDeadline(), last, canReadFollowUp);
        }).toList();
        return new PageResult<>(result, (long) rows.size());
    }

    private cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup.LeadFollowUpRespVO latest(Long id, Long userId) {
        objectPermissionService.check(id, "follow-up-read");
        var records = followUpService.getPage(id, 1, 1, userId).getList();
        return records.isEmpty() ? null : records.getFirst();
    }

    static Comparator<DueLead> comparator(String sort, String direction, Map<String, Integer> categoryOrder) {
        Comparator<DueLead> primary = "category".equals(sort)
                ? Comparator.comparingInt(row -> row.getCategory() == null ? Integer.MAX_VALUE
                    : categoryOrder.getOrDefault(row.getCategory(), Integer.MAX_VALUE))
                : Comparator.comparing(DueLead::getDeadline);
        if ("desc".equals(direction)) primary = primary.reversed();
        return primary.thenComparing(DueLead::getDeadline).thenComparing(DueLead::getId);
    }

    static boolean isPriorityCategory(String value, String label) {
        return java.util.stream.Stream.of(value, label).filter(Objects::nonNull)
                .anyMatch(text -> text.trim().matches("(?i)^S(?:$|[级类].*)"));
    }
}
