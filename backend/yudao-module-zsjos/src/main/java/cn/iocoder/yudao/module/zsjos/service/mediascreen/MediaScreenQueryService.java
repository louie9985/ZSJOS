package cn.iocoder.yudao.module.zsjos.service.mediascreen;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.maintenance.MaintenanceModeApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.pub.mediascreen.vo.MediaScreenRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.mediascreen.MediaScreenDailySnapshotDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.MediaScreenContributionRow;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.MediaScreenTimedContributionRow;
import cn.iocoder.yudao.module.zsjos.dal.mysql.mediascreen.MediaScreenDailySnapshotMapper;
import cn.iocoder.yudao.module.zsjos.framework.mediascreen.MediaScreenProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class MediaScreenQueryService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final LeadMapper leadMapper; private final MediaScreenDailySnapshotMapper snapshotMapper;
    private final AdminUserApi adminUserApi; private final DeptApi deptApi;
    private final MaintenanceModeApi maintenanceModeApi; private final StringRedisTemplate redis;
    private final MediaScreenProperties properties;

    public MediaScreenQueryService(LeadMapper leadMapper, MediaScreenDailySnapshotMapper snapshotMapper,
                                   AdminUserApi adminUserApi, DeptApi deptApi, MaintenanceModeApi maintenanceModeApi,
                                   StringRedisTemplate redis, MediaScreenProperties properties) {
        this.leadMapper=leadMapper; this.snapshotMapper=snapshotMapper; this.adminUserApi=adminUserApi;
        this.deptApi=deptApi; this.maintenanceModeApi=maintenanceModeApi; this.redis=redis; this.properties=properties;
    }

    public MediaScreenRespVO stats(Long tenantId, boolean includePartTimers) {
        String key="zsjos:media-screen:"+tenantId+":stats:"+includePartTimers;
        MediaScreenRespVO cached=read(key,MediaScreenRespVO.class); if(cached!=null)return cached;
        ZonedDateTime now=ZonedDateTime.now(ZONE); MediaScreenRespVO value=aggregate(tenantId,now.toLocalDate(),now.toLocalDateTime());
        value.setPartTimeIncluded(includePartTimers);value.getPartTimer().setEnabled(includePartTimers);write(key,value,properties.getCache().getStatsTtlSeconds()); return value;
    }

    public MediaScreenRespVO history(Long tenantId, LocalDate date, boolean includePartTimers) {
        String key="zsjos:media-screen:"+tenantId+":history:"+date+":"+includePartTimers;
        MediaScreenRespVO cached=read(key,MediaScreenRespVO.class); if(cached!=null)return cached;
        List<MediaScreenDailySnapshotDO> target=snapshotMapper.selectByDate(tenantId,date);
        MediaScreenRespVO result=target.isEmpty()?emptyHistory(tenantId,date):aggregateHistory(tenantId,date,target);
        result.setPartTimeIncluded(includePartTimers); write(key,result,properties.getCache().getHistoryTtlSeconds()); return result;
    }

    public Map<String,Object> maintenance(Long tenantId){
        String key="zsjos:media-screen:"+tenantId+":maintenance"; Map<String,Object> cached=read(key,Map.class); if(cached!=null)return cached;
        Map<String,Object> value=new LinkedHashMap<>(); value.put("tenantId",tenantId); value.put("maintenanceEnabled",maintenanceModeApi.isEnabled()); value.put("checkedAt",OffsetDateTime.now(ZONE)); write(key,value,properties.getCache().getMaintenanceTtlSeconds()); return value;
    }

    @Transactional
    public void freeze(Long tenantId, LocalDate date) {
        List<Long> deptIds=deptIds(); if(deptIds.isEmpty())return; List<AdminUserRespDTO> roster=activeUsers(deptIds);
        Map<Long,AdminUserRespDTO> users=new HashMap<>();roster.forEach(u->users.put(u.getId(),u));LocalDateTime from=date.atStartOfDay(),to=date.plusDays(1).atStartOfDay();
        List<MediaScreenContributionRow> rows=leadMapper.countMediaScreenContributions(tenantId,from,from,from,to);Set<Long> contributorIds=new HashSet<>();rows.forEach(x->contributorIds.add(x.getContributorUserId()));users.putAll(adminUserApi.getUserMap(contributorIds));Map<Key,Counts> counts=merge(rows,users,deptIds);
        Map<Long,Key> assignments=new LinkedHashMap<>();for(AdminUserRespDTO u:roster)assignments.put(u.getId(),new Key(u.getId(),u.getDeptId()));for(Key key:counts.keySet())assignments.put(key.user(),key);
        Set<Long> existing=new HashSet<>(snapshotMapper.selectMemberIds(tenantId,date,assignments.keySet()));Map<Long,DeptRespDTO> deptMap=deptApi.getDeptMap(deptIds);
        for(Key key:assignments.values()){AdminUserRespDTO user=users.get(key.user());if(user==null||existing.contains(user.getId()))continue;DeptRespDTO dept=deptMap.get(key.dept());Counts c=counts.getOrDefault(key,new Counts());snapshotMapper.insertIgnore(tenantId,new MediaScreenDailySnapshotDO().setSnapshotDate(date).setSupervisorId(dept==null?null:dept.getLeaderUserId()).setDepartmentName(dept==null?"":dept.getName()).setMemberId(user.getId()).setMemberName(user.getNickname()).setSubmittedCount((int)c.today).setValidCount((int)c.effective).setPartTimeSubmittedCount(0).setPartTimeValidCount(0));}
    }

    private MediaScreenRespVO aggregate(Long tenantId,LocalDate date,LocalDateTime now){
        MediaScreenRespVO r=new MediaScreenRespVO(); r.setTenantId(tenantId); r.setGeneratedAt(now); r.setUpdatedAt(now.atZone(ZONE).toInstant().toEpochMilli()); r.setRefreshIntervalSeconds((int)properties.getCache().getRefreshIntervalSeconds());
        List<Long> ids=deptIds(); Map<Long,DeptRespDTO> dm=ids.isEmpty()?Map.of():deptApi.getDeptMap(ids); List<AdminUserRespDTO> roster=activeUsers(ids); Map<Long,AdminUserRespDTO> users=new HashMap<>(); roster.forEach(u->users.put(u.getId(),u));
        LocalDateTime day=date.atStartOfDay(), week=date.with(DayOfWeek.MONDAY).atStartOfDay(), month=date.withDayOfMonth(1).atStartOfDay(); List<MediaScreenContributionRow> rows=leadMapper.countMediaScreenContributions(tenantId,day,week,month,now); rows.forEach(x->{if(x.getContributorUserId()!=null)users.putIfAbsent(x.getContributorUserId(),adminUserApi.getUserMap(List.of(x.getContributorUserId())).get(x.getContributorUserId()));});
        List<MediaScreenRespVO.Department> ds=departments(ids,dm,roster,users,merge(rows,users,ids)); MediaScreenRespVO.Metrics summary=new MediaScreenRespVO.Metrics(); ds.forEach(d->add(summary,d.getMetrics())); r.setSummary(summary); r.setDepartments(ds); r.setPartTimeCompanionDepartment(null); r.setTodayStar(star(ds,date,tenantId)); r.setYesterdayChampion(champion(tenantId,date.minusDays(1))); r.setTrend(buildTrend(tenantId,date,now,users,ids)); r.setSeries(buildSeries(tenantId,date,users,ids));
        r.setTotalLeads(summary.getMonthTotal()); r.setDepartmentRanking(rankDepartments(ds)); r.setMemberRanking(rankMembers(ds)); MediaScreenRespVO.PartTimer partTimer=new MediaScreenRespVO.PartTimer();partTimer.setEnabled(false);partTimer.setItems(List.of());r.setPartTimer(partTimer);return r;
    }

    private MediaScreenRespVO aggregateHistory(Long tenantId,LocalDate date,List<MediaScreenDailySnapshotDO> target){
        MediaScreenRespVO r=new MediaScreenRespVO(); r.setTenantId(tenantId);r.setAvailable(true);r.setSource("persisted_snapshot");r.setGeneratedAt(LocalDateTime.now(ZONE));r.setSnapshotCreatedAt(target.get(0).getCreateTime());
        LocalDate month=date.withDayOfMonth(1),week=date.with(DayOfWeek.MONDAY),from=month.isBefore(week)?month:week; List<MediaScreenDailySnapshotDO> all=snapshotMapper.selectByDateBetween(tenantId,from,date); Map<Long,MediaScreenRespVO.Metrics> mm=new HashMap<>();
        for(MediaScreenDailySnapshotDO x:all){MediaScreenRespVO.Metrics m=mm.computeIfAbsent(x.getMemberId(),k->new MediaScreenRespVO.Metrics());if(x.getSnapshotDate().equals(date))m.setToday(m.getToday()+x.getSubmittedCount());if(!x.getSnapshotDate().isBefore(week))m.setWeek(m.getWeek()+x.getSubmittedCount());if(!x.getSnapshotDate().isBefore(month)){m.setMonthTotal(m.getMonthTotal()+x.getSubmittedCount());m.setMonthEffective(m.getMonthEffective()+x.getValidCount());}}
        Map<String,MediaScreenRespVO.Department> dm=new LinkedHashMap<>(); for(MediaScreenDailySnapshotDO x:target){var d=dm.computeIfAbsent(x.getDepartmentName(),this::department);var m=member(x.getMemberName(),mm.getOrDefault(x.getMemberId(),new MediaScreenRespVO.Metrics()));d.getMembers().add(m);add(d.getMetrics(),metrics(m));}
        List<MediaScreenRespVO.Department> ds=new ArrayList<>(dm.values());MediaScreenRespVO.Metrics sum=new MediaScreenRespVO.Metrics();ds.forEach(d->add(sum,d.getMetrics()));r.setSummary(sum);r.setDepartments(ds);r.setPartTimeCompanionDepartment(null);r.setTodayStar(null);r.setYesterdayChampion(null);r.setTrend(trend(sum.getToday()));var s=new MediaScreenRespVO.Series();s.setSubmitted(List.of());s.setValid(List.of());r.setSeries(s);r.setTotalLeads(sum.getToday());r.setDepartmentRanking(rankDepartments(ds));r.setMemberRanking(rankMembers(ds));return r;
    }

    private MediaScreenRespVO emptyHistory(Long tenantId,LocalDate date){MediaScreenRespVO r=new MediaScreenRespVO();r.setTenantId(tenantId);r.setSnapshotDate(date);r.setAvailable(false);r.setSource("persisted_snapshot");r.setGeneratedAt(LocalDateTime.now(ZONE));r.setDepartments(List.of());r.setDepartmentRanking(List.of());r.setMemberRanking(List.of());return r;}
    private Map<Key,Counts> merge(List<MediaScreenContributionRow> rows,Map<Long,AdminUserRespDTO> users,List<Long> ids){Map<Key,Counts> out=new HashMap<>();for(var x:rows){AdminUserRespDTO contributor=users.get(x.getContributorUserId());if(contributor==null||!CommonStatusEnum.ENABLE.getStatus().equals(contributor.getStatus()))continue;AdminUserRespDTO p=x.getSourceProviderUserId()==null?null:users.get(x.getSourceProviderUserId());Long d="sales_self_sourced".equals(x.getSourceType())&&p!=null?p.getDeptId():x.getSourceDeptId();if(d==null||!ids.contains(d))continue;Counts c=out.computeIfAbsent(new Key(x.getContributorUserId(),d),k->new Counts());c.today+=n(x.getTodayCount());c.week+=n(x.getWeekCount());c.month+=n(x.getMonthTotal());c.effective+=n(x.getMonthEffective());}return out;}
    private List<MediaScreenRespVO.Department> departments(List<Long> ids,Map<Long,DeptRespDTO> dm,List<AdminUserRespDTO> roster,Map<Long,AdminUserRespDTO> users,Map<Key,Counts> counts){List<MediaScreenRespVO.Department> out=new ArrayList<>();for(Long id:ids){DeptRespDTO d=dm.get(id);if(d==null)continue;var v=department(d.getName());AdminUserRespDTO l=d.getLeaderUserId()==null?null:users.computeIfAbsent(d.getLeaderUserId(),uid->adminUserApi.getUserMap(List.of(uid)).get(uid));v.setSubtitle(l==null?"":"主管 "+l.getNickname());LinkedHashSet<Long> memberIds=new LinkedHashSet<>();for(var u:roster)if(Objects.equals(u.getDeptId(),id))memberIds.add(u.getId());for(Key key:counts.keySet())if(Objects.equals(key.dept(),id))memberIds.add(key.user());for(Long memberId:memberIds){AdminUserRespDTO u=users.get(memberId);if(u==null)continue;Counts c=counts.get(new Key(memberId,id));var m=member(u.getNickname(),c==null?new MediaScreenRespVO.Metrics():c.metrics());v.getMembers().add(m);add(v.getMetrics(),metrics(m));}out.add(v);}return out;}
    private List<AdminUserRespDTO> activeUsers(List<Long> ids){return ids.isEmpty()?List.of():adminUserApi.getUserListByDeptIds(ids).stream().filter(u->CommonStatusEnum.ENABLE.getStatus().equals(u.getStatus())).toList();}
    private List<Long> deptIds(){return properties.getNewMedia().getDepartmentIds()==null?List.of():properties.getNewMedia().getDepartmentIds().stream().filter(Objects::nonNull).distinct().toList();}
    private MediaScreenRespVO.Department department(String n){var d=new MediaScreenRespVO.Department();d.setName(n);d.setSubtitle("");d.setMetrics(new MediaScreenRespVO.Metrics());d.setMembers(new ArrayList<>());return d;}
    private MediaScreenRespVO.Trend trend(long today){var t=new MediaScreenRespVO.Trend();var a=new ArrayList<>(Collections.nCopies(144,0L));a.set(143,today);t.setToday(a);t.setYesterday(new ArrayList<>(Collections.nCopies(144,0L)));t.setStepMinutes(10);return t;}
    private MediaScreenRespVO.Trend buildTrend(Long tenantId,LocalDate date,LocalDateTime now,Map<Long,AdminUserRespDTO> users,List<Long> ids){int slots=Math.max(1,(now.getHour()*60+now.getMinute())/10+1);LocalDateTime today=date.atStartOfDay(),yesterday=date.minusDays(1).atStartOfDay();var todayRows=leadMapper.countMediaScreenTenMinuteContributions(tenantId,today,now);var yesterdayRows=leadMapper.countMediaScreenTenMinuteContributions(tenantId,yesterday,yesterday.plusMinutes(slots*10L));List<Long> a=cumulative(todayRows,users,ids,slots),b=cumulative(yesterdayRows,users,ids,slots);var t=new MediaScreenRespVO.Trend();t.setToday(a);t.setYesterday(b);t.setStepMinutes(10);return t;}
    private MediaScreenRespVO.Series buildSeries(Long tenantId,LocalDate date,Map<Long,AdminUserRespDTO> users,List<Long> ids){LocalDate from=date.minusDays(13);var rows=leadMapper.countMediaScreenDailyContributions(tenantId,from.atStartOfDay(),date.plusDays(1).atStartOfDay());Map<LocalDate,long[]> totals=new HashMap<>();for(var x:rows)if(inScope(x,users,ids)){LocalDate bucket=LocalDate.parse(x.getBucket());long[] v=totals.computeIfAbsent(bucket,k->new long[2]);v[0]+=n(x.getSubmittedCount());v[1]+=n(x.getValidCount());}List<Long> submitted=new ArrayList<>(),valid=new ArrayList<>();for(int i=0;i<14;i++){long[] v=totals.getOrDefault(from.plusDays(i),new long[2]);submitted.add(v[0]);valid.add(v[1]);}var s=new MediaScreenRespVO.Series();s.setSubmitted(submitted);s.setValid(valid);return s;}
    private List<Long> cumulative(List<MediaScreenTimedContributionRow> rows,Map<Long,AdminUserRespDTO> users,List<Long> ids,int slots){long[] values=new long[slots];for(var x:rows)if(inScope(x,users,ids)){int bucket=Integer.parseInt(x.getBucket());if(bucket>=0&&bucket<slots)values[bucket]+=n(x.getSubmittedCount());}List<Long> out=new ArrayList<>(slots);long total=0;for(long value:values){total+=value;out.add(total);}return out;}
    private boolean inScope(MediaScreenTimedContributionRow x,Map<Long,AdminUserRespDTO> users,List<Long> ids){AdminUserRespDTO contributor=users.computeIfAbsent(x.getContributorUserId(),id->adminUserApi.getUserMap(List.of(id)).get(id));if(contributor==null||!CommonStatusEnum.ENABLE.getStatus().equals(contributor.getStatus()))return false;AdminUserRespDTO p=x.getSourceProviderUserId()==null?null:users.computeIfAbsent(x.getSourceProviderUserId(),id->adminUserApi.getUserMap(List.of(id)).get(id));Long d="sales_self_sourced".equals(x.getSourceType())&&p!=null?p.getDeptId():x.getSourceDeptId();return d!=null&&ids.contains(d);}
    private MediaScreenRespVO.Star star(List<MediaScreenRespVO.Department> ds,LocalDate date,Long tenantId){MediaScreenRespVO.Member w=null;String d="";for(var x:ds)for(var m:x.getMembers())if(w==null||m.getToday()>w.getToday()){w=m;d=x.getName();}if(w==null||w.getToday()==0)return null;var s=new MediaScreenRespVO.Star();s.setName(w.getName());s.setDeptName(d);s.setToday(w.getToday());s.setLeadCount(w.getToday());s.setRankToday(1);s.setRank(1);List<MediaScreenDailySnapshotDO> yesterday=snapshotMapper.selectByDate(tenantId,date.minusDays(1));List<MediaScreenDailySnapshotDO> ranked=yesterday.stream().sorted(Comparator.comparingInt(MediaScreenDailySnapshotDO::getSubmittedCount).reversed()).toList();for(int i=0;i<ranked.size();i++){var x=ranked.get(i);if(Objects.equals(x.getMemberName(),w.getName())&&Objects.equals(x.getDepartmentName(),d)){s.setYesterday(x.getSubmittedCount());s.setRankYesterday(i+1);break;}}return s;}
    private MediaScreenRespVO.Champion champion(Long tenantId,LocalDate date){return snapshotMapper.selectByDate(tenantId,date).stream().max(Comparator.comparingInt(MediaScreenDailySnapshotDO::getSubmittedCount)).filter(x->x.getSubmittedCount()>0).map(x->{var c=new MediaScreenRespVO.Champion();c.setName(x.getMemberName());c.setDeptName(x.getDepartmentName());c.setCount(x.getSubmittedCount());return c;}).orElse(null);}
    private List<MediaScreenRespVO.RankItem> rankDepartments(List<MediaScreenRespVO.Department> ds){List<MediaScreenRespVO.RankItem> out=new ArrayList<>();int i=1;for(var d:ds){var x=new MediaScreenRespVO.RankItem();x.setName(d.getName());x.setLeadCount(d.getMetrics().getMonthTotal());x.setRank(i++);out.add(x);}return out;}
    private List<MediaScreenRespVO.RankItem> rankMembers(List<MediaScreenRespVO.Department> ds){List<MediaScreenRespVO.RankItem> out=new ArrayList<>();for(var d:ds)for(var m:d.getMembers()){var x=new MediaScreenRespVO.RankItem();x.setName(m.getName());x.setLeadCount(m.getMonthTotal());out.add(x);}out.sort(Comparator.comparingLong(MediaScreenRespVO.RankItem::getLeadCount).reversed());int i=1;for(var x:out)x.setRank(i++);return out;}
    private MediaScreenRespVO.Member member(String name,MediaScreenRespVO.Metrics x){var m=new MediaScreenRespVO.Member();m.setName(name);m.setToday(x.getToday());m.setWeek(x.getWeek());m.setMonthTotal(x.getMonthTotal());m.setMonthEffective(x.getMonthEffective());return m;}
    private MediaScreenRespVO.Metrics metrics(MediaScreenRespVO.Member x){var m=new MediaScreenRespVO.Metrics();m.setToday(x.getToday());m.setWeek(x.getWeek());m.setMonthTotal(x.getMonthTotal());m.setMonthEffective(x.getMonthEffective());return m;}
    private static void add(MediaScreenRespVO.Metrics a,MediaScreenRespVO.Metrics b){a.setToday(a.getToday()+b.getToday());a.setWeek(a.getWeek()+b.getWeek());a.setMonthTotal(a.getMonthTotal()+b.getMonthTotal());a.setMonthEffective(a.getMonthEffective()+b.getMonthEffective());}
    private static long n(Long x){return x==null?0:x;} private static class Counts{long today,week,month,effective;MediaScreenRespVO.Metrics metrics(){var m=new MediaScreenRespVO.Metrics();m.setToday(today);m.setWeek(week);m.setMonthTotal(month);m.setMonthEffective(effective);return m;}} private record Key(Long user,Long dept){}
    private <T>T read(String k,Class<T> c){try{String s=redis.opsForValue().get(k);return s==null?null:JsonUtils.parseObject(s,c);}catch(Exception e){return null;}} private void write(String k,Object v,long ttl){try{redis.opsForValue().set(k,JsonUtils.toJsonString(v),ttl,TimeUnit.SECONDS);}catch(Exception ignored){}}
}
