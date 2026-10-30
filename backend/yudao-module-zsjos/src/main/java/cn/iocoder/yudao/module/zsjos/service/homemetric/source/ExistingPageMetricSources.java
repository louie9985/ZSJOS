package cn.iocoder.yudao.module.zsjos.service.homemetric.source;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.appeal.LeadAppealPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.duplicate.LeadDuplicateReviewPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate.SubordinateSalesPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.educationteam.vo.EducationTeamPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.*;
import cn.iocoder.yudao.module.zsjos.service.lead.*;
import cn.iocoder.yudao.module.zsjos.service.registration.*;
import cn.iocoder.yudao.module.zsjos.service.educationteam.EducationTeamService;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import cn.iocoder.yudao.module.zsjos.service.homemetric.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

/** Uses each owning service's authorized total; never walks all pages to calculate an aggregate. */
@Service
public class ExistingPageMetricSources {
    @Resource private PermissionApi permissions;
    @Resource private AdvancedFilterService filters;
    @Resource private LeadAppealService appeals;
    @Resource private LeadDuplicateReviewService duplicates;
    @Resource private RegistrationService registrations;
    @Resource private MediaStudentService media;
    @Resource private SubordinateSalesService sales;
    @Resource private EducationTeamService education;

    private record Entry(String key,String label,String permission) {}
    private static final List<Entry> ENTRIES = List.of(
            new Entry("lead_appeal","个人待处理申诉","zsjos:lead:appeal:query"),
            new Entry("duplicate_review","客资查重审核记录","zsjos:lead-duplicate-review:query"),
            new Entry("registration","报名履约记录","zsjos:registration:query-pool"),
            new Entry("media_student","媒体学员人数","zsjos:media-student:query-my"),
            new Entry("subordinate_sales","下属销售人数","zsjos:subordinate-sales:query"),
            new Entry("education_team","教务团队人数","zsjos:education-team:query"));

    public boolean supports(String key) { return ENTRIES.stream().anyMatch(e -> e.key().equals(key)); }
    public boolean canRead(String key,Long actor) {
        return ENTRIES.stream().filter(e -> e.key().equals(key)).anyMatch(e -> permissions.hasAnyPermissions(actor,e.permission()));
    }
    public List<HomeMetricSources.Source> catalog(Long actor) {
        return ENTRIES.stream().filter(e -> canRead(e.key(),actor))
                .map(e -> new HomeMetricSources.Source(e.key(),e.label(),e.key(),List.of(),timeFields(e.key()),"/zsjos/tasks/today")).toList();
    }
    private List<HomeMetricSources.Field> timeFields(String source) {
        return filters.catalog(source).fields().stream().filter(f -> "date".equals(f.valueType()))
                .map(f -> new HomeMetricSources.Field(f.fieldKey(),f.label(),"TIME")).toList();
    }
    public MetricUnit validate(HomeMetricSpec.Operand item) {
        if (item==null || item.calculation()==null) throw new IllegalArgumentException("指标操作数缺失");
        var op=item.calculation();
        if (!supports(op.source()) || op.aggregate()==null || op.aggregate().requiresField()
                || op.field()!=null && !op.field().isBlank())
            throw new IllegalArgumentException("该业务视图支持按高级筛选计数，不支持独立数值字段聚合");
        filters.validate(op.source(),item.filter());
        if(op.period()!=MetricPeriod.Kind.ALL) {
            if(timeFields(op.source()).stream().noneMatch(f -> f.key().equals(item.timeField())))
                throw new IllegalArgumentException("请选择来源支持的时间字段");
            // Validate the composed shape at save time, including limits on nested OR groups.
            bounded(item,new MetricPeriod.Range(java.time.LocalDateTime.of(2000,1,1,0,0),java.time.LocalDateTime.of(2000,1,2,0,0)));
        }
        return MetricUnit.of("COUNT");
    }
    public MetricFormula.Result aggregate(HomeMetricSpec.Operand item,MetricPeriod.Range range,Long actor) {
        validate(item);
        if (!canRead(item.calculation().source(),actor)) return MetricFormula.Result.failure(MetricFormula.Status.FORBIDDEN);
        var page=page(bounded(item,range),actor,1,1);
        return MetricFormula.Result.ok(BigDecimal.valueOf(page.getTotal()));
    }
    public PageResult<Map<String,Object>> details(HomeMetricSpec.Operand item,MetricPeriod.Range range,Long actor,int pageNo,int pageSize) {
        validate(item);
        if (pageNo<1 || pageSize<1 || pageSize>100) throw new IllegalArgumentException("指标明细参数无效");
        if (!canRead(item.calculation().source(),actor)) throw new AccessDeniedException("无权读取指标来源");
        return page(bounded(item,range),actor,pageNo,pageSize);
    }
    private HomeMetricSpec.Operand bounded(HomeMetricSpec.Operand item,MetricPeriod.Range range) {
        if(range==null)return item;
        var combined=new cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO();
        combined.setLogic("AND");
        var conditions=new ArrayList<cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterConditionReqVO>();
        if("AND".equals(item.filter().getLogic())) {
            if(item.filter().getConditions()!=null)conditions.addAll(item.filter().getConditions());
            combined.setGroups(item.filter().getGroups());
        } else combined.setGroups(List.of(item.filter()));
        var from=new cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterConditionReqVO();
        from.setFieldKey(item.timeField());from.setOperator("gte");from.setValue(range.start().toString());
        var to=new cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterConditionReqVO();
        to.setFieldKey(item.timeField());to.setOperator("lt");to.setValue(range.end().toString());
        conditions.add(from);conditions.add(to);combined.setConditions(conditions);
        filters.validate(item.calculation().source(),combined);
        return new HomeMetricSpec.Operand(item.calculation(),combined,item.timeField());
    }
    private PageResult<Map<String,Object>> page(HomeMetricSpec.Operand item,Long actor,int pageNo,int pageSize) {
        return switch(item.calculation().source()) {
            case "lead_appeal" -> {
                var req=paging(new LeadAppealPageReqVO(),pageNo,pageSize);req.setHandled(false);req.setAdvancedFilter(item.filter());
                yield map(appeals.getMetricInboxPage(req,actor),r -> row(r.getId(),r.getLeadNo(),null));
            }
            case "duplicate_review" -> {
                var req=paging(new LeadDuplicateReviewPageReqVO(),pageNo,pageSize);req.setAdvancedFilter(item.filter());
                yield map(duplicates.getPage(req),r -> row(r.getId(),null,"查重审核记录"
                        +(r.getCreateTime()==null?"":" · "+r.getCreateTime().toString().replace('T',' '))));
            }
            case "registration" -> {
                var req=paging(new RegistrationPoolPageReqVO(),pageNo,pageSize);req.setStatusGroup("all");req.setAdvancedFilter(item.filter());
                yield map(registrations.getPoolPage(req),r -> row(r.getId(),r.getOrderNo(),r.getStatusLabel()));
            }
            case "media_student" -> {
                var req=paging(new MyStudentPageReqVO(),pageNo,pageSize);req.setAdvancedFilter(item.filter());
                yield map(media.getPage(actor,req),r -> row(r.getPersonId(),r.getPersonNo(),null));
            }
            case "subordinate_sales" -> {
                var req=paging(new SubordinateSalesPageReqVO(),pageNo,pageSize);req.setAdvancedFilter(item.filter());
                yield map(sales.getPage(req,actor),r -> row(r.getUserId(),null,r.getName()));
            }
            case "education_team" -> {
                var req=paging(new EducationTeamPageReqVO(),pageNo,pageSize);req.setAdvancedFilter(item.filter());
                yield map(education.getPage(req,actor),r -> row(r.getUserId(),null,r.getName()));
            }
            default -> throw new IllegalArgumentException("未知指标来源");
        };
    }
    private <T extends PageParam> T paging(T req,int pageNo,int pageSize) { req.setPageNo(pageNo);req.setPageSize(pageSize);return req; }
    private <T> PageResult<Map<String,Object>> map(PageResult<T> page,Function<T,Map<String,Object>> projection) {
        return new PageResult<>(page.getList().stream().map(projection).toList(),page.getTotal());
    }
    private Map<String,Object> row(Long id,String businessNo,String label) {
        Map<String,Object> row=new LinkedHashMap<>();row.put("id",id);row.put("businessNo",businessNo);row.put("name",label);return row;
    }
}
