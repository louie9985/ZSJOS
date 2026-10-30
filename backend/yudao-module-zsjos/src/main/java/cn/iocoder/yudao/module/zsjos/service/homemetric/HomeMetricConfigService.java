package cn.iocoder.yudao.module.zsjos.service.homemetric;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.homemetric.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.homemetric.*;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.*;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class HomeMetricConfigService {
    @Resource private HomeMetricMapper mapper;
    @Resource private HomeMetricVersionMapper versions;
    @Resource private PermissionApi permissions;
    @Resource private AdminUserApi users;
    @Resource private DeptApi departments;
    @Resource private HomeMetricSources sources;
    @Resource private StringRedisTemplate redis;
    @Resource private HomeMetricDictionarySnapshots snapshots;
    @Resource private cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService filters;
    public record Config(Long id,int version,boolean enabled,Integer publishedVersion,HomeMetricSpec spec) {}
    public record Save(Long id,Integer version,HomeMetricSpec spec) {}
    public record Command(Long id,Integer version,Integer targetVersion) {}
    public record Value(String status,String value) {}
    public record Input(String name,String sourceLabel,MetricPeriod.Range range,Value result) {}
    public record Evaluation(Instant evaluatedAt,Value result,MetricUnit unit,List<Input> inputs) {}
    public record Context(Long id,int version,Long actor,Long tenant,HomeMetricSpec spec,Evaluation evaluation,Instant expiresAt) {}
    public record ContextView(Long id,int version,HomeMetricSpec spec,Evaluation evaluation,Instant expiresAt) {}
    public ContextView contextView(String token,Long actor) {var c=context(token,actor);return new ContextView(c.id(),c.version(),publicSpec(c.spec()),c.evaluation(),c.expiresAt());}
    private HomeMetricSpec publicSpec(HomeMetricSpec s) {return new HomeMetricSpec(s.code(),s.name(),s.description(),s.unit(),s.sort(),null,List.of(),List.of(),s.action(),s.formula(),s.percentage(),s.decimals(),s.operands().stream().map(o->new HomeMetricSpec.Operand(o.calculation(),null,o.timeField())).toList());}
    private HomeMetricSpec freeze(HomeMetricSpec s,Instant now) {
        return new HomeMetricSpec(s.code(),s.name(),s.description(),s.unit(),s.sort(),s.viewPermission(),s.userIds(),s.deptIds(),s.action(),s.formula(),s.percentage(),s.decimals(),
                s.operands().stream().map(o->new HomeMetricSpec.Operand(o.calculation(),filters.freezeRelativeDates(o.filter(),now),o.timeField())).toList()).withSnapshots(s.dictionarySnapshots()==null?Map.of():s.dictionarySnapshots());
    }
    public record Evaluated(Long id,int publishedVersion,HomeMetricSpec spec,String contextId,Evaluation evaluation) {}
    public record Details(String contextId,String operand,int pageNo,int pageSize) {}

    public List<HomeMetricSources.Source> catalog(Long actor) { return sources.catalog(actor); }
    public List<Config> list() { return mapper.selectList(new LambdaQueryWrapper<HomeMetricDO>().orderByDesc(HomeMetricDO::getId)).stream().map(r->config(r,false)).toList(); }
    @ZsjosPermission(bizType="home-metric",bizId="#id",action="query")
    public Config get(Long id) { return config(require(id),false); }
    public HomeMetricDO require(Long id) { var row=mapper.selectById(id); if(row==null) throw exception(HOME_METRIC_NOT_FOUND); return row; }
    private HomeMetricSpec parse(String json) { return JsonUtils.parseObject(json,HomeMetricSpec.class); }
    private HomeMetricVersionDO version(Long id,int version) {
        var row=versions.selectOne(new LambdaQueryWrapper<HomeMetricVersionDO>().eq(HomeMetricVersionDO::getMetricId,id).eq(HomeMetricVersionDO::getVersion,version));
        if(row==null) throw exception(HOME_METRIC_NOT_FOUND); return row;
    }
    private Config config(HomeMetricDO row,boolean published) {
        return new Config(row.getId(),row.getVersion(),Boolean.TRUE.equals(row.getEnabled()),row.getPublishedVersion(),
                parse(published?version(row.getId(),row.getPublishedVersion()).getSpecJson():row.getDraftJson()));
    }
    public void validate(HomeMetricSpec spec,Long actor) {
        validateRuntime(spec,actor);
        Set<Long> ids=new LinkedHashSet<>(spec.userIds());
        if(ids.contains(null))throw exception(HOME_METRIC_INVALID);
        if(!ids.isEmpty()) {
            var selected=users.getUserList(ids);
            Set<Long> valid=new HashSet<>();for(var user:selected)if(Integer.valueOf(0).equals(user.getStatus()))valid.add(user.getId());
            if(!valid.containsAll(ids))throw exception(HOME_METRIC_INVALID);
        }
        departments.validateDeptList(spec.deptIds());
    }
    public void validateRuntime(HomeMetricSpec spec,Long actor) {
        validateRuntime(spec,actor,false);
    }
    private void validateRuntime(HomeMetricSpec spec,Long actor,boolean resolved) {
        if(spec==null || spec.code()==null || !spec.code().matches("[a-z][a-z0-9_-]{0,63}") || spec.name()==null || spec.name().isBlank() || spec.name().length()>80
                || spec.description()!=null && spec.description().length()>1000 || spec.unit()!=null && spec.unit().length()>30
                || spec.sort()<0 || spec.sort()>9999 || !Set.of("MODAL","ROUTE").contains(spec.action()==null?"":spec.action())
                || spec.userIds()==null || spec.deptIds()==null || spec.userIds().size()>500 || spec.deptIds().size()>500
                || spec.operands()==null || !permissions.isPermissionDefined(spec.viewPermission())) throw exception(HOME_METRIC_INVALID);
        if(resolved)return; // evaluateResolved validates the formula against the stored interval map.
        try {new MetricEvaluation(Clock.systemUTC(),Duration.ofSeconds(20)).validate(spec.definition(),sources.bind(spec,actor));}
        catch(IllegalArgumentException|NullPointerException error){throw exception(HOME_METRIC_INVALID);}
    }
    @Transactional(rollbackFor=Exception.class)
    @ZsjosPermission(bizType="home-metric",bizId="#request.id",action="update")
    public Config updateDraft(Save request,Long actor) { return save(request,actor); }
    @Transactional(rollbackFor=Exception.class)
    public Config save(Save request,Long actor) {
        if(request.id()!=null) check(request.id(),"update",actor);
        validate(request.spec(),actor);
        var spec=snapshots.capture(request.spec(),request.id()==null?null:parse(require(request.id()).getDraftJson()),actor);
        if(request.id()==null) {
            if(mapper.selectList(new LambdaQueryWrapper<HomeMetricDO>().orderByAsc(HomeMetricDO::getId).last("FOR UPDATE")).size()>=200) throw exception(HOME_METRIC_INVALID);
            var row=new HomeMetricDO(); row.setCode(spec.code());row.setDraftJson(JsonUtils.toJsonString(spec));row.setVersion(0);row.setEnabled(false);mapper.insert(row);return config(row,false);
        }
        check(request.id(),"update",actor);
        var row=require(request.id());
        if(!row.getCode().equals(request.spec().code())) throw exception(HOME_METRIC_INVALID);
        update(row,request.version(),new LambdaUpdateWrapper<HomeMetricDO>().set(HomeMetricDO::getDraftJson,JsonUtils.toJsonString(spec)));
        return config(require(row.getId()),false);
    }
    private void update(HomeMetricDO row,Integer expected,LambdaUpdateWrapper<HomeMetricDO> changes) {
        if(expected==null || !expected.equals(row.getVersion()) || mapper.update(null,changes.eq(HomeMetricDO::getId,row.getId()).eq(HomeMetricDO::getVersion,expected).set(HomeMetricDO::getVersion,expected+1))!=1)
            throw exception(HOME_METRIC_CONFLICT);
    }
    public void check(Long id,String action,Long actor) { require(id);if(!permissions.hasAnyPermissions(actor,"zsjos:home-metric:"+action)) throw exception(HOME_METRIC_DENIED); }
    @Transactional(rollbackFor=Exception.class)
    @ZsjosPermission(bizType="home-metric",bizId="#cmd.id",action="publish")
    public Config publish(Command cmd,Long actor) {
        check(cmd.id(),"publish",actor); var row=require(cmd.id());
        if(cmd.targetVersion()!=null&&cmd.targetVersion()<1)throw exception(HOME_METRIC_ROLLBACK_INVALID);
        var spec=cmd.targetVersion()==null?parse(row.getDraftJson()):parse(version(row.getId(),cmd.targetVersion()).getSpecJson());validate(spec,actor);
        spec=snapshots.capture(spec,spec,actor);
        if(!Boolean.TRUE.equals(row.getEnabled()) && mapper.selectList(new LambdaQueryWrapper<HomeMetricDO>().orderByAsc(HomeMetricDO::getId).last("FOR UPDATE")).stream().filter(r->Boolean.TRUE.equals(r.getEnabled())).count()>=12) throw exception(HOME_METRIC_ENABLED_LIMIT);
        int next=row.getVersion()+1;
        update(row,cmd.version(),new LambdaUpdateWrapper<HomeMetricDO>().set(HomeMetricDO::getEnabled,true).set(HomeMetricDO::getPublishedVersion,next));
        var snapshot=new HomeMetricVersionDO();snapshot.setMetricId(row.getId());snapshot.setVersion(next);snapshot.setSpecJson(JsonUtils.toJsonString(spec));versions.insert(snapshot);
        return config(require(row.getId()),false);
    }
    @Transactional(rollbackFor=Exception.class)
    @ZsjosPermission(bizType="home-metric",bizId="#cmd.id",action="publish")
    public Config disable(Command cmd,Long actor) { check(cmd.id(),"publish",actor);var row=require(cmd.id());update(row,cmd.version(),new LambdaUpdateWrapper<HomeMetricDO>().set(HomeMetricDO::getEnabled,false));return config(require(row.getId()),false); }
    @ZsjosPermission(bizType="home-metric",bizId="#id",action="query")
    public List<Config> history(Long id,Long actor) {check(id,"query",actor);return versions.selectList(new LambdaQueryWrapper<HomeMetricVersionDO>().eq(HomeMetricVersionDO::getMetricId,id).orderByDesc(HomeMetricVersionDO::getVersion).last("LIMIT 200")).stream().map(v->new Config(id,v.getVersion(),false,v.getVersion(),parse(v.getSpecJson()))).toList();}
    @Transactional(rollbackFor=Exception.class)
    @ZsjosPermission(bizType="home-metric",bizId="#cmd.id",action="query")
    public Config copy(Command cmd,Long actor) {check(cmd.id(),"query",actor);var row=require(cmd.id());if(!Objects.equals(cmd.version(),row.getVersion()))throw exception(HOME_METRIC_CONFLICT);var s=parse(row.getDraftJson());return save(new Save(null,null,new HomeMetricSpec("copy_"+UUID.randomUUID().toString().replace("-",""),s.name(),s.description(),s.unit(),s.sort(),s.viewPermission(),s.userIds(),s.deptIds(),s.action(),s.formula(),s.percentage(),s.decimals(),s.operands())),actor);}
    public boolean visible(HomeMetricSpec spec,Long actor) {
        if(!permissions.isPermissionDefined(spec.viewPermission()) || !permissions.hasAnyPermissions(actor,spec.viewPermission())) return false;
        return inAudience(spec,actor,users.getUser(actor));
    }
    private boolean inAudience(HomeMetricSpec spec,Long actor,AdminUserRespDTO user) {
        return user!=null && Integer.valueOf(0).equals(user.getStatus())
                && (spec.userIds().isEmpty() || spec.userIds().contains(actor)) && (spec.deptIds().isEmpty() || spec.deptIds().contains(user.getDeptId()));
    }
    public boolean canView(Long id,Long actor){var row=mapper.selectById(id);return row!=null&&Boolean.TRUE.equals(row.getEnabled())&&row.getPublishedVersion()!=null&&visible(config(row,true).spec(),actor);}
    public List<Config> visible(Long actor) {
        var rows=mapper.selectList(new LambdaQueryWrapper<HomeMetricDO>().eq(HomeMetricDO::getEnabled,true).isNotNull(HomeMetricDO::getPublishedVersion).orderByAsc(HomeMetricDO::getId));
        if(rows.isEmpty())return List.of();
        var viewer=users.getUser(actor);if(viewer==null||!Integer.valueOf(0).equals(viewer.getStatus()))return List.of();
        var query=new LambdaQueryWrapper<HomeMetricVersionDO>();
        query.and(q->rows.forEach(row->q.or(n->n.eq(HomeMetricVersionDO::getMetricId,row.getId()).eq(HomeMetricVersionDO::getVersion,row.getPublishedVersion()))));
        Map<String,HomeMetricVersionDO> published=new HashMap<>();
        for(var v:versions.selectList(query))published.put(v.getMetricId()+":"+v.getVersion(),v);
        Map<String,Boolean> grants=new HashMap<>();List<Config> result=new ArrayList<>();
        for(var row:rows) {
            var v=published.get(row.getId()+":"+row.getPublishedVersion());if(v==null)throw exception(HOME_METRIC_NOT_FOUND);
            var spec=parse(v.getSpecJson());
            boolean grant=grants.computeIfAbsent(spec.viewPermission(),p->permissions.isPermissionDefined(p)&&permissions.hasAnyPermissions(actor,p));
            if(grant&&inAudience(spec,actor,viewer))result.add(new Config(row.getId(),row.getVersion(),true,row.getPublishedVersion(),publicSpec(spec)));
        }
        result.sort(Comparator.comparingInt(c->c.spec().sort()));
        // Legacy development data may exceed the publish cap; retain records and limit only the authorized projection.
        return List.copyOf(result.subList(0,Math.min(12,result.size())));
    }
    public Evaluation preview(HomeMetricSpec spec,Long actor) {validateRuntime(spec,actor);var now=Instant.now();return evaluateSpec(freeze(snapshots.capture(spec,null,actor),now),actor,now);}
    private Evaluation evaluateSpec(HomeMetricSpec spec,Long actor,Instant now) {
        var e=new MetricEvaluation(Clock.fixed(now,ZoneOffset.UTC),Duration.ofSeconds(20)).evaluate(spec.definition(),sources.bind(spec,actor));
        return project(e,spec,actor);
    }
    private Evaluation project(MetricEvaluation.Evaluation e,HomeMetricSpec spec,Long actor) {
        Map<String,String> labels=new HashMap<>();var catalog=sources.catalog(actor);
        for(var operand:spec.operands())labels.put(operand.calculation().name(),catalog.stream().filter(s->s.key().equals(operand.calculation().source())).map(HomeMetricSources.Source::label).findFirst().orElse("统计来源"));
        return new Evaluation(e.evaluatedAt(),value(e.result()),e.unit(),e.inputs().stream().map(i->new Input(i.name(),labels.get(i.name()),i.range(),value(i.result()))).toList());
    }
    private Value value(MetricFormula.Result r) {return new Value(r.status().name(),r.value()==null?null:r.value().toPlainString());}
    @ZsjosPermission(bizType="home-metric",bizId="#id",action="view")
    public Evaluated evaluate(Long id,Long actor) {
        var row=require(id);if(!Boolean.TRUE.equals(row.getEnabled()) || row.getPublishedVersion()==null) throw exception(HOME_METRIC_DISABLED);
        var c=config(row,true);if(!visible(c.spec(),actor)) throw exception(HOME_METRIC_DENIED);
        validateRuntime(c.spec(),actor);var now=Instant.now();var frozen=freeze(snapshots.capture(c.spec(),c.spec(),actor),now);var evaluation=evaluateSpec(frozen,actor,now);String token=UUID.randomUUID().toString();
        var current=require(id);if(!Boolean.TRUE.equals(current.getEnabled())||!Objects.equals(current.getPublishedVersion(),row.getPublishedVersion()))throw exception(HOME_METRIC_CONTEXT_EXPIRED);
        if(!visible(config(current,true).spec(),actor))throw exception(HOME_METRIC_DENIED);
        if(evaluation.inputs().isEmpty()) return new Evaluated(id,row.getPublishedVersion(),publicSpec(c.spec()),null,evaluation);
        var context=new Context(id,row.getPublishedVersion(),actor,TenantContextHolder.getRequiredTenantId(),frozen,evaluation,Instant.now().plusSeconds(900));
        redis.opsForValue().set("zsjos:home-metric:context:"+token,JsonUtils.toJsonString(context),Duration.ofMinutes(15));return new Evaluated(id,row.getPublishedVersion(),publicSpec(c.spec()),token,evaluation);
    }
    private void checkContext(Context c,Long actor) {
        if(!Objects.equals(c.actor(),actor)||!Objects.equals(c.tenant(),TenantContextHolder.getRequiredTenantId()))throw exception(HOME_METRIC_DENIED);
        var row=mapper.selectById(c.id());
        if(row==null||!Boolean.TRUE.equals(row.getEnabled())||!Objects.equals(row.getPublishedVersion(),c.version())||!c.expiresAt().isAfter(Instant.now()))throw exception(HOME_METRIC_CONTEXT_EXPIRED);
        if(!visible(c.spec(),actor))throw exception(HOME_METRIC_DENIED);
    }
    public Context context(String token,Long actor) {
        if(token==null || !token.matches("[a-f0-9-]{36}")) throw exception(HOME_METRIC_CONTEXT_EXPIRED);
        String raw=redis.opsForValue().get("zsjos:home-metric:context:"+token);if(raw==null) throw exception(HOME_METRIC_CONTEXT_EXPIRED);
        var c=JsonUtils.parseObject(raw,Context.class);checkContext(c,actor);
        validateRuntime(c.spec(),actor,true);snapshots.capture(c.spec(),c.spec(),actor);
        var bound=sources.bind(c.spec(),actor);for(var operand:c.spec().operands()) if(!bound.canRead(operand.calculation())) throw exception(HOME_METRIC_DENIED);
        Map<String,MetricPeriod.Range> ranges=new LinkedHashMap<>();
        for(var input:c.evaluation().inputs())ranges.put(input.name(),input.range());
        var fresh=new MetricEvaluation(Clock.systemUTC(),Duration.ofSeconds(20)).evaluateResolved(c.spec().definition(),bound,ranges);
        if(fresh.result().status()==MetricFormula.Status.FORBIDDEN)throw exception(HOME_METRIC_DENIED);
        checkContext(c,actor);
        return new Context(c.id(),c.version(),c.actor(),c.tenant(),c.spec(),project(fresh,c.spec(),actor),c.expiresAt());
    }
    public PageResult<Map<String,Object>> details(Details request,Long actor) {
        var c=context(request.contextId(),actor);if(request.pageNo()<1||request.pageSize()<1||request.pageSize()>100) throw exception(HOME_METRIC_INVALID);
        var operand=c.spec().operands().stream().filter(o->o.calculation().name().equals(request.operand())).findFirst().orElseThrow(()->exception(HOME_METRIC_OPERAND_NOT_FOUND));
        var input=c.evaluation().inputs().stream().filter(i->i.name().equals(request.operand())).findFirst().orElseThrow(()->switch(c.evaluation().result().status()){
            case "TIMEOUT" -> exception(HOME_METRIC_QUERY_TIMEOUT);
            case "FORBIDDEN" -> exception(HOME_METRIC_DENIED);
            case "INVALID_CONFIG" -> exception(HOME_METRIC_INVALID);
            case "SOURCE_ERROR" -> exception(HOME_METRIC_QUERY_FAILED);
            default -> exception(HOME_METRIC_CONTEXT_EXPIRED);
        });
        return sources.details(operand,input.range(),actor,request.pageNo(),request.pageSize());
    }
}
