package cn.iocoder.yudao.module.zsjos.service.cashback;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.CashbackPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.cashback.CashbackDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.cashback.CashbackSearchMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.*;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadObjectPermissionService;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderObjectPermissionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Service
@Slf4j
public class CashbackSearchService {
    static final int BATCH_SIZE = 256;
    @Resource private CashbackSearchMapper mapper;
    @Resource private AdvancedFilterService filters;
    @Resource private FinanceTraceService trace;
    @Resource private LeadMapper leads;
    @Resource private PartnerMapper partners;
    @Resource private SalesOrderMapper orders;
    @Resource private AdminUserApi users;
    @Resource private LeadObjectPermissionService leadAccess;
    @Resource private SalesOrderObjectPermissionService orderAccess;

    public PageResult<CashbackDO> search(CashbackPageReqVO request, Long beneficiary) {
        long start = System.nanoTime();
        var query = new CashbackSearchQuery(request, beneficiary, TenantContextHolder.getRequiredTenantId());
        var resolver = new Resolver(query.build(), getLoginUserId());
        query.advanced(filters.buildCashbackQuery(request.getAdvancedFilter(), resolver));
        long filtered = System.nanoTime();
        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            String keyword = request.getKeyword().trim();
            Set<Long> matches = resolver.names(query.build(), keyword);
            String number = query.bind("%" + keyword + "%");
            query.and("c.cashback_no LIKE " + number
                    + " OR EXISTS (SELECT 1 FROM zsjos_lead fl WHERE fl.id=c.lead_id AND fl.tenant_id=c.tenant_id "
                    + "AND fl.deleted=0 AND fl.lead_no LIKE " + number + ") OR " + query.ids("c.id", matches));
        }
        long matched = System.nanoTime();
        var compiled = query.build();
        long total = mapper.count(compiled), counted = System.nanoTime();
        var rows = total == 0 ? List.<CashbackDO>of() : mapper.page(compiled,
                (long) (request.getPageNo() - 1) * request.getPageSize(), request.getPageSize());
        long ended = System.nanoTime();
        log.debug("Cashback search filtersMs={} matchMs={} countMs={} pageMs={} candidates={} total={}",
                millis(filtered-start), millis(matched-filtered), millis(counted-matched), millis(ended-counted), resolver.candidateCount, total);
        return new PageResult<>(rows, total);
    }

    private static long millis(long nanos) { return nanos / 1_000_000; }

    private final class Resolver implements AdvancedFilterService.FinanceResolver {
        private final AdvancedFilterQuery base;
        private final Long operator;
        private final Map<String, Set<Long>> visible = new HashMap<>();
        private int candidateCount;
        Resolver(AdvancedFilterQuery base, Long operator) { this.base = base; this.operator = operator; }

        private void scan(AdvancedFilterQuery query, Consumer<List<CashbackDO>> consumer) {
            long after = 0;
            while (true) {
                var batch = mapper.candidates(query, after, BATCH_SIZE);
                if (batch.isEmpty()) return;
                candidateCount += batch.size(); consumer.accept(batch);
                after = batch.getLast().getId();
                if (batch.size() < BATCH_SIZE) return;
            }
        }

        Set<Long> names(AdvancedFilterQuery source, String keyword) {
            String expected = keyword.toLowerCase(Locale.ROOT);
            // System owns user lookup. Recheck in Java to retain literal, case-insensitive substring semantics.
            boolean simpleFold = keyword.codePoints().allMatch(cp -> cp < 128 || Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
            // SQL/Java Unicode case folding can differ (e.g. dotted I and Greek sigma). For these inputs,
            // use a broader candidate set and retain Java as the final name-matching authority.
            var userIds = users.getUserListByNickname(simpleFold ? keyword.replace("\\", "\\\\") : "").stream()
                    .filter(user -> contains(user.getNickname(), expected)).map(AdminUserRespDTO::getId).toList();
            boolean leadPage = trace.canQuerySource("lead_identity"), orderPage = trace.canQuerySource("order");
            var query = new CashbackSearchQuery(source);
            String pattern = query.bind("%" + expected.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
            String nameMatch = simpleFold ? " LIKE " + pattern + " ESCAPE '!'" : " IS NOT NULL";
            var branches = new ArrayList<String>();
            branches.add("EXISTS (SELECT 1 FROM zsjos_partner fp WHERE fp.id=c.partner_id AND fp.tenant_id=c.tenant_id AND fp.deleted=0 AND LOWER(fp.name)" + nameMatch + ")");
            branches.add("(c.partner_id IS NULL AND " + query.ids("c.beneficiary_user_id", userIds) + ")");
            if (leadPage) branches.add("EXISTS (SELECT 1 FROM zsjos_lead fl WHERE fl.id=c.lead_id AND fl.tenant_id=c.tenant_id AND fl.deleted=0 AND LOWER(fl.submitted_name)" + nameMatch + ")");
            if (orderPage) branches.add("EXISTS (SELECT 1 FROM zsjos_order fo WHERE fo.id=c.order_id AND fo.tenant_id=c.tenant_id AND fo.deleted=0 AND LOWER(fo.student_name)" + nameMatch + ")");
            query.and(String.join(" OR ", branches));
            Set<Long> result = new HashSet<>(), matchedUsers = new HashSet<>(userIds);
            scan(query.build(), batch -> {
                var partnerMap = load(batch, CashbackDO::getPartnerId, partners::selectBatchIds, PartnerDO::getId);
                var leadMap = leadPage ? load(batch, CashbackDO::getLeadId, leads::selectBatchIds, LeadDO::getId) : new HashMap<Long, LeadDO>();
                var orderMap = orderPage ? load(batch, CashbackDO::getOrderId, orders::selectBatchIds, SalesOrderDO::getId) : new HashMap<Long, SalesOrderDO>();
                var allowedLeads = new HashSet<>(leadAccess.filterUnmaskedIdentity(leadMap.values().stream()
                        .filter(lead -> contains(lead.getSubmittedName(), expected)).toList(), operator));
                var allowedOrders = new HashSet<>(orderAccess.filterFinanceReadable(orderMap.values().stream()
                        .filter(order -> contains(order.getStudentName(), expected)).toList(), operator));
                for (var row : batch) {
                    var partner = partnerMap.get(row.getPartnerId());
                    if (row.getPartnerId() != null && partner != null && contains(partner.getName(), expected)
                            || row.getPartnerId() == null && matchedUsers.contains(row.getBeneficiaryUserId())
                            || allowedLeads.contains(row.getLeadId()) || allowedOrders.contains(row.getOrderId())) result.add(row.getId());
                }
            });
            return result;
        }

        @Override public Set<Long> identityIds(String op, Object value) {
            String expected = Objects.toString(value, "").toLowerCase(Locale.ROOT);
            Set<Long> result = new HashSet<>();
            scan(base, batch -> {
                var partnerMap = load(batch, CashbackDO::getPartnerId, partners::selectBatchIds, PartnerDO::getId);
                var userMap = load(batch, CashbackDO::getBeneficiaryUserId, users::getUserList, AdminUserRespDTO::getId);
                for (var row : batch) {
                    var partner = partnerMap.get(row.getPartnerId()); var user = userMap.get(row.getBeneficiaryUserId());
                    String name = row.getPartnerId() != null ? partner == null ? null : partner.getName() : user == null ? null : user.getNickname();
                    String actual = name == null ? "" : name.toLowerCase(Locale.ROOT);
                    boolean match = switch (op) {
                        case "is_empty" -> actual.isBlank(); case "is_not_empty" -> !actual.isBlank();
                        case "contains" -> actual.contains(expected); case "not_contains" -> !actual.isBlank() && !actual.contains(expected);
                        case "eq" -> actual.equals(expected); case "ne" -> !actual.isBlank() && !actual.equals(expected);
                        default -> false;
                    };
                    if (match) result.add(row.getId());
                }
            });
            return result;
        }

        @Override public Set<Long> visibleSourceIds(String kind) {
            if (!trace.canQuerySource(kind)) throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN);
            return visible.computeIfAbsent(kind, ignored -> {
                Set<Long> result = new HashSet<>(), seen = new HashSet<>();
                scan(base, batch -> {
                    if (kind.startsWith("lead")) {
                        var ids = batch.stream().map(CashbackDO::getLeadId).filter(Objects::nonNull).filter(seen::add).toList();
                        if (!ids.isEmpty()) {
                            var candidates = leads.selectBatchIds(ids);
                            result.addAll("lead_identity".equals(kind) ? leadAccess.filterUnmaskedIdentity(candidates, operator)
                                    : leadAccess.filterReadableDetails(candidates, operator));
                        }
                    } else {
                        var ids = batch.stream().map(CashbackDO::getOrderId).filter(Objects::nonNull).filter(seen::add).toList();
                        if (!ids.isEmpty()) result.addAll(orderAccess.filterFinanceReadable(orders.selectBatchIds(ids), operator));
                    }
                });
                return result;
            });
        }
    }

    private static boolean contains(String value, String expected) { return value != null && value.toLowerCase(Locale.ROOT).contains(expected); }
    private static <T> Map<Long, T> load(List<CashbackDO> rows, Function<CashbackDO, Long> reference,
                                       Function<Collection<Long>, List<T>> loader, Function<T, Long> id) {
        var ids = rows.stream().map(reference).filter(Objects::nonNull).collect(Collectors.toSet());
        return ids.isEmpty() ? new HashMap<>() : loader.apply(ids).stream().collect(Collectors.toMap(id, Function.identity(), (a,b) -> a));
    }
}
