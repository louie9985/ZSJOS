package cn.iocoder.yudao.module.zsjos.service.homemetric;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.appeal.LeadAppealPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.duplicate.LeadDuplicateReviewPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate.SubordinateSalesPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.educationteam.vo.EducationTeamPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.CashbackPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo.WithdrawalPageReqVO;
import cn.iocoder.yudao.module.zsjos.service.lead.*;
import cn.iocoder.yudao.module.zsjos.service.registration.*;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderService;
import cn.iocoder.yudao.module.zsjos.service.educationteam.EducationTeamService;
import cn.iocoder.yudao.module.zsjos.service.cashback.CashbackService;
import cn.iocoder.yudao.module.zsjos.service.withdrawal.WithdrawalService;
import jakarta.annotation.Resource;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

/** The context owns the population; each owning business service owns its full page projection. */
@Service
public class HomeMetricPageService {
    @Resource private HomeMetricConfigService contexts;
    @Resource private PermissionApi permissions;
    @Resource private Validator validator;
    @Resource private LeadManagementService leads;
    @Resource private LeadAppealService appeals;
    @Resource private LeadDuplicateReviewService duplicates;
    @Resource private SubordinateSalesService sales;
    @Resource private EducationTeamService education;
    @Resource private RegistrationService registrations;
    @Resource private StudentServiceReadService students;
    @Resource private MediaStudentService media;
    @Resource private SalesOrderService orders;
    @Resource private CashbackService cashbacks;
    @Resource private WithdrawalService withdrawals;
    @Resource private cn.iocoder.yudao.module.zsjos.service.homemetric.source.MetricSourceDeadline deadline;

    public record Request(String contextId, String operand, String source, boolean cursor, Map<String,Object> query) {}

    public Object page(Request request, Long actor) {
        var context = contexts.context(request.contextId(), actor);
        var operand = context.spec().operands().stream().filter(o -> o.calculation().name().equals(request.operand()))
                .findFirst().orElseThrow(() -> exception(HOME_METRIC_OPERAND_NOT_FOUND));
        String source = operand.calculation().source();
        if (!source.equals(request.source())) throw exception(HOME_METRIC_DENIED);
        var input = context.evaluation().inputs().stream().filter(i -> i.name().equals(request.operand()))
                .findFirst().orElseThrow(() -> exception(HOME_METRIC_CONTEXT_EXPIRED));
        if (!"OK".equals(input.result().status()) && !"EMPTY".equals(input.result().status()))
            throw exception(HOME_METRIC_QUERY_FAILED);
        Map<String,Object> query = new LinkedHashMap<>(request.query() == null ? Map.of() : request.query());
        query.put("advancedFilter", bounded(operand, input.range(), query.get("advancedFilter")));
        int size = ((Number) query.getOrDefault(request.cursor() ? "limit" : "pageSize", 20)).intValue();
        if (size < 1 || size > 100) throw exception(HOME_METRIC_INVALID);
        Object result = deadline.execute(java.time.Duration.ofSeconds(5), () -> switch (source) {
            case "lead" -> leads.getLeadPage(convert(query, LeadManagementPageReqVO.class), actor);
            case "order" -> request.cursor()
                    ? orders.getManagementCursorPage(convert(query, SalesOrderMyCursorReqVO.class), actor)
                    : orders.getManagementPage(convert(query, SalesOrderMyPageReqVO.class), actor);
            case "student" -> students.page(actor, convert(query, StudentServicePageReqVO.class));
            case "lead_appeal" -> {
                query.put("handled", false);
                var req = convert(query, LeadAppealPageReqVO.class);
                yield request.cursor() ? appeals.getInboxCursor(req, actor) : appeals.getMetricInboxPage(req, actor);
            }
            case "duplicate_review" -> duplicates.getPage(convert(query, LeadDuplicateReviewPageReqVO.class));
            case "registration" -> registrations.getPoolPage(convert(query, RegistrationPoolPageReqVO.class));
            case "media_student" -> media.getPage(actor, convert(query, MyStudentPageReqVO.class));
            case "subordinate_sales" -> sales.getPage(convert(query, SubordinateSalesPageReqVO.class), actor);
            case "education_team" -> education.getPage(convert(query, EducationTeamPageReqVO.class), actor);
            case "cashback" -> permissions.hasAnyPermissions(actor, "zsjos:cashback:finance-query")
                    ? cashbacks.getFinancePage(convert(query, CashbackPageReqVO.class))
                    : cashbacks.getPage(convert(query, CashbackPageReqVO.class), actor);
            case "withdrawal" -> permissions.hasAnyPermissions(actor, "zsjos:withdrawal:finance-query", "zsjos:withdrawal:admin-query")
                    ? withdrawals.getManagementPage(convert(query, WithdrawalPageReqVO.class))
                    : withdrawals.getPage(convert(query, WithdrawalPageReqVO.class), actor);
            default -> throw exception(HOME_METRIC_INVALID);
        });
        // Do not return rows after a concurrent revoke, disable or publication change.
        contexts.context(request.contextId(), actor);
        return result;
    }

    private <T> T convert(Map<String,Object> query, Class<T> type) {
        var fields = new LinkedHashMap<>(query);
        Object filter = fields.remove("advancedFilter");
        T value = JsonUtils.parseObject(JsonUtils.toJsonString(fields), type);
        new org.springframework.beans.BeanWrapperImpl(value).setPropertyValue("advancedFilter", filter);
        if (!validator.validate(value).isEmpty()) throw exception(HOME_METRIC_INVALID);
        return value;
    }

    static AdvancedFilterGroupReqVO bounded(HomeMetricSpec.Operand operand, MetricPeriod.Range range, Object extra) {
        var groups = new ArrayList<AdvancedFilterGroupReqVO>();
        if (operand.filter() != null) groups.add(operand.filter());
        if (extra != null) groups.add(JsonUtils.parseObject(JsonUtils.toJsonString(extra), AdvancedFilterGroupReqVO.class));
        if (range != null) {
            String field = switch (operand.calculation().source()) {
                case "lead" -> "lead." + operand.timeField();
                case "order" -> "order." + operand.timeField();
                case "student" -> "service." + operand.timeField();
                case "cashback" -> "cashback." + operand.timeField();
                case "withdrawal" -> "withdrawal." + operand.timeField();
                default -> operand.timeField();
            };
            var time = new AdvancedFilterGroupReqVO();
            time.setConditions(List.of(condition(field, "gte", range.start().toString()), condition(field, "lt", range.end().toString())));
            groups.add(time);
        }
        return new cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterIntersection(groups);
    }
    private static AdvancedFilterConditionReqVO condition(String field, String operator, String value) {
        var condition = new AdvancedFilterConditionReqVO();
        condition.setFieldKey(field); condition.setOperator(operator); condition.setValue(value);
        return condition;
    }
}
