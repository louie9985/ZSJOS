package cn.iocoder.yudao.module.zsjos.service.performance;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.MediaLeadVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.performance.MediaLeadTargetDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.performance.MediaLeadOrgDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.performance.MediaLeadRevisionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.MediaLeadFact;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.MediaLeadFactMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.MediaLeadOrderFact;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.MediaLeadTargetMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.MediaLeadOrgMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.MediaLeadRevisionMapper;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MediaLeadAnalysisService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> VALID = Set.of("valid", "converted", "won");
    @Resource private MediaLeadAccess access;
    @Resource private MediaLeadFactMapper facts;
    @Resource private MediaLeadTargetMapper targets;
    @Resource private MediaLeadOrgMapper orgs;
    @Resource private MediaLeadRevisionMapper revisions;

    public MediaLeadVO.Overview overview(MediaLeadVO.Query query) {
        access.authorize(query, false);
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDate today = now.toLocalDate();
        if (query.start().isAfter(query.end()) || java.time.temporal.ChronoUnit.DAYS.between(query.start(), query.end()) > 92)
            throw PerformanceAccess.invalid("提交日期范围不能超过 93 天");
        String scopeType = normalizedType(query);
        Long scopeId = normalizedId(query);
        List<Long> centerDeptIds = scopedCenterDepartments(scopeType, scopeId);
        var leads = facts.leads(TenantContextHolder.getRequiredTenantId(), scopeType, scopeId, centerDeptIds)
                .stream().filter(x -> x.getSubmittedAt() != null && !x.getSubmittedAt().isAfter(now)).toList();
        var orders = facts.firstOrders(TenantContextHolder.getRequiredTenantId(), scopeType, scopeId, centerDeptIds).stream()
                .filter(x -> x.getEffectiveAt() != null && !x.getEffectiveAt().isAfter(now))
                .collect(Collectors.toMap(MediaLeadOrderFact::getLeadId, MediaLeadOrderFact::getEffectiveAt, (a, b) -> a));
        var periods = periodStats(leads, orders, today);
        var monthStart = today.withDayOfMonth(1);
        var target = resolveTarget(query, monthStart, leads, today);
        return new MediaLeadVO.Overview(today, target, periods,
                members(query, leads, orders, today), calendar(leads, query.start(), query.end().isAfter(today) ? today : query.end()),
                funnel(leads, orders, query.start(), query.end().isAfter(today) ? today : query.end()),
                groups(leads, monthStart, today.plusDays(1), MediaLeadFact::getChannelLabel),
                groups(leads, monthStart.minusMonths(1), monthStart, MediaLeadFact::getChannelLabel),
                groups(leads, monthStart, today.plusDays(1), MediaLeadFact::getCategoryLabel),
                groups(leads, monthStart.minusMonths(1), monthStart, MediaLeadFact::getCategoryLabel));
    }

    public List<MediaLeadVO.Detail> details(MediaLeadVO.Query query) {
        access.authorize(query, false);
        LocalDateTime now = LocalDateTime.now(ZONE);
        if (query.start().isAfter(query.end())
                || java.time.temporal.ChronoUnit.DAYS.between(query.start(), query.end()) > 366)
            throw PerformanceAccess.invalid("下钻日期范围不能超过 367 天");
        String scopeType = normalizedType(query);
        Long scopeId = normalizedId(query);
        var leads = facts.leads(TenantContextHolder.getRequiredTenantId(), scopeType, scopeId,
                        scopedCenterDepartments(scopeType, scopeId)).stream()
                .filter(x -> x.getSubmittedAt() != null && !x.getSubmittedAt().isAfter(now)
                        && in(x.getSubmittedAt(), query.start(), query.end().plusDays(1)))
                .toList();
        var orders = facts.firstOrders(TenantContextHolder.getRequiredTenantId(), scopeType, scopeId,
                        scopedCenterDepartments(scopeType, scopeId)).stream()
                .filter(x -> x.getEffectiveAt() != null && !x.getEffectiveAt().isAfter(now))
                .collect(Collectors.toMap(MediaLeadOrderFact::getLeadId, MediaLeadOrderFact::getEffectiveAt,
                        (a, b) -> a));
        return leads.stream().map(x -> {
            var effectiveAt = orders.get(x.getId());
            String status = x.getStatus();
            String statusLabel = VALID.contains(status) ? "有效" : "invalid".equals(status) ? "无效" : "待判或其他";
            return new MediaLeadVO.Detail(x.getLeadNo(), x.getSubmittedAt(), x.getUserName(), status, statusLabel,
                    Objects.toString(x.getChannelLabel(), "历史未记录"),
                    Objects.toString(x.getCategoryLabel(), "历史未记录"), effectiveAt != null, effectiveAt);
        }).toList();
    }

    private List<Long> scopedCenterDepartments(String scopeType, Long scopeId) {
        if (!"CENTER".equals(scopeType) || scopeId == null) return List.of();
        List<Long> ids = new ArrayList<>();
        ids.add(scopeId);
        access.organizations().stream().filter(x -> "DEPT".equals(x.getKind())
                        && Objects.equals(x.getCenterId(), scopeId)).map(MediaLeadOrgDO::getDeptId)
                .filter(x -> !ids.contains(x)).forEach(ids::add);
        return ids;
    }

    public List<MediaLeadVO.ScopeNode> tree() {
        return access.tree();
    }

    public List<MediaLeadVO.Org> organizations() {
        if (!access.has(MediaLeadAccess.TARGET_QUERY)) throw MediaLeadAccess.denied();
        return access.organizations().stream().filter(o -> access.deptAllowed(o.getDeptId()))
                .map(o -> new MediaLeadVO.Org(o.getDeptId(), o.getCenterId(), o.getKind(),
                        targetName("DEPT", o.getDeptId()), targetName("CENTER", o.getCenterId()), o.getVersion()))
                .toList();
    }

    public List<MediaLeadVO.DeptOption> departmentOptions() {
        return access.departmentOptions().stream()
                .map(d -> new MediaLeadVO.DeptOption(d.getId(), d.getName(), d.getParentId())).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveOrganization(MediaLeadVO.OrgEdit edit) {
        if (!access.has(MediaLeadAccess.TARGET_CONFIGURE) || !access.writeDeptAllowed(edit.deptId())
                || !access.writeDeptAllowed(edit.centerId())) throw MediaLeadAccess.denied();
        var center = access.dept(edit.deptId());
        if (!"CENTER".equals(edit.kind()) || !Objects.equals(edit.deptId(), edit.centerId())
                || center == null || !Objects.equals(center.getStatus(), 0))
            throw PerformanceAccess.invalid("请选择有效的新媒体中心部门");
        var old = orgs.selectOne(new LambdaQueryWrapperX<MediaLeadOrgDO>()
                .eq(MediaLeadOrgDO::getDeptId, edit.deptId()).last("FOR UPDATE"));
        boolean versionMatches = old == null ? edit.version() == null
                : "CENTER".equals(old.getKind()) ? Objects.equals(old.getVersion(), edit.version())
                : edit.version() == null;
        if (!versionMatches) throw PerformanceAccess.invalid("组织配置已变更，请刷新后重试");
        var row = old == null ? new MediaLeadOrgDO() : old;
        row.setDeptId(edit.deptId()); row.setCenterId(edit.centerId()); row.setKind(edit.kind());
        row.setVersion(old == null ? 0 : old.getVersion() + 1);
        if (old == null) orgs.insert(row); else orgs.updateById(row);
    }

    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "media-lead-org", bizId = "#edit.deptId", action = "unset")
    public void unsetOrganization(MediaLeadVO.OrgUnset edit) {
        if (!access.has(MediaLeadAccess.TARGET_CONFIGURE) || !access.writeDeptAllowed(edit.deptId()))
            throw MediaLeadAccess.denied();
        var row = orgs.selectOne(new LambdaQueryWrapperX<MediaLeadOrgDO>()
                .eq(MediaLeadOrgDO::getDeptId, edit.deptId()).last("FOR UPDATE"));
        if (row == null || !"CENTER".equals(row.getKind()))
            throw PerformanceAccess.invalid("中心设置不存在，请刷新后重试");
        if (!Objects.equals(row.getVersion(), edit.version()))
            throw PerformanceAccess.invalid("组织配置已变更，请刷新后重试");
        // Historical DEPT rows are inert; demoting preserves a reselectable record without deleting targets or leads.
        row.setKind("DEPT");
        row.setVersion(row.getVersion() + 1);
        orgs.updateById(row);
    }

    public List<MediaLeadVO.Target> listTargets(LocalDate periodStart) {
        if (!access.has(MediaLeadAccess.TARGET_QUERY)) throw MediaLeadAccess.denied();
        if (periodStart == null || periodStart.getDayOfMonth() != 1) throw PerformanceAccess.invalid("请选择月份首日");
        List<MediaLeadVO.Target> result = new ArrayList<>();
        var byKey = targetRows(periodStart);
        var users = access.mediaUsers();
        var organizations = access.organizations();
        for (var user : users) {
            if (!access.deptAllowed(user.getDeptId())) continue;
            var row = byKey.get("USER:" + user.getId());
            result.add(new MediaLeadVO.Target(row == null ? null : row.getId(), "USER", user.getId(),
                    user.getNickname(), periodStart, row == null ? null : row.getTargetCount(), null,
                    row != null && Boolean.TRUE.equals(row.getManual()), row == null ? null : row.getVersion()));
        }
        for (var org : organizations) {
            if (!access.deptAllowed(org.getDeptId())) continue;
            if ("CENTER".equals(org.getKind()) && organizations.stream()
                    .filter(x -> "DEPT".equals(x.getKind()) && Objects.equals(x.getCenterId(), org.getDeptId()))
                    .anyMatch(x -> !access.deptAllowed(x.getDeptId()))) continue;
            var row = byKey.get(org.getKind() + ":" + org.getDeptId());
            var dept = access.dept(org.getDeptId());
            result.add(new MediaLeadVO.Target(row == null ? null : row.getId(), org.getKind(), org.getDeptId(),
                    dept == null ? "历史组织" : dept.getName(), periodStart,
                    effectiveTarget(org.getKind(), org.getDeptId(), byKey, users, organizations), null,
                    row != null && Boolean.TRUE.equals(row.getManual()), row == null ? null : row.getVersion()));
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveTargets(List<MediaLeadVO.TargetEdit> edits) {
        if (!access.has(MediaLeadAccess.TARGET_UPDATE)) throw MediaLeadAccess.denied();
        if (edits == null || edits.isEmpty()) throw PerformanceAccess.invalid("请选择要保存的指标");
        Set<String> seen = new HashSet<>();
        for (var edit : edits) {
            if (!seen.add(edit.scopeType() + ":" + edit.scopeId() + ":" + edit.periodStart()))
                throw PerformanceAccess.invalid("同批指标重复");
            if (!edit.periodStart().equals(edit.periodStart().withDayOfMonth(1)))
                throw PerformanceAccess.invalid("指标月份必须从月初开始");
            if (edit.restoreAutomatic() && "USER".equals(edit.scopeType()))
                throw PerformanceAccess.invalid("个人目标不能恢复自动汇总");
            if (!edit.restoreAutomatic() && edit.targetCount() == null)
                throw PerformanceAccess.invalid("请填写非负整数目标");
            access.authorizeTargetWrite(edit.scopeType(), edit.scopeId());
        }
        for (var edit : edits) {
            var old = targets.selectOne(new LambdaQueryWrapperX<MediaLeadTargetDO>()
                    .eq(MediaLeadTargetDO::getScopeType, edit.scopeType())
                    .eq(MediaLeadTargetDO::getScopeId, edit.scopeId())
                    .eq(MediaLeadTargetDO::getPeriodStart, edit.periodStart())
                    .last("FOR UPDATE"));
            if (old != null && !Objects.equals(old.getVersion(), edit.version()) || old == null && edit.version() != null)
                throw PerformanceAccess.invalid("指标已被修改，请刷新后重试");
            if (old != null && !access.writeDeptAllowed(old.getDeptId())) throw MediaLeadAccess.denied();
            String before = old == null ? null : JsonUtils.toJsonString(old);
            var row = old == null ? new MediaLeadTargetDO() : old;
            row.setScopeType(edit.scopeType()); row.setScopeId(edit.scopeId());
            row.setPeriodStart(edit.periodStart()); row.setTargetCount(edit.restoreAutomatic() ? 0 : edit.targetCount());
            row.setManual(!edit.restoreAutomatic()); row.setReason(edit.reason().trim()); row.setVersion(old == null ? 0 : old.getVersion() + 1);
            if (old == null) {
                if ("USER".equals(edit.scopeType())) {
                    var user = access.user(edit.scopeId()); row.setDeptId(user.getDeptId());
                    var org = access.organization("DEPT", user.getDeptId());
                    row.setCenterId(org == null ? null : org.getCenterId());
                } else {
                    var org = access.organization(edit.scopeType(), edit.scopeId());
                    row.setDeptId(edit.scopeId()); row.setCenterId(org.getCenterId());
                }
            }
            if (old == null) targets.insert(row); else targets.updateById(row);
            var revision = new MediaLeadRevisionDO();
            revision.setTargetId(row.getId()); revision.setBeforeJson(before);
            revision.setAfterJson(JsonUtils.toJsonString(row)); revision.setReason(row.getReason());
            revision.setOperatorId(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId());
            revisions.insert(revision);
        }
    }

    @ZsjosPermission(bizType = "media-lead-target", bizId = "#id", action = "read")
    public List<MediaLeadVO.TargetRevision> revisions(Long id) {
        var row = targets.selectById(id);
        if (row == null) throw MediaLeadAccess.denied();
        access.authorize(new MediaLeadVO.Query(row.getScopeType(), row.getScopeId(), row.getPeriodStart(), row.getPeriodStart()), true);
        return revisions.selectList(new LambdaQueryWrapperX<MediaLeadRevisionDO>()
                .eq(MediaLeadRevisionDO::getTargetId, id).orderByDesc(MediaLeadRevisionDO::getId)).stream()
                .map(x -> new MediaLeadVO.TargetRevision(x.getId(), x.getTargetId(), x.getBeforeJson(),
                        x.getAfterJson(), x.getReason(), x.getOperatorId(), x.getCreateTime())).toList();
    }

    private MediaLeadVO.Target resolveTarget(MediaLeadVO.Query query, LocalDate month, List<MediaLeadFact> leads, LocalDate today) {
        String type = normalizedType(query); Long id = normalizedId(query);
        var byKey = targetRows(month);
        var row = byKey.get(type + ":" + id);
        int actual = (int) leads.stream().filter(x -> VALID.contains(x.getStatus()) && in(x.getSubmittedAt(), month, today.plusDays(1))).count();
        return new MediaLeadVO.Target(row == null ? null : row.getId(), type, id, targetName(type, id), month,
                effectiveTarget(type, id, byKey, access.mediaUsers(), access.organizations()), actual,
                row != null && Boolean.TRUE.equals(row.getManual()), row == null ? null : row.getVersion());
    }

    private Map<String, MediaLeadTargetDO> targetRows(LocalDate month) {
        return targets.selectList(new LambdaQueryWrapperX<MediaLeadTargetDO>()
                .eq(MediaLeadTargetDO::getPeriodStart, month)).stream().collect(Collectors.toMap(
                x -> x.getScopeType() + ":" + x.getScopeId(), Function.identity(), (a, b) -> a));
    }

    private Integer effectiveTarget(String type, Long id, Map<String, MediaLeadTargetDO> rows,
                                    List<cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO> users,
                                    List<MediaLeadOrgDO> organizations) {
        var explicit = rows.get(type + ":" + id);
        if (explicit != null && Boolean.TRUE.equals(explicit.getManual())) return explicit.getTargetCount();
        if ("USER".equals(type)) return null;
        List<Integer> children = new ArrayList<>();
        users.stream().filter(u -> Objects.equals(u.getDeptId(), id))
                .map(u -> effectiveTarget("USER", u.getId(), rows, users, organizations)).forEach(children::add);
        if ("CENTER".equals(type)) organizations.stream()
                .filter(o -> "DEPT".equals(o.getKind()) && Objects.equals(o.getCenterId(), id))
                .filter(o -> users.stream().anyMatch(u -> Objects.equals(u.getDeptId(), o.getDeptId()))
                        || rows.get("DEPT:" + o.getDeptId()) != null
                        && Boolean.TRUE.equals(rows.get("DEPT:" + o.getDeptId()).getManual()))
                .map(o -> effectiveTarget("DEPT", o.getDeptId(), rows, users, organizations)).forEach(children::add);
        return children.isEmpty() || children.stream().anyMatch(Objects::isNull)
                ? null : children.stream().mapToInt(Integer::intValue).sum();
    }

    private List<MediaLeadVO.PeriodStats> periodStats(List<MediaLeadFact> leads, Map<Long, LocalDateTime> orders, LocalDate today) {
        List<MediaLeadVO.PeriodStats> result = new ArrayList<>();
        var monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        add(result, "yesterday", "昨日", leads, orders, today.minusDays(1), today);
        add(result, "today", "今日", leads, orders, today, today.plusDays(1));
        add(result, "lastWeek", "上周", leads, orders, monday.minusWeeks(1), monday);
        add(result, "week", "本周", leads, orders, monday, today.plusDays(1));
        var month = today.withDayOfMonth(1);
        add(result, "lastTwoMonth", "上上月", leads, orders, month.minusMonths(2), month.minusMonths(1));
        add(result, "lastMonth", "上月", leads, orders, month.minusMonths(1), month);
        add(result, "month", "本月", leads, orders, month, today.plusDays(1));
        add(result, "year", "本年", leads, orders, today.withDayOfYear(1), today.plusDays(1));
        add(result, "all", "全部", leads, orders, null, today.plusDays(1));
        return result;
    }

    private void add(List<MediaLeadVO.PeriodStats> out, String key, String label, List<MediaLeadFact> leads,
                     Map<Long, LocalDateTime> orders, LocalDate start, LocalDate end) {
        var selected = leads.stream().filter(x -> start == null || in(x.getSubmittedAt(), start, end)).toList();
        long valid = selected.stream().filter(x -> VALID.contains(x.getStatus())).count();
        long converted = selected.stream().filter(x -> VALID.contains(x.getStatus()) && orders.containsKey(x.getId())).count();
        long invalid = selected.stream().filter(x -> "invalid".equals(x.getStatus())).count();
        out.add(new MediaLeadVO.PeriodStats(key, label, selected.size(), valid, invalid, converted,
                ratio(valid, selected.size()), ratio(converted, valid)));
    }

    private List<MediaLeadVO.Member> members(MediaLeadVO.Query query, List<MediaLeadFact> leads, Map<Long, LocalDateTime> orders, LocalDate today) {
        Map<Long, String> names = new LinkedHashMap<>();
        Set<Long> allowedDepts = new HashSet<>();
        if ("CENTER".equals(query.scopeType())) {
            allowedDepts.add(query.scopeId());
            access.organizations().stream()
                    .filter(o -> "DEPT".equals(o.getKind()) && Objects.equals(o.getCenterId(), query.scopeId()))
                    .map(MediaLeadOrgDO::getDeptId).forEach(allowedDepts::add);
        }
        access.mediaUsers().stream().filter(u -> switch (query.scopeType()) {
            case "SELF" -> Objects.equals(u.getId(), normalizedId(query));
            case "USER" -> Objects.equals(u.getId(), query.scopeId());
            case "DEPT" -> Objects.equals(u.getDeptId(), query.scopeId());
            case "CENTER" -> allowedDepts.contains(u.getDeptId());
            default -> false;
        }).forEach(x -> names.put(x.getId(), x.getNickname()));
        leads.forEach(x -> names.putIfAbsent(x.getUserId(), x.getUserName() == null ? "历史人员" : x.getUserName()));
        LocalDate month = today.withDayOfMonth(1), lastMonth = month.minusMonths(1), monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<String, MediaLeadTargetDO> targetsByKey = targetRows(month);
        List<MediaLeadVO.Member> result = new ArrayList<>();
        names.forEach((userId, name) -> {
            var own = leads.stream().filter(x -> Objects.equals(userId, x.getUserId())).toList();
            var row = targetsByKey.get("USER:" + userId);
            result.add(new MediaLeadVO.Member(userId, name, row == null ? null : row.getTargetCount(),
                    count(own, today.minusDays(1), today, false), converted(own, orders, today.minusDays(1), today),
                    count(own, today, today.plusDays(1), false), converted(own, orders, today, today.plusDays(1)),
                    count(own, monday, today.plusDays(1), false), count(own, monday, today.plusDays(1), true), converted(own, orders, monday, today.plusDays(1)),
                    count(own, monday.minusWeeks(1), monday, false), count(own, monday.minusWeeks(1), monday, true), converted(own, orders, monday.minusWeeks(1), monday),
                    count(own, month, today.plusDays(1), false), count(own, month, today.plusDays(1), true), converted(own, orders, month, today.plusDays(1)),
                    count(own, lastMonth, month, false), count(own, lastMonth, month, true), converted(own, orders, lastMonth, month),
                    ratio(count(own, month, today.plusDays(1), true), row == null ? 0 : row.getTargetCount())));
        });
        return result;
    }

    private long count(List<MediaLeadFact> rows, LocalDate start, LocalDate end, boolean valid) { return rows.stream().filter(x -> in(x.getSubmittedAt(), start, end) && (!valid || VALID.contains(x.getStatus()))).count(); }
    private long converted(List<MediaLeadFact> rows, Map<Long, LocalDateTime> orders, LocalDate start, LocalDate end) { return rows.stream().filter(x -> { var t = orders.get(x.getId()); return t != null && in(t, start, end); }).count(); }
    private List<MediaLeadVO.CalendarDay> calendar(List<MediaLeadFact> rows, LocalDate start, LocalDate end) { if (start.isAfter(end)) return List.of(); return start.datesUntil(end.plusDays(1)).map(d -> { var day=rows.stream().filter(x -> x.getSubmittedAt()!=null && x.getSubmittedAt().toLocalDate().equals(d)).toList(); long valid=day.stream().filter(x->VALID.contains(x.getStatus())).count(), invalid=day.stream().filter(x->"invalid".equals(x.getStatus())).count(); return new MediaLeadVO.CalendarDay(d,day.size(),valid,invalid,day.size()-valid-invalid); }).toList(); }
    private MediaLeadVO.Funnel funnel(List<MediaLeadFact> rows, Map<Long, LocalDateTime> orders, LocalDate start, LocalDate end) {
        if (start.isAfter(end)) return new MediaLeadVO.Funnel(0, 0, 0);
        var selected = rows.stream().filter(x -> in(x.getSubmittedAt(), start, end.plusDays(1))).toList();
        long valid = selected.stream().filter(x -> VALID.contains(x.getStatus())).count();
        long converted = selected.stream().filter(x -> VALID.contains(x.getStatus()) && orders.containsKey(x.getId())).count();
        return new MediaLeadVO.Funnel(selected.size(), valid, converted);
    }
    private List<MediaLeadVO.Group> groups(List<MediaLeadFact> rows, LocalDate start, LocalDate end, Function<MediaLeadFact,String> key) { return rows.stream().filter(x -> VALID.contains(x.getStatus()) && in(x.getSubmittedAt(), start, end)).collect(Collectors.groupingBy(x -> Objects.toString(key.apply(x), "历史未记录"), Collectors.counting())).entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).map(x -> new MediaLeadVO.Group(x.getKey(), x.getValue())).toList(); }
    private static boolean in(LocalDateTime value, LocalDate start, LocalDate end) { return value != null && (start == null || !value.toLocalDate().isBefore(start)) && (end == null || value.toLocalDate().isBefore(end)); }
    private static BigDecimal ratio(long a, long b) { return b == 0 ? null : BigDecimal.valueOf(a).divide(BigDecimal.valueOf(b), 6, RoundingMode.HALF_UP); }
    private String normalizedType(MediaLeadVO.Query q) { return "SELF".equals(q.scopeType()) ? "USER" : q.scopeType(); }
    private Long normalizedId(MediaLeadVO.Query q) { return "SELF".equals(q.scopeType()) ? cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId() : q.scopeId(); }
    private String targetName(String type, Long id) { if ("USER".equals(type)) { var u=access.user(id); return u==null?"历史人员":u.getNickname(); } var d=access.dept(id); return d==null?"历史组织":d.getName(); }
}
