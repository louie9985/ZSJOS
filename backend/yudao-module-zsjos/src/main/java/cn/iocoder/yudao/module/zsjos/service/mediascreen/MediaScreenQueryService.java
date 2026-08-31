package cn.iocoder.yudao.module.zsjos.service.mediascreen;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.pub.mediascreen.vo.MediaScreenRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.mediascreen.MediaScreenDailySnapshotDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.MediaScreenContributionRow;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.MediaScreenTimedContributionRow;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.mediascreen.MediaScreenDailySnapshotMapper;
import cn.iocoder.yudao.module.zsjos.framework.mediascreen.MediaScreenProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class MediaScreenQueryService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String DIRECT = "direct";
    private static final String PART_TIME = "part_time";

    private final LeadMapper leadMapper;
    private final MediaScreenDailySnapshotMapper snapshotMapper;
    private final PartnerMapper partnerMapper;
    private final AdminUserApi adminUserApi;
    private final DeptApi deptApi;
    private final MaintenanceModeApi maintenanceModeApi;
    private final StringRedisTemplate redis;
    private final MediaScreenProperties properties;

    public MediaScreenQueryService(LeadMapper leadMapper, MediaScreenDailySnapshotMapper snapshotMapper,
                                   PartnerMapper partnerMapper, AdminUserApi adminUserApi, DeptApi deptApi,
                                   MaintenanceModeApi maintenanceModeApi, StringRedisTemplate redis,
                                   MediaScreenProperties properties) {
        this.leadMapper = leadMapper;
        this.snapshotMapper = snapshotMapper;
        this.partnerMapper = partnerMapper;
        this.adminUserApi = adminUserApi;
        this.deptApi = deptApi;
        this.maintenanceModeApi = maintenanceModeApi;
        this.redis = redis;
        this.properties = properties;
    }

    public MediaScreenRespVO stats(Long tenantId, boolean includePartTimers) {
        String key = "zsjos:media-screen:" + tenantId + ":stats:" + includePartTimers;
        MediaScreenRespVO cached = read(key, MediaScreenRespVO.class);
        if (cached != null) return cached;
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        MediaScreenRespVO value = aggregateRealtime(tenantId, now.toLocalDate(), now.toLocalDateTime(), includePartTimers);
        write(key, value, properties.getCache().getStatsTtlSeconds());
        return value;
    }

    public MediaScreenRespVO history(Long tenantId, LocalDate date, boolean includePartTimers) {
        String key = "zsjos:media-screen:" + tenantId + ":history:" + date + ":" + includePartTimers;
        MediaScreenRespVO cached = read(key, MediaScreenRespVO.class);
        if (cached != null) return cached;
        List<MediaScreenDailySnapshotDO> rows = snapshotMapper.selectByDate(tenantId, date);
        MediaScreenRespVO value = rows.isEmpty()
                ? emptyHistory(tenantId, date, includePartTimers)
                : aggregateHistory(tenantId, date, rows, includePartTimers);
        write(key, value, properties.getCache().getHistoryTtlSeconds());
        return value;
    }

    public Map<String, Object> maintenance(Long tenantId) {
        String key = "zsjos:media-screen:" + tenantId + ":maintenance";
        Map<String, Object> cached = read(key, Map.class);
        if (cached != null) return cached;
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("tenantId", tenantId);
        value.put("maintenanceEnabled", maintenanceModeApi.isEnabled());
        value.put("checkedAt", OffsetDateTime.now(ZONE));
        write(key, value, properties.getCache().getMaintenanceTtlSeconds());
        return value;
    }

    @cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit(action = "media-screen.freeze-snapshot", targetType = "media-screen-snapshot")
    @Transactional
    public void freeze(Long tenantId, LocalDate date) {
        if (!snapshotMapper.selectByDate(tenantId, date).isEmpty()) return;
        DepartmentScope scope = departmentScope();
        List<Long> departmentIds = scope.rootIds();
        if (departmentIds.isEmpty()) return;
        LocalDateTime cutoff = date.plusDays(1).atStartOfDay();
        List<MediaScreenContributionRow> rows = contributionRows(tenantId, date, cutoff, scope);
        Map<Long, DeptRespDTO> departments = scope.rootDepartments();
        List<AdminUserRespDTO> roster = activeRoster(scope);
        Map<Long, AdminUserRespDTO> rosterUsers = roster.stream().collect(Collectors.toMap(
                AdminUserRespDTO::getId, user -> user, (first, ignored) -> first, LinkedHashMap::new));
        Map<Long, PartnerDO> partners = partnerMap(rows);

        for (Long departmentId : departmentIds) {
            DeptRespDTO department = departments.get(departmentId);
            if (department == null) continue;
            List<MediaScreenContributionRow> directRows = rows.stream()
                    .filter(row -> DIRECT.equals(row.getContributionType())
                            && Objects.equals(departmentId, scope.rootFor(row.getSourceDeptId()))).toList();
            Set<Long> memberIds = roster.stream()
                    .filter(user -> Objects.equals(departmentId, scope.rootFor(user.getDeptId())))
                    .map(AdminUserRespDTO::getId).collect(Collectors.toCollection(LinkedHashSet::new));
            for (Long memberId : memberIds) {
                List<MediaScreenContributionRow> memberRows = filterMember(directRows, memberId);
                insertSnapshot(tenantId, date, DIRECT, department, memberId, rosterUsers.get(memberId), memberRows, null);
            }
            List<MediaScreenContributionRow> hiddenRows = directRows.stream()
                    .filter(row -> !memberIds.contains(row.getContributorUserId())).toList();
            if (memberIds.isEmpty() || !hiddenRows.isEmpty()) {
                insertSnapshot(tenantId, date, DIRECT, department, 0L, null, hiddenRows, null);
            }
        }

        Map<MemberKey, List<MediaScreenContributionRow>> partTimeGroups = rows.stream()
                .filter(row -> PART_TIME.equals(row.getContributionType()))
                .collect(Collectors.groupingBy(row -> new MemberKey(scope.rootFor(row.getSourceDeptId()),
                                row.getContributorUserId()),
                        LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<MemberKey, List<MediaScreenContributionRow>> entry : partTimeGroups.entrySet()) {
            DeptRespDTO department = departments.get(entry.getKey().departmentId());
            if (department == null) continue;
            List<MediaScreenRespVO.PartTimerDetail> details = partnerDetails(entry.getValue(), partners);
            insertSnapshot(tenantId, date, PART_TIME, department, entry.getKey().userId(),
                    rosterUsers.get(entry.getKey().userId()), entry.getValue(), JsonUtils.toJsonString(details));
        }
    }

    private MediaScreenRespVO aggregateRealtime(Long tenantId, LocalDate date, LocalDateTime now,
                                                 boolean includePartTimers) {
        DepartmentScope scope = departmentScope();
        List<MediaScreenContributionRow> rows = contributionRows(tenantId, date, now, scope);
        Map<Long, AdminUserRespDTO> users = userMap(rows);
        List<AdminUserRespDTO> roster = activeRoster(scope);
        roster.forEach(user -> users.put(user.getId(), user));
        Set<Long> rosterUserIds = roster.stream().map(AdminUserRespDTO::getId).collect(Collectors.toSet());
        Map<Long, PartnerDO> partners = partnerMap(rows);

        List<MediaScreenRespVO.Department> directDepartments = buildDirectDepartments(
                scope, rows, roster);
        MediaScreenRespVO.Department companion = includePartTimers
                ? buildCompanion(rows, users, partners, rosterUserIds) : null;
        MediaScreenRespVO.Metrics summary = sumDepartments(directDepartments);
        if (companion != null) add(summary, companion.getMetrics());

        MediaScreenRespVO result = base(tenantId, includePartTimers);
        result.setSummary(summary);
        result.setDepartments(directDepartments);
        result.setPartTimeCompanionDepartment(companion);
        result.setTodayStar(todayStar(rows, users, rosterUserIds, includePartTimers, tenantId, date));
        result.setYesterdayChampion(yesterdayChampion(tenantId, date.minusDays(1), includePartTimers));
        result.setTrend(buildTrend(tenantId, date, now, scope.departmentIds(), includePartTimers));
        result.setSeries(buildSeries(tenantId, date, scope.departmentIds(), includePartTimers));
        applyLegacy(result);
        return result;
    }

    private List<MediaScreenContributionRow> contributionRows(Long tenantId, LocalDate date, LocalDateTime cutoff,
                                                               DepartmentScope scope) {
        return leadMapper.countMediaScreenContributions(tenantId, date.atStartOfDay(),
                        date.with(DayOfWeek.MONDAY).atStartOfDay(), date.withDayOfMonth(1).atStartOfDay(), cutoff)
                .stream().filter(row -> scope.rootFor(row.getSourceDeptId()) != null).toList();
    }

    private List<MediaScreenRespVO.Department> buildDirectDepartments(DepartmentScope scope,
                                                                      List<MediaScreenContributionRow> allRows,
                                                                      List<AdminUserRespDTO> roster) {
        List<MediaScreenRespVO.Department> result = new ArrayList<>();
        for (Long departmentId : scope.rootIds()) {
            DeptRespDTO source = scope.rootDepartments().get(departmentId);
            if (source == null) continue;
            MediaScreenRespVO.Department department = department(departmentId, source.getName(),
                    supervisorSubtitle(source));
            List<MediaScreenContributionRow> rows = allRows.stream()
                    .filter(row -> DIRECT.equals(row.getContributionType())
                            && Objects.equals(departmentId, scope.rootFor(row.getSourceDeptId()))).toList();
            rows.forEach(row -> add(department.getMetrics(), metrics(row)));
            roster.stream().filter(user -> Objects.equals(departmentId, scope.rootFor(user.getDeptId())))
                    .forEach(user -> department.getMembers().add(member(user.getId(), user.getNickname(),
                            source.getName(), sum(filterMember(rows, user.getId())), false, null)));
            result.add(department);
        }
        return result;
    }

    private MediaScreenRespVO.Department buildCompanion(List<MediaScreenContributionRow> allRows,
                                                         Map<Long, AdminUserRespDTO> users,
                                                         Map<Long, PartnerDO> partners,
                                                         Set<Long> rosterUserIds) {
        List<MediaScreenContributionRow> rows = allRows.stream()
                .filter(row -> PART_TIME.equals(row.getContributionType())).toList();
        if (rows.isEmpty()) return null;
        MediaScreenRespVO.Department department = department(null, "兼职陪跑", "按提交时员工归属统计");
        rows.forEach(row -> add(department.getMetrics(), metrics(row)));
        Map<Long, List<MediaScreenContributionRow>> groups = rows.stream().collect(Collectors.groupingBy(
                MediaScreenContributionRow::getContributorUserId, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<Long, List<MediaScreenContributionRow>> entry : groups.entrySet()) {
            if (!rosterUserIds.contains(entry.getKey()) || !isEnabled(users.get(entry.getKey()))) continue;
            String name = snapshotName(entry.getValue());
            if (name == null) continue;
            String departmentName = distinctDepartmentName(entry.getValue());
            department.getMembers().add(member(entry.getKey(), name, departmentName, sum(entry.getValue()), false,
                    partnerDetails(entry.getValue(), partners)));
        }
        return department;
    }

    private List<MediaScreenRespVO.PartTimerDetail> partnerDetails(List<MediaScreenContributionRow> rows,
                                                                   Map<Long, PartnerDO> partners) {
        List<MediaScreenRespVO.PartTimerDetail> result = new ArrayList<>();
        for (MediaScreenContributionRow row : rows) {
            PartnerDO partner = partners.get(row.getProviderOwnerId());
            if (partner == null || !"enabled".equals(partner.getStatus()) || row.getProviderOwnerName() == null) continue;
            MediaScreenRespVO.PartTimerDetail detail = new MediaScreenRespVO.PartTimerDetail();
            detail.setPartnerId(row.getProviderOwnerId());
            detail.setName(row.getProviderOwnerName());
            copy(detail, metrics(row));
            result.add(detail);
        }
        result.sort(Comparator.comparingLong(MediaScreenRespVO.PartTimerDetail::getToday).reversed()
                .thenComparing(MediaScreenRespVO.PartTimerDetail::getName));
        return result;
    }

    private MediaScreenRespVO aggregateHistory(Long tenantId, LocalDate date,
                                                List<MediaScreenDailySnapshotDO> rows,
                                                boolean includePartTimers) {
        MediaScreenRespVO result = base(tenantId, includePartTimers);
        result.setAvailable(true);
        result.setSnapshotDate(date);
        result.setSource("persisted_snapshot_v2");
        result.setSnapshotCreatedAt(rows.get(0).getCreateTime());
        Map<Long, List<MediaScreenDailySnapshotDO>> direct = rows.stream()
                .filter(row -> DIRECT.equals(row.getContributionType()))
                .collect(Collectors.groupingBy(MediaScreenDailySnapshotDO::getDepartmentId,
                        LinkedHashMap::new, Collectors.toList()));
        List<MediaScreenRespVO.Department> departments = new ArrayList<>();
        for (Long departmentId : departmentIds()) {
            List<MediaScreenDailySnapshotDO> departmentRows = direct.get(departmentId);
            if (departmentRows == null || departmentRows.isEmpty()) continue;
            String name = departmentRows.get(0).getDepartmentName();
            String supervisor = departmentRows.get(0).getSupervisorName();
            MediaScreenRespVO.Department department = department(departmentId, name,
                    supervisor == null ? "" : "主管 " + supervisor);
            for (MediaScreenDailySnapshotDO row : departmentRows) {
                MediaScreenRespVO.Metrics metrics = metrics(row);
                add(department.getMetrics(), metrics);
                if (Boolean.TRUE.equals(row.getMemberEnabled()) && row.getMemberId() != 0L
                        && row.getMemberName() != null && !row.getMemberName().isBlank()) {
                    department.getMembers().add(member(row.getMemberId(), row.getMemberName(), name,
                            metrics, false, null));
                }
            }
            departments.add(department);
        }
        MediaScreenRespVO.Department companion = includePartTimers ? historyCompanion(rows) : null;
        MediaScreenRespVO.Metrics summary = sumDepartments(departments);
        if (companion != null) add(summary, companion.getMetrics());
        result.setSummary(summary);
        result.setDepartments(departments);
        result.setPartTimeCompanionDepartment(companion);
        result.setTodayStar(starFromSnapshot(rows, includePartTimers));
        result.setYesterdayChampion(championFromSnapshot(rows, includePartTimers));
        result.setTrend(emptyTrend());
        MediaScreenRespVO.Series series = new MediaScreenRespVO.Series();
        series.setSubmitted(List.of());
        series.setValid(List.of());
        result.setSeries(series);
        applyLegacy(result);
        return result;
    }

    private MediaScreenRespVO.Department historyCompanion(List<MediaScreenDailySnapshotDO> rows) {
        List<MediaScreenDailySnapshotDO> partRows = rows.stream()
                .filter(row -> PART_TIME.equals(row.getContributionType())).toList();
        if (partRows.isEmpty()) return null;
        MediaScreenRespVO.Department department = department(null, "兼职陪跑", "按快照日账号状态统计");
        for (MediaScreenDailySnapshotDO row : partRows) {
            MediaScreenRespVO.Metrics metrics = metrics(row);
            add(department.getMetrics(), metrics);
            if (!Boolean.TRUE.equals(row.getMemberEnabled()) || row.getMemberName() == null
                    || row.getMemberName().isBlank()) continue;
            List<MediaScreenRespVO.PartTimerDetail> details = row.getPartnerDetailsJson() == null ? null
                    : JsonUtils.parseArray(row.getPartnerDetailsJson(), MediaScreenRespVO.PartTimerDetail.class);
            if (details == null) details = List.of();
            department.getMembers().add(member(row.getMemberId(), row.getMemberName(), row.getDepartmentName(),
                    metrics, false, details));
        }
        return department;
    }

    private MediaScreenRespVO emptyHistory(Long tenantId, LocalDate date, boolean includePartTimers) {
        MediaScreenRespVO result = base(tenantId, includePartTimers);
        result.setAvailable(false);
        result.setSnapshotDate(date);
        result.setSource("persisted_snapshot_v2");
        result.setSummary(new MediaScreenRespVO.Metrics());
        result.setDepartments(List.of());
        result.setPartTimeCompanionDepartment(null);
        result.setTrend(emptyTrend());
        MediaScreenRespVO.Series series = new MediaScreenRespVO.Series();
        series.setSubmitted(List.of());
        series.setValid(List.of());
        result.setSeries(series);
        result.setDepartmentRanking(List.of());
        result.setMemberRanking(List.of());
        MediaScreenRespVO.HistorySnapshot snapshot = new MediaScreenRespVO.HistorySnapshot();
        snapshot.setAvailable(false);
        snapshot.setSnapshotDate(date);
        result.setHistorySnapshot(snapshot);
        return result;
    }

    private MediaScreenRespVO.Star todayStar(List<MediaScreenContributionRow> rows,
                                              Map<Long, AdminUserRespDTO> users,
                                              Set<Long> rosterUserIds,
                                              boolean includePartTimers, Long tenantId, LocalDate date) {
        Map<Long, List<MediaScreenContributionRow>> groups = eligibleRows(rows, includePartTimers).stream()
                .filter(row -> rosterUserIds.contains(row.getContributorUserId())
                        && isEnabled(users.get(row.getContributorUserId())) && snapshotName(List.of(row)) != null)
                .collect(Collectors.groupingBy(MediaScreenContributionRow::getContributorUserId));
        List<Map.Entry<Long, List<MediaScreenContributionRow>>> ranked = groups.entrySet().stream()
                .sorted(Comparator.<Map.Entry<Long, List<MediaScreenContributionRow>>>comparingLong(
                        entry -> sum(entry.getValue()).getToday()).reversed().thenComparing(Map.Entry::getKey)).toList();
        if (ranked.isEmpty() || sum(ranked.get(0).getValue()).getToday() == 0) return null;
        Map.Entry<Long, List<MediaScreenContributionRow>> winner = ranked.get(0);
        MediaScreenRespVO.Metrics metrics = sum(winner.getValue());
        MediaScreenRespVO.Star star = new MediaScreenRespVO.Star();
        star.setName(snapshotName(winner.getValue()));
        star.setDeptName(distinctDepartmentName(winner.getValue()));
        star.setToday(metrics.getToday());
        star.setLeadCount(metrics.getToday());
        star.setRankToday(1);
        star.setRank(1);
        star.setIncludesPartTime(winner.getValue().stream().anyMatch(row -> PART_TIME.equals(row.getContributionType())));
        List<MediaScreenDailySnapshotDO> yesterday = snapshotMapper.selectByDate(tenantId, date.minusDays(1));
        List<MemberSnapshot> yesterdayRanking = rankSnapshots(yesterday, includePartTimers);
        for (int i = 0; i < yesterdayRanking.size(); i++) {
            if (Objects.equals(yesterdayRanking.get(i).userId(), winner.getKey())) {
                star.setYesterday(yesterdayRanking.get(i).metrics().getToday());
                star.setRankYesterday(i + 1);
                break;
            }
        }
        return star;
    }

    private MediaScreenRespVO.Champion yesterdayChampion(Long tenantId, LocalDate date,
                                                          boolean includePartTimers) {
        List<MemberSnapshot> ranked = rankSnapshots(snapshotMapper.selectByDate(tenantId, date), includePartTimers);
        if (ranked.isEmpty() || ranked.get(0).metrics().getToday() == 0) return null;
        return champion(ranked.get(0));
    }

    private MediaScreenRespVO.Star starFromSnapshot(List<MediaScreenDailySnapshotDO> rows,
                                                    boolean includePartTimers) {
        List<MemberSnapshot> ranked = rankSnapshots(rows, includePartTimers);
        if (ranked.isEmpty() || ranked.get(0).metrics().getToday() == 0) return null;
        MemberSnapshot winner = ranked.get(0);
        MediaScreenRespVO.Star star = new MediaScreenRespVO.Star();
        star.setName(winner.name());
        star.setDeptName(winner.departmentName());
        star.setToday(winner.metrics().getToday());
        star.setLeadCount(winner.metrics().getToday());
        star.setRankToday(1);
        star.setRank(1);
        star.setIncludesPartTime(winner.includesPartTime());
        return star;
    }

    private MediaScreenRespVO.Champion championFromSnapshot(List<MediaScreenDailySnapshotDO> rows,
                                                            boolean includePartTimers) {
        List<MemberSnapshot> ranked = rankSnapshots(rows, includePartTimers);
        return ranked.isEmpty() || ranked.get(0).metrics().getToday() == 0 ? null : champion(ranked.get(0));
    }

    private List<MemberSnapshot> rankSnapshots(List<MediaScreenDailySnapshotDO> rows, boolean includePartTimers) {
        Map<Long, List<MediaScreenDailySnapshotDO>> groups = rows.stream()
                .filter(row -> Boolean.TRUE.equals(row.getMemberEnabled()) && row.getMemberId() != 0L)
                .filter(row -> includePartTimers || DIRECT.equals(row.getContributionType()))
                .collect(Collectors.groupingBy(MediaScreenDailySnapshotDO::getMemberId));
        return groups.entrySet().stream().map(entry -> {
                    List<MediaScreenDailySnapshotDO> memberRows = entry.getValue();
                    MediaScreenRespVO.Metrics metrics = new MediaScreenRespVO.Metrics();
                    memberRows.forEach(row -> add(metrics, metrics(row)));
                    String name = memberRows.stream().map(MediaScreenDailySnapshotDO::getMemberName)
                            .filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
                    Set<String> departments = memberRows.stream().map(MediaScreenDailySnapshotDO::getDepartmentName)
                            .filter(Objects::nonNull).collect(Collectors.toSet());
                    String departmentName = departments.size() == 1 ? departments.iterator().next() : "跨部门";
                    boolean partTime = memberRows.stream().anyMatch(row -> PART_TIME.equals(row.getContributionType()));
                    return new MemberSnapshot(entry.getKey(), name, departmentName, metrics, partTime);
                }).filter(item -> item.name() != null)
                .sorted(Comparator.comparingLong((MemberSnapshot item) -> item.metrics().getToday()).reversed()
                        .thenComparing(MemberSnapshot::userId)).toList();
    }

    private MediaScreenRespVO.Champion champion(MemberSnapshot winner) {
        MediaScreenRespVO.Champion champion = new MediaScreenRespVO.Champion();
        champion.setName(winner.name());
        champion.setDeptName(winner.departmentName());
        champion.setCount(winner.metrics().getToday());
        champion.setIncludesPartTime(winner.includesPartTime());
        return champion;
    }

    private MediaScreenRespVO.Trend buildTrend(Long tenantId, LocalDate date, LocalDateTime now,
                                                Set<Long> departmentIds, boolean includePartTimers) {
        int slots = Math.max(1, (now.getHour() * 60 + now.getMinute()) / 10 + 1);
        LocalDateTime today = date.atStartOfDay();
        LocalDateTime yesterday = date.minusDays(1).atStartOfDay();
        List<Long> todayValues = cumulative(leadMapper.countMediaScreenTenMinuteContributions(
                tenantId, today, now), departmentIds, includePartTimers, slots);
        List<Long> yesterdayValues = cumulative(leadMapper.countMediaScreenTenMinuteContributions(
                tenantId, yesterday, yesterday.plusMinutes(slots * 10L)), departmentIds, includePartTimers, slots);
        MediaScreenRespVO.Trend trend = new MediaScreenRespVO.Trend();
        trend.setToday(todayValues);
        trend.setYesterday(yesterdayValues);
        trend.setStepMinutes(10);
        return trend;
    }

    private MediaScreenRespVO.Series buildSeries(Long tenantId, LocalDate date, Set<Long> departmentIds,
                                                  boolean includePartTimers) {
        LocalDate from = date.minusDays(13);
        List<MediaScreenTimedContributionRow> rows = leadMapper.countMediaScreenDailyContributions(
                tenantId, from.atStartOfDay(), date.plusDays(1).atStartOfDay());
        Map<LocalDate, long[]> totals = new HashMap<>();
        for (MediaScreenTimedContributionRow row : rows) {
            if (!timedInScope(row, departmentIds, includePartTimers)) continue;
            long[] values = totals.computeIfAbsent(LocalDate.parse(row.getBucket()), key -> new long[2]);
            values[0] += number(row.getSubmittedCount());
            values[1] += number(row.getValidCount());
        }
        List<Long> submitted = new ArrayList<>();
        List<Long> valid = new ArrayList<>();
        for (int index = 0; index < 14; index++) {
            long[] values = totals.getOrDefault(from.plusDays(index), new long[2]);
            submitted.add(values[0]);
            valid.add(values[1]);
        }
        MediaScreenRespVO.Series series = new MediaScreenRespVO.Series();
        series.setSubmitted(submitted);
        series.setValid(valid);
        return series;
    }

    private List<Long> cumulative(List<MediaScreenTimedContributionRow> rows, Set<Long> departmentIds,
                                  boolean includePartTimers, int slots) {
        long[] buckets = new long[slots];
        for (MediaScreenTimedContributionRow row : rows) {
            if (!timedInScope(row, departmentIds, includePartTimers)) continue;
            int bucket = Integer.parseInt(row.getBucket());
            if (bucket >= 0 && bucket < slots) buckets[bucket] += number(row.getSubmittedCount());
        }
        List<Long> result = new ArrayList<>(slots);
        long total = 0;
        for (long bucket : buckets) {
            total += bucket;
            result.add(total);
        }
        return result;
    }

    private boolean timedInScope(MediaScreenTimedContributionRow row, Set<Long> departmentIds,
                                 boolean includePartTimers) {
        return departmentIds.contains(row.getSourceDeptId())
                && (includePartTimers || DIRECT.equals(row.getContributionType()));
    }

    private void insertSnapshot(Long tenantId, LocalDate date, String type, DeptRespDTO department,
                                Long memberId, AdminUserRespDTO currentUser,
                                List<MediaScreenContributionRow> rows, String partnerDetailsJson) {
        MediaScreenRespVO.Metrics metrics = sum(rows);
        String memberName = snapshotName(rows);
        if (memberName == null && currentUser != null) memberName = currentUser.getNickname();
        AdminUserRespDTO supervisor = department.getLeaderUserId() == null ? null
                : adminUserApi.getUser(department.getLeaderUserId());
        snapshotMapper.insertIgnore(tenantId, new MediaScreenDailySnapshotDO()
                .setSnapshotDate(date).setContributionType(type).setDepartmentId(department.getId())
                .setDepartmentName(department.getName()).setSupervisorId(department.getLeaderUserId())
                .setSupervisorName(supervisor == null ? null : supervisor.getNickname())
                .setMemberId(memberId).setMemberName(memberName == null ? "" : memberName)
                .setMemberEnabled(memberId != 0L && isEnabled(currentUser))
                .setTodayCount((int) metrics.getToday()).setWeekCount((int) metrics.getWeek())
                .setMonthTotal((int) metrics.getMonthTotal()).setMonthEffective((int) metrics.getMonthEffective())
                .setPartnerDetailsJson(partnerDetailsJson));
    }

    private MediaScreenRespVO base(Long tenantId, boolean includePartTimers) {
        MediaScreenRespVO result = new MediaScreenRespVO();
        LocalDateTime now = LocalDateTime.now(ZONE);
        result.setTenantId(tenantId);
        result.setGeneratedAt(now);
        result.setUpdatedAt(now.atZone(ZONE).toInstant().toEpochMilli());
        result.setRefreshIntervalSeconds((int) properties.getCache().getRefreshIntervalSeconds());
        result.setPartTimeIncluded(includePartTimers);
        MediaScreenRespVO.PartTimer partTimer = new MediaScreenRespVO.PartTimer();
        partTimer.setEnabled(includePartTimers);
        partTimer.setItems(List.of());
        result.setPartTimer(partTimer);
        return result;
    }

    private void applyLegacy(MediaScreenRespVO result) {
        result.setTotalLeads(result.getSummary() == null ? 0 : result.getSummary().getMonthTotal());
        result.setDepartmentRanking(rankDepartments(result.getDepartments()));
        List<MediaScreenRespVO.Department> memberSources = new ArrayList<>(result.getDepartments());
        if (result.getPartTimeCompanionDepartment() != null) memberSources.add(result.getPartTimeCompanionDepartment());
        result.setMemberRanking(rankMembers(memberSources));
        MediaScreenRespVO.HistorySnapshot snapshot = new MediaScreenRespVO.HistorySnapshot();
        snapshot.setAvailable(result.isAvailable());
        snapshot.setSnapshotDate(result.getSnapshotDate());
        snapshot.setTotalLeads(result.getTotalLeads());
        result.setHistorySnapshot(snapshot);
    }

    private List<MediaScreenRespVO.RankItem> rankDepartments(List<MediaScreenRespVO.Department> departments) {
        List<MediaScreenRespVO.RankItem> result = departments.stream().map(department -> {
            MediaScreenRespVO.RankItem item = new MediaScreenRespVO.RankItem();
            item.setName(department.getName());
            item.setLeadCount(department.getMetrics().getMonthTotal());
            return item;
        }).sorted(Comparator.comparingLong(MediaScreenRespVO.RankItem::getLeadCount).reversed()).toList();
        for (int index = 0; index < result.size(); index++) result.get(index).setRank(index + 1);
        return result;
    }

    private List<MediaScreenRespVO.RankItem> rankMembers(List<MediaScreenRespVO.Department> departments) {
        List<MediaScreenRespVO.RankItem> result = departments.stream().flatMap(value -> value.getMembers().stream())
                .map(member -> {
                    MediaScreenRespVO.RankItem item = new MediaScreenRespVO.RankItem();
                    item.setName(member.getName());
                    item.setLeadCount(member.getMonthTotal());
                    return item;
                }).sorted(Comparator.comparingLong(MediaScreenRespVO.RankItem::getLeadCount).reversed()).toList();
        for (int index = 0; index < result.size(); index++) result.get(index).setRank(index + 1);
        return result;
    }

    private List<AdminUserRespDTO> activeRoster(DepartmentScope scope) {
        if (scope.departmentIds().isEmpty()) return List.of();
        return adminUserApi.getUserListByDeptIds(scope.departmentIds()).stream()
                .filter(this::isEnabled).filter(user -> scope.rootFor(user.getDeptId()) != null)
                .collect(Collectors.toMap(AdminUserRespDTO::getId, user -> user, (first, ignored) -> first,
                        LinkedHashMap::new)).values().stream().toList();
    }

    private Map<Long, AdminUserRespDTO> userMap(List<MediaScreenContributionRow> rows) {
        Set<Long> ids = rows.stream().map(MediaScreenContributionRow::getContributorUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        return ids.isEmpty() ? new HashMap<>() : new HashMap<>(adminUserApi.getUserMap(ids));
    }

    private Map<Long, PartnerDO> partnerMap(List<MediaScreenContributionRow> rows) {
        List<Long> ids = rows.stream().filter(row -> PART_TIME.equals(row.getContributionType()))
                .map(MediaScreenContributionRow::getProviderOwnerId).filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of() : partnerMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(PartnerDO::getId, value -> value));
    }

    private List<Long> departmentIds() {
        return properties.getNewMedia().getDepartmentIds() == null ? List.of()
                : properties.getNewMedia().getDepartmentIds().stream().filter(Objects::nonNull).distinct().toList();
    }

    private DepartmentScope departmentScope() {
        List<Long> configuredRootIds = departmentIds();
        if (configuredRootIds.isEmpty()) return DepartmentScope.empty();
        Map<Long, DeptRespDTO> roots = new LinkedHashMap<>(deptApi.getDeptMap(configuredRootIds));
        List<Long> rootIds = configuredRootIds.stream().filter(roots::containsKey).toList();
        if (rootIds.isEmpty()) return DepartmentScope.empty();
        Map<Long, DeptRespDTO> departments = new LinkedHashMap<>(roots);
        deptApi.getChildDeptList(rootIds).forEach(department -> departments.put(department.getId(), department));
        Set<Long> configuredRoots = new LinkedHashSet<>(rootIds);
        Map<Long, Long> rootByDepartment = new LinkedHashMap<>();
        departments.keySet().forEach(departmentId -> rootByDepartment.put(departmentId,
                findNearestRoot(departmentId, configuredRoots, departments)));
        rootByDepartment.values().removeIf(Objects::isNull);
        return new DepartmentScope(rootIds, roots, rootByDepartment);
    }

    private static Long findNearestRoot(Long departmentId, Set<Long> configuredRoots,
                                        Map<Long, DeptRespDTO> departments) {
        Set<Long> visited = new HashSet<>();
        Long current = departmentId;
        while (current != null && visited.add(current)) {
            if (configuredRoots.contains(current)) return current;
            DeptRespDTO department = departments.get(current);
            current = department == null ? null : department.getParentId();
        }
        return null;
    }

    private String supervisorSubtitle(DeptRespDTO department) {
        if (department.getLeaderUserId() == null) return "";
        AdminUserRespDTO leader = adminUserApi.getUser(department.getLeaderUserId());
        return leader == null ? "" : "主管 " + leader.getNickname();
    }

    private boolean isEnabled(AdminUserRespDTO user) {
        return user != null && CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus());
    }

    private List<MediaScreenContributionRow> eligibleRows(List<MediaScreenContributionRow> rows,
                                                            boolean includePartTimers) {
        return rows.stream().filter(row -> includePartTimers || DIRECT.equals(row.getContributionType())).toList();
    }

    private static List<MediaScreenContributionRow> filterMember(List<MediaScreenContributionRow> rows, Long memberId) {
        return rows.stream().filter(row -> Objects.equals(memberId, row.getContributorUserId())).toList();
    }

    private static String snapshotName(Collection<MediaScreenContributionRow> rows) {
        return rows.stream().map(MediaScreenContributionRow::getContributorName)
                .filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
    }

    private static String distinctDepartmentName(Collection<MediaScreenContributionRow> rows) {
        Set<String> names = rows.stream().map(MediaScreenContributionRow::getDepartmentName)
                .filter(value -> value != null && !value.isBlank()).collect(Collectors.toSet());
        return names.size() == 1 ? names.iterator().next() : "跨部门";
    }

    private static MediaScreenRespVO.Department department(Long id, String name, String subtitle) {
        MediaScreenRespVO.Department result = new MediaScreenRespVO.Department();
        result.setDepartmentId(id);
        result.setName(name);
        result.setSubtitle(subtitle);
        result.setMetrics(new MediaScreenRespVO.Metrics());
        result.setMembers(new ArrayList<>());
        return result;
    }

    private static MediaScreenRespVO.Member member(Long id, String name, String departmentName,
                                                    MediaScreenRespVO.Metrics metrics, boolean disabled,
                                                    List<MediaScreenRespVO.PartTimerDetail> details) {
        MediaScreenRespVO.Member result = new MediaScreenRespVO.Member();
        result.setUserId(id);
        result.setName(name);
        result.setDepartmentName(departmentName);
        result.setDisabled(disabled);
        result.setPartTimers(details);
        result.setToday(metrics.getToday());
        result.setWeek(metrics.getWeek());
        result.setMonthTotal(metrics.getMonthTotal());
        result.setMonthEffective(metrics.getMonthEffective());
        return result;
    }

    private static MediaScreenRespVO.Metrics metrics(MediaScreenContributionRow row) {
        MediaScreenRespVO.Metrics result = new MediaScreenRespVO.Metrics();
        result.setToday(number(row.getTodayCount()));
        result.setWeek(number(row.getWeekCount()));
        result.setMonthTotal(number(row.getMonthTotal()));
        result.setMonthEffective(number(row.getMonthEffective()));
        return result;
    }

    private static MediaScreenRespVO.Metrics metrics(MediaScreenDailySnapshotDO row) {
        MediaScreenRespVO.Metrics result = new MediaScreenRespVO.Metrics();
        result.setToday(number(row.getTodayCount()));
        result.setWeek(number(row.getWeekCount()));
        result.setMonthTotal(number(row.getMonthTotal()));
        result.setMonthEffective(number(row.getMonthEffective()));
        return result;
    }

    private static MediaScreenRespVO.Metrics sum(Collection<MediaScreenContributionRow> rows) {
        MediaScreenRespVO.Metrics result = new MediaScreenRespVO.Metrics();
        rows.forEach(row -> add(result, metrics(row)));
        return result;
    }

    private static MediaScreenRespVO.Metrics sumDepartments(List<MediaScreenRespVO.Department> departments) {
        MediaScreenRespVO.Metrics result = new MediaScreenRespVO.Metrics();
        departments.forEach(department -> add(result, department.getMetrics()));
        return result;
    }

    private static void add(MediaScreenRespVO.Metrics target, MediaScreenRespVO.Metrics value) {
        target.setToday(target.getToday() + value.getToday());
        target.setWeek(target.getWeek() + value.getWeek());
        target.setMonthTotal(target.getMonthTotal() + value.getMonthTotal());
        target.setMonthEffective(target.getMonthEffective() + value.getMonthEffective());
    }

    private static void copy(MediaScreenRespVO.PartTimerDetail target, MediaScreenRespVO.Metrics value) {
        target.setToday(value.getToday());
        target.setWeek(value.getWeek());
        target.setMonthTotal(value.getMonthTotal());
        target.setMonthEffective(value.getMonthEffective());
    }

    private static long number(Number value) {
        return value == null ? 0 : value.longValue();
    }

    private static MediaScreenRespVO.Trend emptyTrend() {
        MediaScreenRespVO.Trend trend = new MediaScreenRespVO.Trend();
        trend.setToday(List.of());
        trend.setYesterday(List.of());
        trend.setStepMinutes(10);
        return trend;
    }

    private <T> T read(String key, Class<T> type) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? null : JsonUtils.parseObject(value, type);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void write(String key, Object value, long ttlSeconds) {
        try {
            redis.opsForValue().set(key, JsonUtils.toJsonString(value), ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Redis is an optimization; the public screen remains available from authoritative APIs.
        }
    }

    private record MemberKey(Long departmentId, Long userId) {}
    private record DepartmentScope(List<Long> rootIds, Map<Long, DeptRespDTO> rootDepartments,
                                   Map<Long, Long> rootByDepartment) {
        static DepartmentScope empty() {
            return new DepartmentScope(List.of(), Map.of(), Map.of());
        }

        Long rootFor(Long departmentId) {
            return rootByDepartment.get(departmentId);
        }

        Set<Long> departmentIds() {
            return rootByDepartment.keySet();
        }
    }
    private record MemberSnapshot(Long userId, String name, String departmentName,
                                  MediaScreenRespVO.Metrics metrics, boolean includesPartTime) {}
}
