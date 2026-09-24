package cn.iocoder.yudao.module.zsjos.service.cashback;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.cashback.CashbackDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.withdrawal.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.cashback.CashbackMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.withdrawal.*;
import cn.iocoder.yudao.module.zsjos.service.lead.LeadObjectPermissionService;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderObjectPermissionService;
import cn.iocoder.yudao.module.zsjos.service.withdrawal.WithdrawalObjectPermissionProvider;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

/** Management-only projections. Personal/H5 converters intentionally never call this service. */
@Service
public class FinanceTraceService {
    @Resource private CashbackMapper cashbacks;
    @Resource private PartnerMapper partners;
    @Resource private LeadMapper leads;
    @Resource private SalesOrderMapper orders;
    @Resource private SalesOrderItemMapper orderItems;
    @Resource private WithdrawalMapper withdrawals;
    @Resource private WithdrawalItemMapper withdrawalItems;
    @Resource private PermissionApi permissions;
    @Resource private AdminUserApi users;
    @Resource private LeadObjectPermissionService leadAccess;
    @Resource private SalesOrderObjectPermissionService orderAccess;
    @Resource private WithdrawalObjectPermissionProvider withdrawalAccess;

    @PreAuthorize("@ss.hasPermission('zsjos:cashback:finance-query')")
    public CashbackRespVO detail(Long id) {
        CashbackDO row = cashbacks.selectById(id);
        if (row == null) throw exception(CASHBACK_SOURCE_INVALID);
        CashbackRespVO result = BeanUtils.toBean(row, CashbackRespVO.class);
        enrichCashbacks(List.of(result));
        return result;
    }

    public PageResult<CashbackRespVO> enrichCashbackPage(PageResult<CashbackRespVO> page) {
        enrichCashbacks(page.getList());
        return page;
    }

    public void enrichCashbacks(List<CashbackRespVO> views) {
        if (views.isEmpty()) return;
        var rows = index(cashbacks.selectBatchIds(views.stream().map(CashbackRespVO::getId).toList()), CashbackDO::getId);
        var partnerIds = ids(rows.values(), CashbackDO::getPartnerId);
        var partnerMap = partnerIds.isEmpty() ? new HashMap<Long, PartnerDO>() : index(partners.selectBatchIds(partnerIds), PartnerDO::getId);
        var leadIds = ids(rows.values(), CashbackDO::getLeadId);
        var leadMap = leadIds.isEmpty() ? new HashMap<Long, LeadDO>() : index(leads.selectBatchIds(leadIds), LeadDO::getId);
        var orderIds = ids(rows.values(), CashbackDO::getOrderId);
        var orderMap = orderIds.isEmpty() ? new HashMap<Long, SalesOrderDO>() : index(orders.selectBatchIds(orderIds), SalesOrderDO::getId);
        var itemIds = ids(rows.values(), CashbackDO::getOrderItemId);
        var itemMap = itemIds.isEmpty() ? new HashMap<Long, SalesOrderItemDO>() : index(orderItems.selectBatchIds(itemIds), SalesOrderItemDO::getId);
        var userIds = ids(rows.values(), CashbackDO::getBeneficiaryUserId);
        userIds.addAll(ids(orderMap.values(), SalesOrderDO::getFormalSalesUserId));
        var userMap = userIds.isEmpty() ? new HashMap<Long, cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO>() : users.getUserMap(userIds);
        Long operator = getLoginUserId();
        // Page entitlement is required in addition to the owning domain's object permission.
        boolean leadPage = permissions.hasAnyPermissions(operator, "zsjos:lead:query");
        boolean orderPage = permissions.hasAnyPermissions(operator, "zsjos:sales-order:query", "zsjos:sales-order:query-management", "zsjos:sales-order:query-team", "zsjos:sales-order:query-own");
        for (CashbackRespVO view : views) {
            CashbackDO row = rows.get(view.getId());
            if (row == null) continue;
            PartnerDO partner = partnerMap.get(row.getPartnerId());
            var beneficiary = userMap.get(row.getBeneficiaryUserId());
            view.setPartnerName(partner == null ? null : partner.getName());
            view.setBeneficiaryName(row.getPartnerId() != null ? partner == null ? "历史归属信息缺失" : partner.getName()
                    : beneficiary == null ? "历史归属信息缺失" : beneficiary.getNickname());
            FinanceSourceRespVO source = new FinanceSourceRespVO();
            source.setLeadAccess("denied");
            source.setOrderAccess(row.getOrderId() == null ? "not_applicable" : "denied");
            LeadDO lead = leadMap.get(row.getLeadId());
            if (leadPage && lead != null && leadAccess.canReadDetail(lead, operator)) {
                source.setLeadAccess("available"); source.setLeadId(lead.getId()); source.setLeadNo(lead.getLeadNo());
                if (leadAccess.canViewUnmaskedIdentity(operator, lead)) source.setCustomerName(lead.getSubmittedName());
            } else if (leadPage && lead == null) source.setLeadAccess("unavailable");
            SalesOrderDO order = orderMap.get(row.getOrderId());
            if (orderPage && order != null && canReadOrder(order, operator)) {
                source.setOrderAccess("available"); source.setOrderId(order.getId()); source.setOrderNo(order.getOrderNo());
                source.setStudentName(order.getStudentName()); source.setOrderStatus(order.getStatus()); source.setOrderType(order.getOrderType());
                source.setOrderStatusLabel(switch (Objects.toString(order.getStatus(), "")) {
                    case "pending_approval" -> "审批中"; case "revision_required" -> "待修改";
                    case "effective" -> "已生效"; case "superseded" -> "已被接续"; case "terminated" -> "已终止";
                    default -> "状态暂不可用";
                });
                source.setOrderTypeLabel(switch (Objects.toString(order.getOrderType(), "")) {
                    case "first_purchase" -> "首购"; case "repurchase" -> "复购"; default -> "历史未记录";
                });
                source.setOrderTotalAmount(order.getTotalAmount()); source.setOrderPayableAmount(order.getPayableAmount());
                source.setCustomerPaidAt(order.getCustomerPaidAt());
                var sales = userMap.get(order.getFormalSalesUserId()); source.setSalesName(sales == null ? null : sales.getNickname());
                SalesOrderItemDO item = itemMap.get(row.getOrderItemId());
                if (item != null && Objects.equals(item.getOrderId(), order.getId())) {
                    var snapshot = cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObjectQuietly(item.getProductSnapshot(),
                            cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot.class);
                    if (snapshot != null) { source.setProductName(snapshot.name()); source.setSkuName(snapshot.skuName()); }
                    source.setQuantity(item.getQuantity());
                    source.setUnitPrice(item.getUnitPrice()); source.setDiscountAmount(item.getDiscountAmount()); source.setItemPayableAmount(item.getPayableAmount());
                }
            } else if (orderPage && row.getOrderId() != null && order == null) source.setOrderAccess("unavailable");
            view.setSource(source);
            // Legacy top-level references must not bypass the source visibility contract.
            view.setLeadId(source.getLeadId()); view.setLeadNo(source.getLeadNo()); view.setOrderId(source.getOrderId());
            if (!"available".equals(source.getOrderAccess())) view.setOrderItemId(null);
        }
    }

    private boolean canReadOrder(SalesOrderDO order, Long operator) {
        if (permissions.hasAnyPermissions(operator, "zsjos:sales-order:query-management") && orderAccess.canReadManagement(order, operator)) return true;
        if (permissions.hasAnyPermissions(operator, "zsjos:sales-order:query-own") && Objects.equals(order.getSubmitterUserId(), operator)) return true;
        return permissions.hasAnyPermissions(operator, "zsjos:sales-order:query", "zsjos:sales-order:query-team")
                && (permissions.hasTenantReadAllAccess(operator) || orderAccess.canRead(order, operator));
    }

    public boolean canQuerySource(String kind) {
        return kind.startsWith("lead") ? permissions.hasAnyPermissions(getLoginUserId(), "zsjos:lead:query")
                : permissions.hasAnyPermissions(getLoginUserId(), "zsjos:sales-order:query", "zsjos:sales-order:query-management", "zsjos:sales-order:query-team", "zsjos:sales-order:query-own");
    }

    /** Bound filters use the same object checks as projections, including negative/empty operators. */
    public Set<Long> visibleSourceIds(String kind) {
        if (!canQuerySource(kind)) throw exception(cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN);
        var references = cashbacks.selectList(new LambdaQueryWrapper<CashbackDO>().select(CashbackDO::getLeadId, CashbackDO::getOrderId));
        var result = new HashSet<Long>();
        Long operator = getLoginUserId();
        if (kind.startsWith("lead")) {
            var ids = ids(references, CashbackDO::getLeadId);
            if (!ids.isEmpty()) for (LeadDO lead : leads.selectBatchIds(ids)) {
                if (leadAccess.canReadDetail(lead, operator) && (!"lead_identity".equals(kind) || leadAccess.canViewUnmaskedIdentity(operator, lead))) result.add(lead.getId());
            }
        } else {
            var ids = ids(references, CashbackDO::getOrderId);
            if (!ids.isEmpty()) for (SalesOrderDO order : orders.selectBatchIds(ids)) {
                if (canReadOrder(order, operator)) result.add(order.getId());
            }
        }
        return result;
    }

    public Set<Long> matchIdentityIds(String scene, String operator, Object value) {
        Map<Long, Long> ownerIds = new HashMap<>(), partnerIds = new HashMap<>();
        if ("cashback".equals(scene)) {
            for (CashbackDO row : cashbacks.selectList(new LambdaQueryWrapper<CashbackDO>().select(CashbackDO::getId, CashbackDO::getBeneficiaryUserId, CashbackDO::getPartnerId))) {
                ownerIds.put(row.getId(), row.getBeneficiaryUserId()); partnerIds.put(row.getId(), row.getPartnerId());
            }
        } else {
            for (WithdrawalDO row : withdrawals.selectList(new LambdaQueryWrapper<WithdrawalDO>().select(WithdrawalDO::getId, WithdrawalDO::getApplicantUserId, WithdrawalDO::getPartnerId))) {
                ownerIds.put(row.getId(), row.getApplicantUserId());
                partnerIds.put(row.getId(), row.getApplicantUserId() == null ? row.getPartnerId() : null);
            }
        }
        var userIds = ownerIds.values().stream().filter(Objects::nonNull).collect(Collectors.toSet());
        var userMap = userIds.isEmpty() ? new HashMap<Long, cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO>() : users.getUserMap(userIds);
        var pids = partnerIds.values().stream().filter(Objects::nonNull).collect(Collectors.toSet());
        var partnerMap = pids.isEmpty() ? new HashMap<Long, PartnerDO>() : index(partners.selectBatchIds(pids), PartnerDO::getId);
        String expected = Objects.toString(value, "").toLowerCase(Locale.ROOT);
        return ownerIds.keySet().stream().filter(id -> {
            var partner = partnerMap.get(partnerIds.get(id)); var user = userMap.get(ownerIds.get(id));
            String name = partnerIds.get(id) != null ? partner == null ? null : partner.getName() : user == null ? null : user.getNickname();
            String actual = name == null ? "" : name.toLowerCase(Locale.ROOT);
            return switch (operator) {
                case "is_empty" -> actual.isBlank(); case "is_not_empty" -> !actual.isBlank();
                case "contains" -> actual.contains(expected); case "not_contains" -> !actual.isBlank() && !actual.contains(expected);
                case "eq" -> actual.equals(expected); case "ne" -> !actual.isBlank() && !actual.equals(expected);
                default -> false;
            };
        }).collect(Collectors.toSet());
    }

    public WithdrawalRespVO enrichWithdrawalIfManagement(WithdrawalRespVO view) {
        return permissions.hasAnyPermissions(getLoginUserId(), "zsjos:withdrawal:finance-query", "zsjos:withdrawal:admin-query")
                ? enrichWithdrawal(view) : view;
    }

    public PageResult<WithdrawalRespVO> enrichWithdrawalPage(PageResult<WithdrawalRespVO> page) {
        enrichWithdrawals(page.getList()); return page;
    }

    public WithdrawalRespVO enrichWithdrawal(WithdrawalRespVO view) {
        enrichWithdrawals(List.of(view)); return view;
    }

    private void enrichWithdrawals(List<WithdrawalRespVO> views) {
        if (views.isEmpty()) return;
        var rows = index(withdrawals.selectBatchIds(views.stream().map(WithdrawalRespVO::getId).toList()), WithdrawalDO::getId);
        var partnerIds = ids(rows.values(), WithdrawalDO::getPartnerId);
        var partnerMap = partnerIds.isEmpty() ? new HashMap<Long, PartnerDO>() : index(partners.selectBatchIds(partnerIds), PartnerDO::getId);
        var userIds = ids(rows.values(), WithdrawalDO::getApplicantUserId); userIds.addAll(ids(rows.values(), WithdrawalDO::getPaidByUserId));
        var userMap = userIds.isEmpty() ? new HashMap<Long, cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO>() : users.getUserMap(userIds);
        for (WithdrawalRespVO view : views) {
            WithdrawalDO row = rows.get(view.getId()); if (row == null) continue;
            var partner = partnerMap.get(row.getPartnerId()); var applicant = userMap.get(row.getApplicantUserId()); var payer = userMap.get(row.getPaidByUserId());
            view.setPartnerName(partner == null ? null : partner.getName());
            view.setApplicantName(applicant == null ? row.getApplicantUserId() == null && partner != null
                    ? partner.getName() + "（合作方申请）" : "历史申请人信息缺失" : applicant.getNickname());
            view.setPaidByName(payer == null ? null : payer.getNickname());
            view.setCashbackCount(view.getItems() == null ? 0 : view.getItems().size());
        }
    }

    @PreAuthorize("@ss.hasAnyPermissions('zsjos:withdrawal:finance-query','zsjos:withdrawal:admin-query')")
    public PageResult<WithdrawalSourceRespVO> sources(Long id, PageParam page) {
        withdrawalAccess.check(id, "read", getLoginUserId());
        var records = withdrawalItems.selectPage(page, new LambdaQueryWrapper<WithdrawalItemDO>().eq(WithdrawalItemDO::getWithdrawalId, id).orderByAsc(WithdrawalItemDO::getId));
        var cashbackIds = ids(records.getList(), WithdrawalItemDO::getCashbackId);
        var cashbackViews = cashbackIds.isEmpty() ? List.<CashbackRespVO>of() : BeanUtils.toBean(cashbacks.selectBatchIds(cashbackIds), CashbackRespVO.class);
        enrichCashbacks(cashbackViews);
        var cashbackMap = index(cashbackViews, CashbackRespVO::getId);
        return new PageResult<>(records.getList().stream().map(item -> new WithdrawalSourceRespVO().setId(item.getId())
                .setAmount(item.getAmountSnapshot()).setActive(item.getActiveFlag()).setCashback(cashbackMap.get(item.getCashbackId()))).toList(), records.getTotal());
    }

    @PreAuthorize("@ss.hasPermission('zsjos:cashback:finance-query') && @ss.hasAnyPermissions('zsjos:withdrawal:finance-query','zsjos:withdrawal:admin-query')")
    public PageResult<CashbackWithdrawalRespVO> history(Long id, PageParam page) {
        if (cashbacks.selectById(id) == null) throw exception(CASHBACK_SOURCE_INVALID);
        var records = withdrawalItems.selectPage(page, new LambdaQueryWrapper<WithdrawalItemDO>().eq(WithdrawalItemDO::getCashbackId, id).orderByDesc(WithdrawalItemDO::getId));
        var ids = ids(records.getList(), WithdrawalItemDO::getWithdrawalId);
        var map = ids.isEmpty() ? new HashMap<Long, WithdrawalDO>() : index(withdrawals.selectBatchIds(ids), WithdrawalDO::getId);
        var result = new ArrayList<CashbackWithdrawalRespVO>();
        for (WithdrawalItemDO item : records.getList()) {
            WithdrawalDO row = map.get(item.getWithdrawalId());
            if (row == null || !withdrawalAccess.hasPermission(row.getId(), "read", getLoginUserId())) continue;
            result.add(new CashbackWithdrawalRespVO().setId(row.getId()).setWithdrawalNo(row.getWithdrawalNo())
                    .setAmount(item.getAmountSnapshot()).setActive(item.getActiveFlag()).setStatus(row.getStatus()).setSubmittedAt(row.getSubmittedAt()));
        }
        return new PageResult<>(result, records.getTotal());
    }

    private static <T> Set<Long> ids(Collection<T> rows, Function<T, Long> id) { return rows.stream().map(id).filter(Objects::nonNull).collect(Collectors.toSet()); }
    private static <T> Map<Long, T> index(Collection<T> rows, Function<T, Long> id) { return rows.stream().collect(Collectors.toMap(id, Function.identity(), (a, b) -> a)); }
}
