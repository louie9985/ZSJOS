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
 @Resource private PerformanceDetailMapper detailQueries;
 @Resource private PerformanceTargetService targets;
 @Resource private PerformanceReportMapper reports;
 private Clock clock=Clock.system(ZONE);
 private Long tenant(){return TenantContextHolder.getRequiredTenantId();}
 private LocalDateTime now(){return LocalDateTime.now(clock);}
 private PerformanceReportQuery request(Query q,LocalDateTime n) {
  access.authorize(q,false);var scope=access.historicalScope(q);
  var r=new PerformanceReportQuery();r.setTenant(tenant());r.setType(q.getScopeType());r.setId(q.getScopeId());
  r.setAllDepartments(scope.allDepartments());r.setMissingDepartment(scope.missingDepartment());r.setDepartments(scope.departments());
  r.setNow(n);r.setToday(n.toLocalDate().atStartOfDay());r.setTomorrow(n.toLocalDate().plusDays(1).atStartOfDay());return r;
 }
 private void interval(PerformanceReportQuery r,Window w){r.setStart(w.start());r.setEnd(w.end());}
 private Window reportRange(Query q,PerformanceReportQuery r,LocalDateTime n){
  if(q.isCumulative()){var first=detailQueries.firstDate(r);q.setStart((first==null?n:first).toLocalDate());q.setEnd(n.toLocalDate());q.setGrain("month");}
  var w=range(q,n);interval(r,w);r.setDueEnd(dueWindow(q,w,n).end());return w;
 }
 private Map<String,PerformanceAggregate> amounts(PerformanceReportQuery r,List<Window> windows,String grouping){
  if(windows.isEmpty())return Map.of();
  r.setIntervals(windows.stream().map(w->new PerformanceReportQuery.Interval(w.key(),w.start(),w.end())).toList());r.setGrouping(grouping);
  Map<String,PerformanceAggregate> values=new LinkedHashMap<>();
  for(var x:reports.amounts(r))values.put(x.getKey()+"/"+x.getGroupKey(),x);return values;
 }
 private PerformanceAggregate amount(Map<String,PerformanceAggregate> data,Window w,String group){return data.getOrDefault(w.key()+"/"+group,new PerformanceAggregate());}
 private Metric metric(Window w,PerformanceAggregate a,PerformanceConversion.Population population){
  var people=population.at(w);long converted=people.values().stream().filter(x->x.order()!=null).count();
  return new Metric(w.key(),w.label(),w.start(),w.end(),a.getAmount(),a.getCount(),converted,people.size(),ratio(BigDecimal.valueOf(converted),BigDecimal.valueOf(people.size())),ratio(a.getAverageAmount(),BigDecimal.valueOf(a.getAverageOrders())),a.getAverageAmount(),a.getAverageOrders());
 }
 private List<PerformanceFact> conversionReceipts(PerformanceReportQuery r,List<PerformanceFact> orders){
  r.setConversionLeadIds(orders.stream().map(PerformanceFact::getLeadId).filter(Objects::nonNull).collect(Collectors.toSet()));return detailQueries.conversionReceipts(r);
 }
 public static Metric metric(Window w,List<PerformanceFact> orders,List<PerformanceFact> receipts) {
  var selected=orders.stream().filter(x->w.contains(x.getOccurredAt())).toList();
  BigDecimal amount=selected.stream().map(PerformanceFact::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
  var avg=selected.stream().filter(x->eligibleAverage(x.getAmount())).toList();
  var population=PerformanceConversion.population(w,orders,receipts);
  long won=population.values().stream().filter(x->x.order()!=null).count();
  BigDecimal averageAmount=avg.stream().map(PerformanceFact::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
  return new Metric(w.key(),w.label(),w.start(),w.end(),amount,selected.size(),won,population.size(),ratio(BigDecimal.valueOf(won),BigDecimal.valueOf(population.size())),ratio(averageAmount,BigDecimal.valueOf(avg.size())),averageAmount,avg.size());
 }
 public Overview overview(Query q) {
  var n=now();var r=request(q,n);
  var keys=List.of("today","week","month","lastMonth","quarter","year","last7","last30","last60","last90","lastWeek");
  var windows=keys.stream().map(k->window(k,n)).toList();
  r.setStart(windows.stream().map(Window::start).min(Comparator.naturalOrder()).orElseThrow());r.setEnd(n);
  var orders=detailQueries.conversionOrders(r);var population=new PerformanceConversion.Population(orders,conversionReceipts(r,orders));
  var sums=amounts(r,windows,"total");Map<String,Metric> metrics=new HashMap<>();
  for(var w:windows)metrics.put(w.key(),metric(w,amount(sums,w,"total"),population));
  var performance=keys.subList(0,10).stream().map(metrics::get).toList();
  var conversion=List.of("month","lastMonth","last7","last30","last60","last90").stream().map(metrics::get).toList();
  var targetKeys=List.of("lastWeek","week","month","quarter","year");
  var batch=targets.batch(targetKeys.stream().map(k->new PerformanceTargetService.Period("lastWeek".equals(k)?"week":k,window(k,n).start().toLocalDate())).toList(),List.of(q.getScopeId()));
  List<TargetProgress> progress=new ArrayList<>();
  for(var k:targetKeys){var w=window(k,n);progress.add(new TargetProgress(k,w.label(),metrics.get(k),batch.resolve(q.getScopeType(),q.getScopeId(),"lastWeek".equals(k)?"week":k,w.start().toLocalDate())));}
  Map<String,Long> pending=new LinkedHashMap<>();for(var x:reports.pending(r))pending.put(x.getKey(),x.getCount());pending.put("missingTarget",(long)progress.get(2).target().missing());
  var missing=reports.missingAttribution(r);
  return new Overview(n,facts.availableSince(tenant(),q.getScopeType(),q.getScopeId()),progress,performance,conversion,pending,missing.getCount(),missing.getAmount(),access.has("zsjos:sales-performance:detail"));
 }
 private boolean pending(PerformanceFact x){return "pending".equals(x.getStatus());}
 private Window range(Query q){return range(q,now());}
 private Window range(Query q,LocalDateTime n){if(q.getPeriodKey()!=null)return window(q.getPeriodKey(),n);LocalDate start=q.getStart()==null?n.toLocalDate().withDayOfMonth(1):q.getStart();LocalDate end=q.getEnd()==null?n.toLocalDate():q.getEnd();if(end.isBefore(start))throw PerformanceAccess.invalid("结束日期不能早于开始日期");return new Window("range","所选期间",start.atStartOfDay(),end.plusDays(1).atStartOfDay().isAfter(n)?n:end.plusDays(1).atStartOfDay());}
 private Window dueWindow(Query q,Window w){return dueWindow(q,w,now());}
 private Window dueWindow(Query q,Window w,LocalDateTime n){
  if("lastWeek".equals(q.getPeriodKey())||"lastMonth".equals(q.getPeriodKey()))return w;
  LocalDate last=q.getPeriodKey()==null&&q.getEnd()!=null?q.getEnd():n.toLocalDate();return new Window(w.key(),w.label(),w.start(),last.plusDays(1).atStartOfDay());
 }
 public Analysis analysis(Query q){
  var n=now();var r=request(q,n);var w=reportRange(q,r,n);
  var orders=detailQueries.conversionOrders(r);var receipts=conversionReceipts(r,orders);
  var population=new PerformanceConversion.Population(orders,receipts);
  List<Window> buckets=new ArrayList<>();
  for(LocalDate d=w.start().toLocalDate();d.atStartOfDay().isBefore(w.end());){
   LocalDate next=switch(q.getGrain()){case "week"->d.with(TemporalAdjusters.next(DayOfWeek.MONDAY));case "month"->d.withDayOfMonth(1).plusMonths(1);default->d.plusDays(1);};
   buckets.add(new Window(d.toString(),d.toString(),d.atStartOfDay(),next.atStartOfDay().isAfter(w.end())?w.end():next.atStartOfDay()));d=next;
  }
  var windows=new ArrayList<>(buckets);windows.add(w);
  var sums=amounts(r,windows,"total");var sources=amounts(r,windows,"source");
  var sourcePopulations=new HashMap<String,PerformanceConversion.Population>();
  for(var group:List.of("all","inbound","self"))sourcePopulations.put(group,new PerformanceConversion.Population("all".equals(group)?orders:orders.stream().filter(x->group.equals(sourceGroup(x))).toList(),List.of()));
  List<Metric> averages=new ArrayList<>();averages.add(metric(new Window("all","整体",w.start(),w.end()),amount(sums,w,"total"),population));
  for(var group:List.of("inbound","self"))averages.add(metric(new Window(group,sourceName(group),w.start(),w.end()),amount(sources,w,group),sourcePopulations.get(group)));
  var trend=buckets.stream().map(b->metric(b,amount(sums,b,"total"),population)).toList();
  Map<String,List<Metric>> averageTrends=new LinkedHashMap<>();for(var group:List.of("all","inbound","self"))averageTrends.put(group,buckets.stream().map(b->metric(b,amount("all".equals(group)?sums:sources,b,"all".equals(group)?"total":group),sourcePopulations.get(group))).toList());
  var byUser=amounts(r,List.of(w),"user");Set<Long> members=new LinkedHashSet<>();
  if(Set.of("SELF","USER").contains(q.getScopeType()))members.add(q.getScopeId());
  else {
   var orgs=access.orgs().stream().collect(Collectors.toMap(x->x.getDeptId(),Function.identity(),(a,b)->a));
   for(var user:access.sales()){var mapping=orgs.get(user.getDeptId());if(mapping!=null&&q.getScopeId().equals("DEPT".equals(q.getScopeType())?mapping.getDeptId():mapping.getCenterId())&&(r.isAllDepartments()||r.getDepartments().contains(user.getDeptId())))members.add(user.getId());}
  }
  byUser.values().stream().map(PerformanceAggregate::getUserId).filter(Objects::nonNull).forEach(members::add);
  var people=access.users(members).stream().filter(x->Objects.equals(x.getStatus(),0)).collect(Collectors.toMap(x->x.getId(),Function.identity()));members.retainAll(people.keySet());
  String period=targetPeriod(q,w,n);Set<Long> targetUsers=new HashSet<>(members);if(Set.of("SELF","USER").contains(q.getScopeType()))targetUsers.add(q.getScopeId());
  var targetBatch=period==null?null:targets.batch(List.of(new PerformanceTargetService.Period(period,w.start().toLocalDate())),targetUsers);
  var orderGroups=orders.stream().filter(x->x.getUserId()!=null).collect(Collectors.groupingBy(PerformanceFact::getUserId));
  var receiptGroups=receipts.stream().filter(x->x.getUserId()!=null).collect(Collectors.groupingBy(PerformanceFact::getUserId));
  List<Contribution> contributions=new ArrayList<>();
  for(Long user:members){var personOrders=orderGroups.getOrDefault(user,List.of());var pop=new PerformanceConversion.Population(personOrders,receiptGroups.getOrDefault(user,List.of()));var actual=metric(w,amount(byUser,w,user.toString()),pop);String name=personOrders.stream().map(PerformanceFact::getUserName).filter(Objects::nonNull).findFirst().orElse(people.get(user).getNickname());var target=targetBatch==null?null:targetBatch.resolve("USER",user,period,w.start().toLocalDate());contributions.add(new Contribution(user,name,actual,target==null||!target.complete()?null:ratio(actual.amount(),target.floorAmount()),target==null||!target.complete()?null:ratio(actual.amount(),target.sprintAmount()),ratio(actual.amount(),averages.getFirst().amount())));}
  var products=amounts(r,List.of(w),"product");var contributorSums=amounts(r,List.of(w),"contributor");
  var contributorGroups=aggregateGroups(contributorSums,w,false).stream().filter(g->members.stream().anyMatch(id->g.key().startsWith(id+"|"))).map(g->new Group(g.key(),g.label(),g.amount(),g.count(),ratio(g.amount(),averages.getFirst().amount()))).toList();
  return new Analysis(n,w.start().toLocalDate(),q.getEnd()==null?n.toLocalDate():q.getEnd(),averages,trend,aggregateGroups(sources,w,true),aggregateGroups(products,w,false),contributorGroups,contributions,averageTrends,targetBatch==null?null:targetBatch.resolve(q.getScopeType(),q.getScopeId(),period,w.start().toLocalDate()));
 }
 private List<Group> aggregateGroups(Map<String,PerformanceAggregate> sums,Window w,boolean source){
  var rows=sums.values().stream().filter(x->w.key().equals(x.getKey())&&x.getCount()>0).toList();var total=rows.stream().map(PerformanceAggregate::getAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
  return rows.stream().map(x->{String key=source?x.getGroupKey()+"|"+sourceName(x.getGroupKey()):x.getGroupKey();String label=key.contains("|")?key.substring(key.indexOf('|')+1):key;return new Group(key,label,x.getAmount(),x.getCount(),ratio(x.getAmount(),total));}).sorted(Comparator.comparing(Group::amount).reversed()).toList();
 }
 private String targetPeriod(Query q,Window w,LocalDateTime n){
  LocalDate d=w.start().toLocalDate(),end=q.getEnd()==null?n.toLocalDate():q.getEnd();String period=null;
  if(q.getPeriodKey()!=null&&Set.of("week","lastWeek","month","lastMonth","quarter","year").contains(q.getPeriodKey()))period=switch(q.getPeriodKey()){case "lastWeek"->"week";case "lastMonth"->"month";default->q.getPeriodKey();};
  else if(d.getDayOfYear()==1&&end.equals(d.plusYears(1).minusDays(1)))period="year";
  else if(d.getDayOfMonth()==1&&(d.getMonthValue()-1)%3==0&&end.equals(d.plusMonths(3).minusDays(1)))period="quarter";
  else if(d.getDayOfMonth()==1&&(end.equals(d.withDayOfMonth(d.lengthOfMonth()))||d.equals(n.toLocalDate().withDayOfMonth(1))&&end.equals(n.toLocalDate())))period="month";
  else if(d.getDayOfWeek()==DayOfWeek.MONDAY&&end.equals(d.plusDays(6)))period="week";
  return period;
 }

 public static String sourceGroup(PerformanceFact fact){return "repurchase".equals(fact.getOrderType())?"self":Set.of("inbound","self").contains(Objects.toString(fact.getGroupKey(),""))?fact.getGroupKey():"unknown";}
 private String sourceName(String k){return switch(Objects.toString(k,"unknown")){case "inbound"->"线上引流";case "self"->"非引流";case "repurchase"->"复购";default->"其他";};}
 public List<HistoryMonth> history(Query q){
  var n=now();var r=request(q,n);int year=q.getYear()==null?n.getYear():q.getYear();List<HistoryMonth> rows=new ArrayList<>();List<Window> windows=new ArrayList<>();
  for(int m=1;m<=12;m++){var start=LocalDate.of(year,m,1).atStartOfDay();boolean future=start.isAfter(n);var end=start.plusMonths(1);if(!future&&end.isAfter(n))end=n;var previousStart=start.minusYears(1);var previousEnd=end.minusYears(1);rows.add(new HistoryMonth(m,null,null,start,end,previousStart,previousEnd,future));if(!future){windows.add(new Window("m"+m,"",start,end));windows.add(new Window("p"+m,"",previousStart,previousEnd));}}
  var sums=amounts(r,windows,"total");return rows.stream().map(x->new HistoryMonth(x.month(),x.future()?null:sums.getOrDefault("m"+x.month()+"/total",new PerformanceAggregate()).getAmount(),x.future()?null:sums.getOrDefault("p"+x.month()+"/total",new PerformanceAggregate()).getAmount(),x.start(),x.end(),x.previousStart(),x.previousEnd(),x.future())).toList();
 }
 private List<Group> countGroups(List<PerformanceFact> rows,Function<PerformanceFact,String> key){return rows.stream().collect(Collectors.groupingBy(key,LinkedHashMap::new,Collectors.mapping(PerformanceFact::getLeadId,Collectors.toSet()))).entrySet().stream().map(e->new Group(e.getKey(),e.getKey(),BigDecimal.ZERO,e.getValue().size(),ratio(BigDecimal.valueOf(e.getValue().size()),BigDecimal.valueOf(rows.stream().map(PerformanceFact::getLeadId).distinct().count())))).toList();}
 public LeadWorkload leadWorkload(Query q){var n=now();var r=request(q,n);var w=reportRange(q,r,n);return workload(q,r,w,n,reports.receipts(r));}
 public LeadCalendar leadCalendar(Query q){var n=now();var r=request(q,n);var w=reportRange(q,r,n);checkCalendar(q,w);return calendar(q,r,w,n,reports.receipts(r),true);}
 private void checkCalendar(Query q,Window w){if(java.time.temporal.ChronoUnit.DAYS.between(w.start().toLocalDate(),q.getEnd()==null?w.end().toLocalDate():q.getEnd())>31)throw PerformanceAccess.invalid("日历每次仅支持一个月");}
 public LeadReport leads(Query q){
  var n=now();var r=request(q,n);var w=reportRange(q,r,n);if(q.isCalendar())checkCalendar(q,w);var receipts=reports.receipts(r);
  var work=workload(q,r,w,n,receipts);var calendar=calendar(q,r,w,n,receipts,q.isCalendar());
  return new LeadReport(n,work.start(),work.end(),work.workload(),work.categories(),work.stages(),calendar.calendar(),calendar.funnel(),work.followUp(),work.categoryTrend());
 }
 private LeadWorkload workload(Query q,PerformanceReportQuery r,Window w,LocalDateTime n,List<PerformanceFact> receipts){
  Map<String,Long> counts=new HashMap<>();reports.activity(r).forEach(x->counts.put(x.getKey(),x.getCount()));
  Map<String,Long> work=new LinkedHashMap<>();work.put("received",receipts.stream().map(PerformanceFact::getLeadId).distinct().count());work.put("valid",receipts.stream().filter(x->Set.of("valid","converted","won").contains(x.getStatus())).map(PerformanceFact::getLeadId).distinct().count());
  for(var key:List.of("assigned","missed","followUps"))work.put(key,counts.getOrDefault(key,0L));
  return new LeadWorkload(n,w.start().toLocalDate(),q.getEnd()==null?n.toLocalDate():q.getEnd(),work,countGroups(receipts,x->Objects.toString(x.getCategory(),"历史分类缺失")),countGroups(receipts,x->Objects.toString(x.getStage(),"阶段未记录")),List.of(count("completed","已完成",counts.getOrDefault("completed",0L)),count("pending","未到期未完成",counts.getOrDefault("pending",0L)),count("overdue","逾期未完成",counts.getOrDefault("overdue",0L)),count("cancelled","已取消",counts.getOrDefault("cancelled",0L))),categoryTrend(receipts,q.getGrain(),w));
 }
 private LeadCalendar calendar(Query q,PerformanceReportQuery r,Window w,LocalDateTime n,List<PerformanceFact> receipts,boolean includeDays){
  List<CalendarDay> days=new ArrayList<>();
  if(includeDays){var tasks=reports.calendarTasks(r);var indexed=new PerformanceCalendar.Index(tasks);for(LocalDate d=w.start().toLocalDate();!d.isAfter(q.getEnd()==null?w.end().toLocalDate():q.getEnd());d=d.plusDays(1))days.add(indexed.summarize(d,receipts,n));}
  long received=receipts.stream().map(PerformanceFact::getLeadId).distinct().count(),valid=receipts.stream().filter(x->Set.of("valid","converted","won").contains(x.getStatus())).map(PerformanceFact::getLeadId).distinct().count();
  long converted=reports.cohortOrders(r).stream().map(PerformanceFact::getLeadId).distinct().count();
  return new LeadCalendar(n,w.start().toLocalDate(),q.getEnd()==null?n.toLocalDate():q.getEnd(),days,List.of(count("received","接收客资",received),count("valid","有效客资",valid),count("converted","成交客资",converted)));
 }
 private List<CategoryPoint> categoryTrend(List<PerformanceFact> rows,String grain,Window window){
  Map<String,Map<String,Set<Long>>> buckets=new TreeMap<>();
  for(var x:rows)buckets.computeIfAbsent(categoryBucket(x.getReceivedAt(),grain),k->new TreeMap<>()).computeIfAbsent(Objects.toString(x.getCategory(),"历史分类缺失"),k->new HashSet<>()).add(x.getLeadId());
  Set<String> categories=rows.stream().map(x->Objects.toString(x.getCategory(),"历史分类缺失")).collect(Collectors.toCollection(TreeSet::new));
  for(var day=window.start().toLocalDate();day.atStartOfDay().isBefore(window.end());day=day.plusDays(1)){
   var bucket=buckets.computeIfAbsent(categoryBucket(day.atStartOfDay(),grain),k->new TreeMap<>());for(var category:categories)bucket.computeIfAbsent(category,k->new HashSet<>());
  }
  List<CategoryPoint> result=new ArrayList<>();buckets.forEach((bucket,values)->values.forEach((category,ids)->result.add(new CategoryPoint(bucket,category,ids.size()))));return result;
 }
 private String categoryBucket(LocalDateTime at,String grain){LocalDate d=at.toLocalDate();return switch(grain){case "week"->d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();case "month"->d.toString().substring(0,7);default->d.toString();};}
 private Group count(String k,String label,long count){return new Group(k,label,BigDecimal.ZERO,count,null);}
 private String userName(Long id){if(id==null)return "未分配";var user=access.user(id);return user==null?"人员已不存在":user.getNickname();}
 private Detail taskDetail(PerformanceFact x,LocalDateTime n,Map<Long,String> names){
  String label=switch(x.getGroupKey()){case "lead_assignment_accept"->"接收客资";case "lead_qualification"->"有效性判定";case "lead_first_follow_up"->"首次跟进";default->"后续跟进";};
  String state=switch(Objects.toString(x.getStatus(),"")){case "pending"->"待处理";case "completed"->"已完成";case "cancelled"->"已取消";default->"状态未记录";};
  if(Boolean.TRUE.equals(x.getCurrentQualification())&&"overdue".equals(x.getOutcome()))state="逾期未判定";
  return new Detail(x.getId(),x.getNumber(),"task",label,x.getOccurredAt(),null,state,x.getLeadId(),names.computeIfAbsent(x.getOwnerUserId(),this::userName),names.computeIfAbsent(x.getUserId(),this::userName),x.getReceivedAt(),x.getDueAt(),x.getCategory(),x.getStage(),(pending(x)||Boolean.TRUE.equals(x.getCurrentQualification())&&"overdue".equals(x.getOutcome()))&&x.getDueAt()!=null&&x.getDueAt().isBefore(n)?Duration.between(x.getDueAt(),n).toMinutes():null);
 }
 public List<MissingTarget> missingTargets(Query q){access.authorize(q,false);if(!access.has("zsjos:sales-performance-target:query"))throw PerformanceAccess.denied();return targets.missing(q.getScopeType(),q.getScopeId(),now().toLocalDate().withDayOfMonth(1));}
 public PageResult<Detail> details(Query q){
  if(!access.has("zsjos:sales-performance:detail"))throw PerformanceAccess.denied();
  access.authorize(q,false);
  var scope=access.historicalScope(q);
  var n=now();
  var request=new PerformanceDetailQuery().setTenant(tenant()).setType(q.getScopeType()).setId(q.getScopeId())
    .setAllDepartments(scope.allDepartments()).setMissingDepartment(scope.missingDepartment()).setDepartments(scope.departments());
  if(q.isCumulative()){
   var first=detailQueries.firstDate(request);
   q.setStart((first==null?n:first).toLocalDate());q.setEnd(n.toLocalDate());q.setGrain("month");
  }
  var w=range(q,n);
  request.setMetric(q.getMetric()).setDimension(q.getDimension()).setGroupKey(q.getGroupKey())
    .setStart(w.start()).setEnd(w.end()).setDueEnd(dueWindow(q,w,n).end())
    .setNow(n).setToday(n.toLocalDate().atStartOfDay()).setTomorrow(n.toLocalDate().plusDays(1).atStartOfDay())
    .setOffset((long)(q.getPageNo()-1)*q.getPageSize()).setSize(q.getPageSize());
  if("conversion".equals(q.getMetric()))return conversionDetails(request,w);
  long total=detailQueries.count(request);
  if(request.getOffset()>=total)return new PageResult<>(List.of(),total);
  Map<Long,String> names=new HashMap<>();
  var rows=detailQueries.page(request).stream().map(x->switch(q.getMetric()){
   case "orders" -> new Detail(x.getId(),x.getNumber(),"order",sourceName(sourceGroup(x)),x.getOccurredAt(),x.getAmount(),"审批通过");
   case "leads","valid" -> new Detail(x.getId(),x.getNumber(),"lead",x.getLabel(),x.getOccurredAt(),null,x.getStatus());
   case "assigned","missed","followUps" -> new Detail(x.getId(),x.getNumber(),"lead",x.getLabel(),x.getOccurredAt(),null,x.getGroupKey());
   default -> taskDetail(x,n,names);
  }).toList();
  return new PageResult<>(rows,total);
 }
 private PageResult<Detail> conversionDetails(PerformanceDetailQuery request,Window window){
  var orders=detailQueries.conversionOrders(request);
  request.setConversionLeadIds(orders.stream().map(PerformanceFact::getLeadId).filter(Objects::nonNull).collect(Collectors.toSet()));
  var receipts=detailQueries.conversionReceipts(request);
  var population=PerformanceConversion.population(window,orders,receipts);
  Map<Long,String> numbers=new HashMap<>();
  receipts.stream().filter(x->x.getNumber()!=null).forEach(x->numbers.putIfAbsent(x.getLeadId(),x.getNumber()));
  var page=population.entrySet().stream().skip(request.getOffset()).limit(request.getSize()).map(e->{
   var receipt=e.getValue().receipt();var order=e.getValue().order();
   return new Detail(e.getKey(),receipt==null?numbers.get(e.getKey()):receipt.getNumber(),"lead",
     e.getValue().previous()?"往期接收有效期内成交":"期间新接有效",receipt==null?order.getReceivedAt():receipt.getReceivedAt(),
     order==null?null:order.getAmount(),order==null?"未成交":"已成交");
  }).toList();
  return new PageResult<>(page,(long)population.size());
 }
}
