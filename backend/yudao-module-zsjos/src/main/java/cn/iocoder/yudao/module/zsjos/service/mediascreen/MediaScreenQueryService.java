package cn.iocoder.yudao.module.zsjos.service.mediascreen;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.pub.mediascreen.vo.MediaScreenRespVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.framework.mediascreen.MediaScreenProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class MediaScreenQueryService {
    private final LeadMapper leadMapper;
    private final AdminUserApi adminUserApi;
    private final DeptApi deptApi;
    private final MaintenanceModeApi maintenanceModeApi;
    private final StringRedisTemplate redis;
    private final MediaScreenProperties properties;

    public MediaScreenQueryService(LeadMapper leadMapper, AdminUserApi adminUserApi, DeptApi deptApi,
                                   MaintenanceModeApi maintenanceModeApi, StringRedisTemplate redis,
                                   MediaScreenProperties properties) {
        this.leadMapper = leadMapper; this.adminUserApi = adminUserApi; this.deptApi = deptApi;
        this.maintenanceModeApi = maintenanceModeApi; this.redis = redis; this.properties = properties;
    }

    public MediaScreenRespVO stats(Long tenantId, boolean includePartTimers) {
        String key = "zsjos:media-screen:" + tenantId + ":stats:" + includePartTimers;
        MediaScreenRespVO cached = read(key, MediaScreenRespVO.class);
        if (cached != null) return cached;
        MediaScreenRespVO value = aggregate(tenantId, includePartTimers);
        write(key, value, properties.getCache().getStatsTtlSeconds());
        return value;
    }

    public MediaScreenRespVO history(Long tenantId, LocalDate date, boolean includePartTimers) {
        MediaScreenRespVO result = new MediaScreenRespVO();
        result.setTenantId(tenantId); result.setSnapshotDate(date); result.setAvailable(false);
        result.setSource("persisted_snapshot"); result.setGeneratedAt(LocalDateTime.now());
        result.setDepartmentRanking(List.of()); result.setMemberRanking(List.of()); result.setTrend(List.of());
        MediaScreenRespVO.HistorySnapshot snapshot = new MediaScreenRespVO.HistorySnapshot();
        snapshot.setAvailable(false); snapshot.setSnapshotDate(date); result.setHistorySnapshot(snapshot);
        return result;
    }

    public Map<String, Object> maintenance(Long tenantId) {
        String key = "zsjos:media-screen:" + tenantId + ":maintenance";
        Map<String, Object> cached = read(key, Map.class);
        if (cached != null) return cached;
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("tenantId", tenantId); value.put("maintenanceEnabled", maintenanceModeApi.isEnabled());
        value.put("checkedAt", OffsetDateTime.now());
        write(key, value, properties.getCache().getMaintenanceTtlSeconds());
        return value;
    }

    private MediaScreenRespVO aggregate(Long tenantId, boolean includePartTimers) {
        MediaScreenRespVO r = new MediaScreenRespVO(); r.setTenantId(tenantId); r.setGeneratedAt(LocalDateTime.now());
        r.setTotalLeads(leadMapper.countForMediaScreen(tenantId));
        List<Map<String, Object>> deptRows = leadMapper.countMediaScreenDepartments(tenantId);
        List<Long> deptIds = ids(deptRows); Map<Long, DeptRespDTO> depts = deptIds.isEmpty() ? Map.of() : deptApi.getDeptMap(deptIds);
        r.setDepartmentRanking(rank(deptRows, id -> Optional.ofNullable(depts.get(id)).map(DeptRespDTO::getName).orElse("未分配")));
        List<Map<String, Object>> memberRows = leadMapper.countMediaScreenMembers(tenantId);
        List<Long> userIds = ids(memberRows); Map<Long, AdminUserRespDTO> users = userIds.isEmpty() ? Map.of() : adminUserApi.getUserMap(userIds);
        r.setMemberRanking(rank(memberRows, id -> Optional.ofNullable(users.get(id)).map(AdminUserRespDTO::getNickname).orElse("未知成员")));
        r.setTodayStar(r.getMemberRanking().isEmpty() ? null : r.getMemberRanking().get(0));
        MediaScreenRespVO.PartTimer part = new MediaScreenRespVO.PartTimer(); part.setEnabled(includePartTimers); part.setItems(includePartTimers ? r.getMemberRanking() : List.of()); r.setPartTimer(part);
        LocalDate today = LocalDate.now(); LocalDateTime from = today.minusDays(6).atStartOfDay();
        r.setTrend(leadMapper.countMediaScreenTrend(tenantId, from, today.plusDays(1).atStartOfDay()).stream().map(x -> { MediaScreenRespVO.TrendItem t = new MediaScreenRespVO.TrendItem(); t.setDate(LocalDate.parse(String.valueOf(x.get("bucket")))); t.setLeadCount(number(x.get("total"))); return t; }).toList());
        return r;
    }

    private List<MediaScreenRespVO.RankItem> rank(List<Map<String, Object>> rows, java.util.function.Function<Long, String> name) {
        List<MediaScreenRespVO.RankItem> result = new ArrayList<>(); int rank = 1;
        for (Map<String, Object> row : rows) { MediaScreenRespVO.RankItem item = new MediaScreenRespVO.RankItem(); item.setName(name.apply(longValue(row.get("bucket")))); item.setLeadCount(number(row.get("total"))); item.setRank(rank++); result.add(item); }
        return result;
    }
    private List<Long> ids(List<Map<String, Object>> rows) { return rows.stream().map(x -> longValue(x.get("bucket"))).filter(Objects::nonNull).toList(); }
    private static Long longValue(Object value) { return value instanceof Number n ? n.longValue() : value == null ? null : Long.valueOf(value.toString()); }
    private static long number(Object value) { return value instanceof Number n ? n.longValue() : value == null ? 0 : Long.parseLong(value.toString()); }
    private <T> T read(String key, Class<T> type) { try { String json = redis.opsForValue().get(key); return json == null ? null : JsonUtils.parseObject(json, type); } catch (Exception ignored) { return null; } }
    private void write(String key, Object value, long ttl) { try { redis.opsForValue().set(key, JsonUtils.toJsonString(value), ttl, TimeUnit.SECONDS); } catch (Exception ignored) { } }
}
