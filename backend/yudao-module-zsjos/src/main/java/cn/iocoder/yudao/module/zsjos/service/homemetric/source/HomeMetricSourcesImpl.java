package cn.iocoder.yudao.module.zsjos.service.homemetric.source;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.dal.mysql.homemetric.MetricSourceMapper;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.service.homemetric.*;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.*;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadManagementServiceImpl;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderObjectPermissionService;
import cn.iocoder.yudao.module.zsjos.service.registration.StudentServiceReadService;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.StudentServicePageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.StudentServicePageSql;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.*;
import java.math.BigDecimal;

@Service
public class HomeMetricSourcesImpl implements HomeMetricSources {
    @Resource private PermissionApi permissions;
    @Resource private AdvancedFilterService filters;
    @Resource private LeadManagementServiceImpl leads;
    @Resource private SalesOrderObjectPermissionService orders;
    @Resource private StudentServiceReadService students;
    @Resource private MetricSourceMapper mapper;
    @Resource private MetricSourceDeadline deadlines;
    @Resource private ExistingPageMetricSources existing;
    @Resource private BuiltinMetricSources builtin;
    @Resource private cn.iocoder.yudao.module.zsjos.service.cashback.CashbackSearchService cashbacks;
    private static final List<Source> SOURCES=List.of(
        new Source("withdrawal","提现","withdrawal",List.of(new Field("applicationAmount","申请金额","CNY"),new Field("approvedAmount","批准金额","CNY")),List.of(new Field("submittedAt","提交时间","TIME"),new Field("reviewedAt","审核时间","TIME"),new Field("paidAt","打款时间","TIME")),"/zsjos/withdrawal"),
        new Source("cashback","返现","cashback",List.of(new Field("amount","返现金额","CNY"),new Field("baseAmount","计算基数","CNY")),List.of(new Field("generatedAt","生成时间","TIME"),new Field("availableAt","可用时间","TIME"),new Field("settledAt","结算时间","TIME")),"/zsjos/cashback"),
        new Source("lead","客资管理","lead",List.of(new Field("followUpCount","跟进次数","COUNT")),List.of(new Field("submittedAt","提交时间","TIME")),"/zsjos/leads/manage"),
        new Source("order","订单管理","order",List.of(new Field("totalAmount","订单总金额","CNY")),List.of(new Field("submittedAt","提交时间","TIME"),new Field("effectiveAt","生效时间","TIME")),"/zsjos/sales-orders"),
        new Source("student","我的学员（按学员去重）","student_service",List.of(),List.of(new Field("activatedAt","服务激活时间","TIME")),"/zsjos/my-students")
    );
    private Source source(String key) { return SOURCES.stream().filter(s->s.key().equals(key)).findFirst().orElseThrow(()->new IllegalArgumentException("不支持的指标来源")); }
    private boolean allowed(String key,Long actor) {
        if(builtin.supports(key)) return builtin.canRead(key,actor);
        if(existing.supports(key)) return existing.canRead(key,actor);
        return switch(key) {
            case "withdrawal" -> permissions.hasAnyPermissions(actor,"zsjos:withdrawal:finance-query","zsjos:withdrawal:admin-query","zsjos:withdrawal:my-query");
            case "cashback" -> permissions.hasAnyPermissions(actor,"zsjos:cashback:finance-query","zsjos:cashback:my-query");
            case "lead" -> permissions.hasAnyPermissions(actor,"zsjos:lead:query")
                    && (permissions.hasTenantReadAllAccess(actor) || permissions.hasAnyPermissions(actor,"zsjos:lead:query-submitted","zsjos:lead:query-owned","zsjos:lead:query-all"));
            case "order" -> permissions.hasAnyPermissions(actor,"zsjos:sales-order:query-management");
            case "student" -> permissions.hasAnyPermissions(actor,"zsjos:student:query-my");
            default -> false;
        };
    }
    @Override public List<Source> catalog(Long actor) { return java.util.stream.Stream.concat(SOURCES.stream().filter(s->allowed(s.key(),actor)),java.util.stream.Stream.concat(existing.catalog(actor).stream(),builtin.catalog(actor).stream())).toList(); }
    @Override public MetricEvaluation.Sources bind(HomeMetricSpec spec,Long actor) {
        Map<String,HomeMetricSpec.Operand> operands=new LinkedHashMap<>();
        for(var item:spec.operands()) if(operands.put(item.calculation().name(),item)!=null) throw new IllegalArgumentException("重复操作数");
        return new MetricEvaluation.Sources() {
            public MetricUnit validate(MetricEvaluation.Operand op) {
                var item=operands.get(op.name()); if(builtin.supports(op.source())) return builtin.validate(item); if(existing.supports(op.source())) return existing.validate(item); var src=source(op.source());
                if(item==null) throw new IllegalArgumentException("操作数缺失");
                filters.validate(src.scene(),item.filter());
                if(op.period()!=MetricPeriod.Kind.ALL && src.timeFields().stream().noneMatch(f->f.key().equals(item.timeField()))) throw new IllegalArgumentException("时间字段无效");
                if(!op.aggregate().requiresField()) return MetricUnit.of("COUNT");
                return MetricUnit.of(src.fields().stream().filter(f->f.key().equals(op.field())).findFirst().orElseThrow(()->new IllegalArgumentException("聚合字段无效")).unit());
            }
            public boolean canRead(MetricEvaluation.Operand op) { return allowed(op.source(),actor); }
            public MetricFormula.Result aggregate(MetricEvaluation.Operand op,MetricPeriod.Range range,java.time.Duration remaining) {
                return deadlines.execute(remaining, () -> aggregate(op,range));
            }
            public MetricFormula.Result aggregate(MetricEvaluation.Operand op,MetricPeriod.Range range) {
                if(!allowed(op.source(),actor)) return MetricFormula.Result.failure(MetricFormula.Status.FORBIDDEN);
                if(builtin.supports(op.source())) return builtin.aggregate(operands.get(op.name()),range,actor);
                if(existing.supports(op.source())) return existing.aggregate(operands.get(op.name()),range,actor);
                var query=query(operands.get(op.name()),range,actor);
                String expression=op.aggregate().requiresField()?op.aggregate().name()+"(v)":"COUNT(*)";
                return op.aggregate().result(mapper.aggregate(new AdvancedFilterQuery("SELECT "+expression+" FROM ("+query.whereSql()+") metric_rows",query.parameters())));
            }
        };
    }
    private record Query(String whereSql,Map<String,Object> parameters) {}
    private Query query(HomeMetricSpec.Operand item,MetricPeriod.Range range,Long actor) {
        var op=item.calculation(); source(op.source());
        if(!allowed(op.source(),actor)) throw new org.springframework.security.access.AccessDeniedException("无权读取指标来源");
        Map<String,Object> params=new LinkedHashMap<>(); params.put("tenantId",TenantContextHolder.getRequiredTenantId());
        String from,identity,number,name,status,value="NULL",time;
        if("withdrawal".equals(op.source())) {
            var q=filters.buildWithdrawalQuery(item.filter());if(q!=null)params.putAll(q.getParameters());
            from=" FROM zsjos_withdrawal w WHERE w.deleted=0 AND w.tenant_id=#{query.parameters.tenantId}";
            if(q!=null)from+=" AND ("+q.getWhereSql()+")";
            if(!permissions.hasAnyPermissions(actor,"zsjos:withdrawal:finance-query","zsjos:withdrawal:admin-query")){params.put("metricApplicant",actor);from+=" AND w.applicant_user_id=#{query.parameters.metricApplicant}";}
            identity="w.id";number="w.withdrawal_no";name="NULL";status="w.status";
            time=switch(item.timeField()==null?"":item.timeField()){case "reviewedAt"->"w.reviewed_at";case "paidAt"->"w.paid_at";default->"w.submitted_at";};
            if("applicationAmount".equals(op.field()))value="w.application_amount";else if("approvedAmount".equals(op.field()))value="w.approved_amount";
        } else if("cashback".equals(op.source())) {
            var q=cashbacks.metricQuery(item.filter(),actor);params.putAll(q.getParameters());
            from=" FROM zsjos_cashback c WHERE "+q.getWhereSql();identity="c.id";number="c.cashback_no";name="NULL";status="c.status";
            time=switch(item.timeField()==null?"":item.timeField()){case "availableAt"->"c.available_at";case "settledAt"->"c.settled_at";default->"c.generated_at";};
            if("amount".equals(op.field()))value="c.amount";else if("baseAmount".equals(op.field()))value="c.base_amount";
        } else if("student".equals(op.source())) {
            var req=new StudentServicePageReqVO();req.setAdvancedFilter(item.filter());
            var q=students.metricQuery(actor,req);params.putAll(q.getParameters());
            from=StudentServicePageSql.FROM.replace("${query.whereSql}",q.getWhereSql());
            identity="sr.person_id";number="p.person_no";name="NULL";status="NULL";time="sr.activated_at";
        } else {
            boolean lead="lead".equals(op.source());String alias=lead?"l":"o";
            var q=lead?filters.buildLeadQuery(item.filter()):filters.buildOrderQuery(item.filter());
            if(q!=null) params.putAll(q.getParameters());
            from=" FROM "+(lead?"zsjos_lead l":"zsjos_order o")+" WHERE "+alias+".deleted=0 AND "+alias+".tenant_id=#{query.parameters.tenantId}";
            if(q!=null)from+=" AND ("+q.getWhereSql()+")";
            if(lead) {
                var scope=leads.metricVisibilityScope(actor);
                if(!scope.queryAll()) {
                    String sourceIds=in(scope.sourceUserIds(),params,"source"),ownerIds=in(scope.ownerUserIds(),params,"owner");
                    from+=" AND ((l.provider_owner_type='system_user' AND l.provider_owner_id IN ("+sourceIds+")) OR l.owner_user_id IN ("+ownerIds+") OR (l.assignment_status='recycle_pending' AND l.recycle_source_owner_user_id IN ("+ownerIds+")))";
                }
                identity="l.id";number="l.lead_no";name="NULL";status="l.status";time="l.submitted_at";
                if("followUpCount".equals(op.field()))value="l.follow_up_count";
            } else {
                var scope=orders.resolveManagementScope(actor);
                if(!scope.isAll())from+=" AND o.submitter_user_id IN ("+in(scope.getSubmitterUserIds(),params,"owner")+")";
                identity="o.id";number="o.order_no";name="NULL";status="o.status";
                time="effectiveAt".equals(item.timeField())?"o.effective_at":"o.submitted_at";
                if("totalAmount".equals(op.field()))value="o.total_amount";
            }
        }
        if(range!=null) { params.put("metricStart",range.start());params.put("metricEnd",range.end());from+=" AND "+time+">=#{query.parameters.metricStart} AND "+time+"<#{query.parameters.metricEnd}"; }
        if(op.aggregate().requiresField() && "NULL".equals(value))throw new IllegalArgumentException("聚合字段无效");
        return new Query("SELECT DISTINCT "+identity+" AS id,"+number+" AS businessNo,"+name+" AS name,"+status+" AS status,"+value+" AS v"+from,params);
    }
    private String in(Collection<Long> ids,Map<String,Object> params,String prefix) {
        if(ids==null||ids.isEmpty())return "NULL";
        List<String> keys=new ArrayList<>();for(Long id:ids){String key="metric_"+prefix+keys.size();params.put(key,id);keys.add("#{query.parameters."+key+"}");}return String.join(",",keys);
    }
    @Override public PageResult<Map<String,Object>> details(HomeMetricSpec.Operand operand,MetricPeriod.Range range,Long actor,int pageNo,int pageSize) {
        return deadlines.execute(java.time.Duration.ofSeconds(5), () -> detailsWithinDeadline(operand,range,actor,pageNo,pageSize));
    }
    private PageResult<Map<String,Object>> detailsWithinDeadline(HomeMetricSpec.Operand operand,MetricPeriod.Range range,Long actor,int pageNo,int pageSize) {
        if(pageNo<1||pageSize<1||pageSize>100)throw new IllegalArgumentException("分页参数无效");
        if(builtin.supports(operand.calculation().source())) return builtin.details(operand,range,actor,pageNo,pageSize);
        if(existing.supports(operand.calculation().source())) return existing.details(operand,range,actor,pageNo,pageSize);
        filters.validate(source(operand.calculation().source()).scene(),operand.filter());
        Query q=query(operand,range,actor);
        long count=mapper.aggregate(new AdvancedFilterQuery("SELECT COUNT(*) FROM ("+q.whereSql()+") metric_rows",q.parameters())).longValueExact();
        q.parameters().put("metricLimit",pageSize);q.parameters().put("metricOffset",(long)(pageNo-1)*pageSize);
        var rows=mapper.details(new AdvancedFilterQuery(q.whereSql()+" ORDER BY id LIMIT #{query.parameters.metricLimit} OFFSET #{query.parameters.metricOffset}",q.parameters()));
        var safeRows=rows.stream().map(row->{
            Map<String,Object> copy=new LinkedHashMap<>(row);
            copy.replaceAll((key,value)->"v".equalsIgnoreCase(key) && value instanceof BigDecimal decimal
                    ? decimal.toPlainString() : "v".equalsIgnoreCase(key) && value instanceof Number ? value.toString() : value);
            return copy;
        }).toList();
        return new PageResult<>(safeRows,count);
    }
}
