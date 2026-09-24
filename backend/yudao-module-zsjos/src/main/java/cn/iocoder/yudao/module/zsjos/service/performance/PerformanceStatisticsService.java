package cn.iocoder.yudao.module.zsjos.service.performance;
import cn.iocoder.yudao.module.zsjos.controller.admin.performance.vo.PerformanceVO.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.performance.*;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.math.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.module.zsjos.service.performance.PerformancePeriods.*;
@Service
@org.springframework.transaction.annotation.Transactional(readOnly=true)
public class PerformanceStatisticsService {
 @Resource private PerformanceAccess access;
 @Resource private PerformanceFactMapper facts;
 @Resource private PerformanceTargetService targets;
 private Long tenant(){return TenantContextHolder.getRequiredTenantId();}
 private LocalDateTime now(){return LocalDateTime.now(ZONE);}
 private List<PerformanceFact> authorizedRows(Query q,List<PerformanceFact> rows){return rows.stream().filter(x->access.historicalRowAllowed(q,x.getDeptId())).toList();}
 private List<PerformanceFact> orders(Query q){access.authorize(q,false);return authorizedRows(q,facts.orders(tenant(),q.getScopeType(),q.getScopeId()));}
 private List<PerformanceFact> receipts(Query q){return authorizedRows(q,facts.receipts(tenant(),q.getScopeType(),q.getScopeId()));}
 private Set<Long> users(Query q){
  Set<Long> users=new HashSet<>();
  if(Set.of("SELF","USER").contains(q.getScopeType()))users.add(q.getScopeId());
  else for(var u:access.sales()){var m=access.mapping(u.getDeptId());if(m!=null&&("DEPT".equals(q.getScopeType())?q.getScopeId().equals(m.getDeptId()):q.getScopeId().equals(m.getCenterId()))&&access.departmentAllowed(u.getDeptId()))users.add(u.getId());}
  return users;
 }
 private List<PerformanceFact> tasks(Query q){return authorizedRows(q,facts.tasks(tenant(),q.getScopeType(),q.getScopeId()));}
 public static Metric metric(Window w,List<PerformanceFact> orders,List<PerformanceFact> receipts) {
  var selected=orders.stream().filter(x->w.contains(x.getOccurredAt())).toList();
  BigDecimal amount=selected.stream().map(PerformanceFact::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
  var avg=selected.stream().filter(x->eligibleAverage(x.getAmount())).toList();
  Set<Long> newValid=receipts.stream().filter(x->w.contains(x.getReceivedAt())&&Set.of("valid","converted","won").contains(x.getStatus())).map(PerformanceFact::getLeadId).filter(Objects::nonNull).collect(Collectors.toSet());
  Set<Long> won=new HashSet<>(),oldWon=new HashSet<>();
  for(var x:selected)if("first_purchase".equals(x.getOrderType())&&x.getLeadId()!=null&&withinValidity(x.getReceivedAt(),x.getOccurredAt())) {
   if(w.contains(x.getReceivedAt())){newValid.add(x.getLeadId());won.add(x.getLeadId());}
   else if(x.getReceivedAt().isBefore(w.start())){oldWon.add(x.getLeadId());won.add(x.getLeadId());}
  }
  newValid.addAll(oldWon);
  return new Metric(w.key(),w.label(),w.start(),w.end(),amount,selected.size(),won.size(),newValid.size(),ratio(BigDecimal.valueOf(won.size()),BigDecimal.valueOf(newValid.size())),ratio(avg.stream().map(PerformanceFact::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add),BigDecimal.valueOf(avg.size())));
 }
 public Overview overview(Query q) {
  var o=orders(q);var r=receipts(q);var n=now();
  var performance=List.of("today","week","month","quarter","year","last7","last30","last60","last90").stream().map(k->metric(window(k,n),o,r)).toList();
  var conversion=List.of("month","lastMonth","last7","last30","last60","last90").stream().map(k->metric(window(k,n),o,r)).toList();
  List<TargetProgress> progress=new ArrayList<>();
  for(String k:List.of("lastWeek","week","month","quarter","year")){var w=window(k,n);String period="lastWeek".equals(k)?"week":k;progress.add(new TargetProgress(k,w.label(),metric(w,o,r),targets.resolve(q.getScopeType(),q.getScopeId(),period,w.start().toLocalDate())));}
  var t=tasks(q);Map<String,Long> pending=new LinkedHashMap<>();
  pending.put("accept",t.stream().filter(x->pending(x)&&"lead_assignment_accept".equals(x.getGroupKey())).count());
  pending.put("qualification",t.stream().filter(x->Boolean.TRUE.equals(x.getCurrentQualification())&&(pending(x)||"overdue".equals(x.getOutcome()))&&"lead_qualification".equals(x.getGroupKey())&&x.getDueAt()!=null&&!x.getDueAt().isAfter(n)).count());
  pending.put("todayFollowUp",t.stream().filter(x->pending(x)&&follow(x)&&x.getDueAt()!=null&&x.getDueAt().toLocalDate().equals(n.toLocalDate())).count());
  pending.put("overdueFollowUp",t.stream().filter(x->pending(x)&&follow(x)&&x.getDueAt()!=null&&x.getDueAt().isBefore(n)).count());
  pending.put("missingTarget",(long)progress.get(2).target().missing());
  var missing=o.stream().filter(x->x.getDeptId()==null||x.getCenterId()==null).toList();
  return new Overview(n,facts.availableSince(tenant(),q.getScopeType(),q.getScopeId()),progress,performance,conversion,pending,missing.size(),missing.stream().map(PerformanceFact::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add),access.has("zsjos:sales-performance:detail"));
 }
 private boolean pending(PerformanceFact x){return "pending".equals(x.getStatus());}
 private boolean follow(PerformanceFact x){return Set.of("lead_first_follow_up","lead_follow_up_reminder").contains(x.getGroupKey());}
 private void cumulative(Query q,List<PerformanceFact> o,List<PerformanceFact> r){if(q.isCumulative()){var first=java.util.stream.Stream.concat(o.stream().map(PerformanceFact::getOccurredAt),r.stream().map(PerformanceFact::getReceivedAt)).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(now());q.setStart(first.toLocalDate());q.setEnd(now().toLocalDate());q.setGrain("month");}}
 private Window range(Query q){var n=now();if(q.getPeriodKey()!=null)return window(q.getPeriodKey(),n);LocalDate start=q.getStart()==null?n.toLocalDate().withDayOfMonth(1):q.getStart();LocalDate end=q.getEnd()==null?n.toLocalDate():q.getEnd();if(end.isBefore(start))throw PerformanceAccess.invalid("结束日期不能早于开始日期");return new Window("range","所选期间",start.atStartOfDay(),end.plusDays(1).atStartOfDay().isAfter(n)?n:end.plusDays(1).atStartOfDay());}
 public Analysis analysis(Query q){
  var o=orders(q);var r=receipts(q);cumulative(q,o,r);var w=range(q);var selected=o.stream().filter(x->w.contains(x.getOccurredAt())).toList();
  List<Metric> averages=new ArrayList<>();averages.add(metric(new Window("all","整体",w.start(),w.end()),o,r));
  for(String k:List.of("inbound","self","repurchase"))averages.add(metric(new Window(k,sourceName(k),w.start(),w.end()),o.stream().filter(x->k.equals(x.getGroupKey())||"repurchase".equals(k)&&"repurchase".equals(x.getOrderType())).toList(),List.of()));
  List<Metric> trend=new ArrayList<>();LocalDate d=w.start().toLocalDate();
  while(d.atStartOfDay().isBefore(w.end())){LocalDate next=switch(q.getGrain()){case "week"->d.with(TemporalAdjusters.next(DayOfWeek.MONDAY));case "month"->d.withDayOfMonth(1).plusMonths(1);default->d.plusDays(1);};var end=next.atStartOfDay().isAfter(w.end())?w.end():next.atStartOfDay();trend.add(metric(new Window(d.toString(),d.toString(),d.atStartOfDay(),end),o,r));d=next;}
  var products=authorizedRows(q,facts.products(tenant(),q.getScopeType(),q.getScopeId())).stream().filter(x->w.contains(x.getOccurredAt())).toList();
  List<Contribution> contributions=new ArrayList<>();Set<Long> members=new LinkedHashSet<>(users(q));selected.stream().map(PerformanceFact::getUserId).filter(Objects::nonNull).forEach(members::add);
  // Current account status controls personnel rows, never the historical facts or share denominator.
  members.retainAll(access.enabledUserIds(members));
  for(Long user:members){var personOrders=o.stream().filter(x->user.equals(x.getUserId())).toList();var personReceipts=r.stream().filter(x->user.equals(x.getUserId())).toList();var m=metric(w,personOrders,personReceipts);String name=selected.stream().filter(x->user.equals(x.getUserId())&&x.getUserName()!=null).map(PerformanceFact::getUserName).findFirst().orElseGet(()->{var u=access.user(user);return u==null?"历史人员":u.getNickname();});var t=matchingTarget(q,user,w);contributions.add(new Contribution(user,name,m,t==null||!t.complete()?null:ratio(m.amount(),t.floorAmount()),t==null||!t.complete()?null:ratio(m.amount(),t.sprintAmount()),ratio(m.amount(),averages.getFirst().amount())));}
  Map<String,List<Metric>> averageTrends=new LinkedHashMap<>();for(String group:List.of("all","inbound","self","repurchase")){var subset="all".equals(group)?o:o.stream().filter(x->group.equals(x.getGroupKey())||"repurchase".equals(group)&&"repurchase".equals(x.getOrderType())).toList();averageTrends.put(group,trend.stream().map(t->metric(new Window(t.key(),t.label(),t.start(),t.end()),subset,List.of())).toList());}
  return new Analysis(now(),w.start().toLocalDate(),q.getEnd()==null?now().toLocalDate():q.getEnd(),averages,trend,groups(selected,x->Objects.toString(x.getChannelCode(),"unknown")+"|"+Objects.toString(x.getLabel(),"历史来源缺失"),false),groups(products,x->Objects.toString(x.getGroupKey(),"unknown")+"|"+Objects.toString(x.getLabel(),"历史产品名称缺失"),true),groups(selected.stream().filter(x->members.contains(x.getUserId())).toList(),x->Objects.toString(x.getUserId(),"unknown")+"|"+Objects.toString(x.getUserName(),"历史姓名缺失"),false).stream().map(g->new Group(g.key(),g.label(),g.amount(),g.count(),ratio(g.amount(),averages.getFirst().amount()))).toList(),contributions,averageTrends,targetPeriod(q,w)==null?null:targets.resolve(q.getScopeType(),q.getScopeId(),targetPeriod(q,w),w.start().toLocalDate()));
 }
 private String targetPeriod(Query q,Window w){
  LocalDate d=w.start().toLocalDate(),end=q.getEnd()==null?now().toLocalDate():q.getEnd();String period=null;
  if(q.getPeriodKey()!=null&&Set.of("week","lastWeek","month","lastMonth","quarter","year").contains(q.getPeriodKey()))period=switch(q.getPeriodKey()){case "lastWeek"->"week";case "lastMonth"->"month";default->q.getPeriodKey();};
  else if(d.getDayOfYear()==1&&end.equals(d.plusYears(1).minusDays(1)))period="year";
  else if(d.getDayOfMonth()==1&&(d.getMonthValue()-1)%3==0&&end.equals(d.plusMonths(3).minusDays(1)))period="quarter";
  else if(d.getDayOfMonth()==1&&(end.equals(d.withDayOfMonth(d.lengthOfMonth()))||d.equals(now().toLocalDate().withDayOfMonth(1))&&end.equals(now().toLocalDate())))period="month";
  else if(d.getDayOfWeek()==DayOfWeek.MONDAY&&end.equals(d.plusDays(6)))period="week";
  return period;
 }

 private Target matchingTarget(Query q,Long user,Window w){String period=targetPeriod(q,w);return period==null?null:targets.resolve("USER",user,period,w.start().toLocalDate());}
 private String sourceName(String k){return switch(Objects.toString(k,"unknown")){case "inbound"->"线上引流";case "self"->"非引流";case "repurchase"->"复购";default->"历史来源缺失";};}
 private List<Group> groups(List<PerformanceFact> rows,Function<PerformanceFact,String> key,boolean product){
  BigDecimal total=rows.stream().map(PerformanceFact::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
  return rows.stream().collect(Collectors.groupingBy(key,LinkedHashMap::new,Collectors.toList())).entrySet().stream().map(e->{BigDecimal a=e.getValue().stream().map(PerformanceFact::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);String label=e.getKey().contains("|")?e.getKey().substring(e.getKey().indexOf('|')+1):e.getKey();return new Group(e.getKey(),label,a,product?e.getValue().stream().map(PerformanceFact::getLeadId).distinct().count():e.getValue().size(),ratio(a,total));}).sorted(Comparator.comparing(Group::amount).reversed()).toList();
 }
 public List<HistoryMonth> history(Query q){var o=orders(q);int year=q.getYear()==null?now().getYear():q.getYear();List<HistoryMonth> result=new ArrayList<>();var n=now();for(int m=1;m<=12;m++){LocalDate start=LocalDate.of(year,m,1);var end=start.plusMonths(1).atStartOfDay();if(year==n.getYear()&&m==n.getMonthValue())end=n;var previousEnd=end.minusYears(1);result.add(new HistoryMonth(m,metric(new Window("m","",start.atStartOfDay(),end),o,List.of()).amount(),metric(new Window("p","",start.minusYears(1).atStartOfDay(),previousEnd),o,List.of()).amount()));}return result;}
 private List<Group> countGroups(List<PerformanceFact> rows,Function<PerformanceFact,String> key){return rows.stream().collect(Collectors.groupingBy(key,LinkedHashMap::new,Collectors.mapping(PerformanceFact::getLeadId,Collectors.toSet()))).entrySet().stream().map(e->new Group(e.getKey(),e.getKey(),BigDecimal.ZERO,e.getValue().size(),ratio(BigDecimal.valueOf(e.getValue().size()),BigDecimal.valueOf(rows.stream().map(PerformanceFact::getLeadId).distinct().count())))).toList();}
 public LeadReport leads(Query q){
  var o=orders(q);var all=receipts(q);cumulative(q,o,all);var w=range(q);if(q.isCalendar()&&java.time.temporal.ChronoUnit.DAYS.between(w.start().toLocalDate(),q.getEnd()==null?w.end().toLocalDate():q.getEnd())>31)throw PerformanceAccess.invalid("日历每次仅支持一个月");var n=now();var selected=all.stream().filter(x->w.contains(x.getReceivedAt())).toList();var tasks=tasks(q);
  Map<String,Long> work=new LinkedHashMap<>();work.put("received",selected.stream().map(PerformanceFact::getLeadId).distinct().count());work.put("valid",selected.stream().filter(x->Set.of("valid","converted","won").contains(x.getStatus())).map(PerformanceFact::getLeadId).distinct().count());
  var userIds=users(q);var assignments=authorizedRows(q,facts.assignments(tenant(),q.getScopeType(),q.getScopeId()));work.put("assigned",assignments.stream().filter(x->"dispatch".equals(x.getGroupKey())&&w.contains(x.getOccurredAt())).map(PerformanceFact::getLeadId).distinct().count());work.put("missed",assignments.stream().filter(x->"timeout".equals(x.getGroupKey())&&w.contains(x.getOccurredAt())).map(PerformanceFact::getLeadId).distinct().count());work.put("followUps",authorizedRows(q,facts.followUps(tenant(),q.getScopeType(),q.getScopeId())).stream().filter(x->w.contains(x.getOccurredAt())).count());
  List<CalendarDay> calendar=new ArrayList<>();
  for(LocalDate d=w.start().toLocalDate();q.isCalendar()&&!d.isAfter(q.getEnd()==null?w.end().toLocalDate():q.getEnd());d=d.plusDays(1)){
   calendar.add(PerformanceCalendar.summarize(d,selected,tasks,n));
  }

  Set<Long> ids=selected.stream().map(PerformanceFact::getLeadId).collect(Collectors.toSet());long converted=o.stream().filter(x->"first_purchase".equals(x.getOrderType())&&ids.contains(x.getLeadId())&&selected.stream().anyMatch(r->Objects.equals(r.getLeadId(),x.getLeadId())&&Objects.equals(r.getUserId(),x.getUserId())&&Objects.equals(r.getReceivedAt(),x.getReceivedAt()))).map(PerformanceFact::getLeadId).distinct().count();
  List<Group> funnel=List.of(count("received","接收客资",work.get("received")),count("valid","有效客资",work.get("valid")),count("converted","成交客资",converted));
  var follow=tasks.stream().filter(x->follow(x)&&w.contains(x.getDueAt())).toList();
  return new LeadReport(n,w.start().toLocalDate(),q.getEnd()==null?n.toLocalDate():q.getEnd(),work,countGroups(selected,x->Objects.toString(x.getCategory(),"历史分类缺失")),countGroups(selected,x->Objects.toString(x.getStage(),"阶段未记录")),calendar,funnel,List.of(count("planned","计划跟进",follow.size()),count("completed","已完成",follow.stream().filter(x->"completed".equals(x.getStatus())).count()),count("pending","未完成",follow.stream().filter(this::pending).count()),count("overdue","逾期未完成",follow.stream().filter(x->pending(x)&&x.getDueAt().isBefore(n)).count())),countGroups(selected,x->categoryBucket(x.getReceivedAt(),q.getGrain())+" · "+Objects.toString(x.getCategory(),"历史分类缺失")));
 }
 private String categoryBucket(LocalDateTime at,String grain){LocalDate d=at.toLocalDate();return switch(grain){case "week"->d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();case "month"->d.toString().substring(0,7);default->d.toString();};}
 private Group count(String k,String label,long count){return new Group(k,label,BigDecimal.ZERO,count,null);}
 public PageResult<Detail> details(Query q){
  if(!access.has("zsjos:sales-performance:detail"))throw PerformanceAccess.denied();
  var o=orders(q);var allReceipts=receipts(q);cumulative(q,o,allReceipts);var w=range(q);List<Detail> rows;
  if(q.getDimension()!=null&&q.getGroupKey()!=null){switch(q.getDimension()){
   case "source"->o=o.stream().filter(x->(Objects.toString(x.getChannelCode(),"unknown")+"|"+Objects.toString(x.getLabel(),"历史来源缺失")).equals(q.getGroupKey())).toList();
   case "contributor"->o=o.stream().filter(x->Objects.toString(x.getUserId(),"").equals(q.getGroupKey())).toList();
   case "product"->{var amounts=authorizedRows(q,facts.products(tenant(),q.getScopeType(),q.getScopeId())).stream().filter(x->(Objects.toString(x.getGroupKey(),"unknown")+"|"+Objects.toString(x.getLabel(),"历史产品名称缺失")).equals(q.getGroupKey())).collect(Collectors.toMap(PerformanceFact::getLeadId,PerformanceFact::getAmount,BigDecimal::add));o=o.stream().filter(x->amounts.containsKey(x.getId())).peek(x->x.setAmount(amounts.get(x.getId()))).toList();}
  }}
  if("orders".equals(q.getMetric()))rows=o.stream().filter(x->w.contains(x.getOccurredAt())).map(x->new Detail(x.getId(),x.getNumber(),"order",sourceName("repurchase".equals(x.getOrderType())?"repurchase":x.getGroupKey()),x.getOccurredAt(),x.getAmount(),"审批通过")).toList();
  else if("conversion".equals(q.getMetric())) {
   Map<Long,Detail> records=new LinkedHashMap<>();
   for(var x:receipts(q))if(w.contains(x.getReceivedAt())&&Set.of("valid","converted","won").contains(x.getStatus()))records.put(x.getLeadId(),new Detail(x.getLeadId(),x.getNumber(),"lead","期间新接有效",x.getReceivedAt(),null,"未在期间成交"));
   for(var x:o)if(w.contains(x.getOccurredAt())&&"first_purchase".equals(x.getOrderType())&&x.getLeadId()!=null&&withinValidity(x.getReceivedAt(),x.getOccurredAt())){var existing=records.get(x.getLeadId());records.put(x.getLeadId(),new Detail(x.getLeadId(),existing==null?allReceipts.stream().filter(r->Objects.equals(r.getLeadId(),x.getLeadId())).map(PerformanceFact::getNumber).findFirst().orElse(null):existing.number(),"lead",w.contains(x.getReceivedAt())?"期间新接成交":"往期接收有效期内成交",x.getReceivedAt(),x.getAmount(),"期间成交"));}
   rows=new ArrayList<>(records.values());
  }
  else if(Set.of("leads","valid").contains(q.getMetric()))rows=allReceipts.stream().filter(x->w.contains(x.getReceivedAt())&&(!"valid".equals(q.getMetric())||Set.of("valid","converted","won").contains(x.getStatus()))&&(!"category".equals(q.getDimension())||Objects.toString(x.getCategory(),"历史分类缺失").equals(q.getGroupKey()))&&(!"stage".equals(q.getDimension())||Objects.toString(x.getStage(),"阶段未记录").equals(q.getGroupKey()))).collect(Collectors.toMap(PerformanceFact::getLeadId,java.util.function.Function.identity(),(a,b)->a,LinkedHashMap::new)).values().stream().map(x->new Detail(x.getLeadId(),x.getNumber(),"lead",Objects.toString(x.getCategory(),"历史分类缺失"),x.getReceivedAt(),null,x.getStatus())).toList();
  else if(Set.of("assigned","missed","followUps").contains(q.getMetric())){var u=users(q);var fs="followUps".equals(q.getMetric())?facts.followUps(tenant(),q.getScopeType(),q.getScopeId()):facts.assignments(tenant(),q.getScopeType(),q.getScopeId());rows=authorizedRows(q,fs).stream().filter(x->w.contains(x.getOccurredAt())&&("followUps".equals(q.getMetric())||("assigned".equals(q.getMetric())?"dispatch":"timeout").equals(x.getGroupKey()))).map(x->new Detail(x.getId(),x.getNumber(),"lead",x.getLabel(),x.getOccurredAt(),null,x.getGroupKey())).toList();}
  else {var n=now();rows=tasks(q).stream().filter(x->switch(q.getMetric()){
   case "accept"->pending(x)&&"lead_assignment_accept".equals(x.getGroupKey());
   case "qualification"->Boolean.TRUE.equals(x.getCurrentQualification())&&(pending(x)||"overdue".equals(x.getOutcome()))&&"lead_qualification".equals(x.getGroupKey())&&x.getDueAt()!=null&&!x.getDueAt().isAfter(n);
   case "todayFollowUp"->pending(x)&&follow(x)&&x.getDueAt()!=null&&x.getDueAt().toLocalDate().equals(n.toLocalDate());
   case "overdueFollowUp"->pending(x)&&follow(x)&&x.getDueAt()!=null&&x.getDueAt().isBefore(n);
   default->follow(x)&&w.contains(x.getDueAt());
  }).map(x->new Detail(x.getId(),x.getNumber(),"task",x.getGroupKey(),x.getDueAt(),null,x.getStatus())).toList();}
  int from=Math.min(rows.size(),(q.getPageNo()-1)*q.getPageSize());return new PageResult<>(rows.subList(from,Math.min(rows.size(),from+q.getPageSize())),(long)rows.size());
 }
}
