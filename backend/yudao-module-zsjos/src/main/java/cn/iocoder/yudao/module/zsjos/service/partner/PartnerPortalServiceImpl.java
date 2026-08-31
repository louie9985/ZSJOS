package cn.iocoder.yudao.module.zsjos.service.partner;

import cn.hutool.core.util.DesensitizedUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerHomeStatisticsDetailPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerHomeStatisticsDetailRespVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerHomeStatisticsRespVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerLeadActivityRespVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerLeadFilterOptionsRespVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerLeaderboardConfigRespVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerLeaderboardPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerLeaderboardRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.cashback.CashbackDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadComplaintDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadFollowUpImageDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadFollowUpRecordDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadIntendedProductDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.withdrawal.WithdrawalDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.cashback.CashbackMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadComplaintMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadFollowUpImageMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadFollowUpRecordMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadIntendedProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerLeaderboardMetricRow;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.withdrawal.WithdrawalMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.CashbackConstants.STATUS_AVAILABLE;
import static cn.iocoder.yudao.module.zsjos.enums.CashbackConstants.STATUS_CANCELLED;
import static cn.iocoder.yudao.module.zsjos.enums.CashbackConstants.STATUS_PENDING;
import static cn.iocoder.yudao.module.zsjos.enums.CashbackConstants.STATUS_WITHDRAWING;
import static cn.iocoder.yudao.module.zsjos.enums.CashbackConstants.STATUS_WITHDRAWN;
import static cn.iocoder.yudao.module.zsjos.enums.CashbackConstants.TYPE_DEAL;
import static cn.iocoder.yudao.module.zsjos.enums.CashbackConstants.TYPE_VALID;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.APPEAL_STATUS_CHAIRMAN_REVIEWING;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.APPEAL_STATUS_OVERTURNED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.APPEAL_STATUS_QUALITY_REVIEWING;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.APPEAL_STATUS_SALES_MANAGER_REVIEWING;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.APPEAL_STATUS_UPHELD;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.ASSIGNMENT_OWNED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.ASSIGNMENT_PENDING;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.ASSIGNMENT_PUBLIC_POOL;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.ASSIGNMENT_RECYCLE_PENDING;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.ASSIGNMENT_UNASSIGNED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.SOURCE_INTERNAL_NEW_MEDIA;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.SOURCE_PARTNER;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.SOURCE_SALES_SELF;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.STATUS_CLOSED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.STATUS_CONVERTED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.STATUS_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.STATUS_SUBMITTED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.STATUS_SUSPENDED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.STATUS_VALID;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.STATUS_WON;
import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_STATUS_ENABLED;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.ORDER_TYPE_FIRST_PURCHASE;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.ORDER_TYPE_REPURCHASE;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.STATUS_EFFECTIVE;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.STATUS_PENDING_APPROVAL;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.STATUS_REVISION_REQUIRED;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.STATUS_SUPERSEDED;
import static cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.STATUS_TERMINATED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_NOT_EXISTS;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PARTNER_ACCOUNT_DISABLED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PARTNER_NOT_EXISTS;

@Service
public class PartnerPortalServiceImpl implements PartnerPortalService {

    private static final ZoneId BIZ_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> VALID_LEAD_STATUSES = Set.of(STATUS_VALID, STATUS_CONVERTED, STATUS_WON);
    private static final List<TypeDef> LEADERBOARD_TYPES = List.of(
            new TypeDef("estimated_income", "预计收益", "预计收益", "money", "统计周期内已生成且未取消的返现金额"),
            new TypeDef("withdrawn_amount", "已提现金额", "已提现", "money", "统计周期内已打款提现金额"),
            new TypeDef("lead_count", "提交客资", "客资数", "count", "统计周期内提交的客资数量"),
            new TypeDef("valid_lead_count", "有效客资", "有效数", "count", "统计周期内被判定有效、转化或成交的客资数量"));
    private static final Map<String, TypeDef> LEADERBOARD_TYPE_MAP = LEADERBOARD_TYPES.stream()
            .collect(Collectors.toMap(TypeDef::key, Function.identity(), (a, b) -> a, LinkedHashMap::new));

    @Resource
    private LeadMapper leadMapper;
    @Resource
    private LeadIntendedProductMapper intendedProductMapper;
    @Resource
    private LeadFollowUpRecordMapper followUpRecordMapper;
    @Resource
    private LeadFollowUpImageMapper followUpImageMapper;
    @Resource
    private LeadComplaintMapper complaintMapper;
    @Resource
    private CashbackMapper cashbackMapper;
    @Resource
    private WithdrawalMapper withdrawalMapper;
    @Resource
    private SalesOrderMapper salesOrderMapper;
    @Resource
    private PartnerMapper partnerMapper;

    @Override
    public PartnerHomeStatisticsRespVO getHomeStatistics(Long partnerId, String period) {
        requireEnabledPartner(partnerId);
        DateRange range = homeRange(period);
        PartnerHomeStatisticsRespVO response = new PartnerHomeStatisticsRespVO();
        response.setPeriod(range.period());
        response.setLeadCount(nullToZero(leadMapper.countPartnerLeads(partnerId, range.from(), range.to())));
        response.setValidLeadCount(nullToZero(leadMapper.countPartnerLeadsByStatuses(partnerId,
                VALID_LEAD_STATUSES, range.from(), range.to())));
        response.setConvertedLeadCount(nullToZero(leadMapper.countPartnerLeadsByStatuses(partnerId,
                Set.of(STATUS_WON), range.from(), range.to())));
        response.setWithdrawnAmount(zeroIfNull(withdrawalMapper.sumPartnerPaidAmount(
                TenantContextHolder.getRequiredTenantId(), partnerId, range.from(), range.to())));
        return response;
    }

    @Override
    public PartnerHomeStatisticsDetailRespVO getHomeStatisticsDetails(Long partnerId,
                                                                      PartnerHomeStatisticsDetailPageReqVO request) {
        requireEnabledPartner(partnerId);
        DateRange range = homeRange(request.getPeriod());
        String metric = normalizeMetric(request.getMetric());
        PartnerHomeStatisticsDetailRespVO response = new PartnerHomeStatisticsDetailRespVO();
        response.setPeriod(range.period());
        response.setMetric(metric);
        if ("withdrawn_amount".equals(metric)) {
            PageResult<WithdrawalDO> page = withdrawalMapper.selectPartnerPaidPage(request, partnerId,
                    range.from(), range.to());
            response.setTotal(page.getTotal());
            response.setTotalAmount(zeroIfNull(withdrawalMapper.sumPartnerPaidAmount(
                    TenantContextHolder.getRequiredTenantId(), partnerId, range.from(), range.to())));
            response.setList(page.getList().stream().map(this::toWithdrawalItem).map(Object.class::cast).toList());
            return response;
        }
        Collection<String> statuses = switch (metric) {
            case "valid_lead_count" -> VALID_LEAD_STATUSES;
            case "converted_lead_count" -> Set.of(STATUS_WON);
            default -> List.of();
        };
        PageResult<LeadDO> page = leadMapper.selectPartnerMetricPage(request, partnerId, statuses,
                range.from(), range.to());
        Map<Long, LeadIntendedProductDO> primaryProducts = primaryProducts(page.getList());
        response.setTotal(page.getTotal());
        response.setList(page.getList().stream()
                .map(lead -> toLeadItem(lead, primaryProducts.get(lead.getId())))
                .map(Object.class::cast).toList());
        return response;
    }

    @Override
    public PartnerLeaderboardConfigRespVO getLeaderboardConfig() {
        PartnerLeaderboardConfigRespVO response = new PartnerLeaderboardConfigRespVO();
        response.setEnabled(true);
        response.setEnabledTypes(LEADERBOARD_TYPES.stream().map(TypeDef::key).toList());
        response.setDefaultType("estimated_income");
        response.setDefaultPeriod("month");
        response.setPageSize(20);
        response.setMaskName(true);
        response.setTypeOptions(LEADERBOARD_TYPES.stream().map(type -> {
            PartnerLeaderboardConfigRespVO.TypeOption option = new PartnerLeaderboardConfigRespVO.TypeOption();
            option.setKey(type.key());
            option.setLabel(type.label());
            option.setValueLabel(type.valueLabel());
            option.setValueUnit(type.valueUnit());
            option.setRuleText(type.ruleText());
            return option;
        }).toList());
        return response;
    }

    @Override
    public PartnerLeaderboardRespVO getLeaderboard(Long partnerId, PartnerLeaderboardPageReqVO request) {
        requireEnabledPartner(partnerId);
        DateRange range = leaderboardRange(request.getPeriod());
        TypeDef type = LEADERBOARD_TYPE_MAP.getOrDefault(request.getType(), LEADERBOARD_TYPE_MAP.get("lead_count"));
        List<RankRow> rows = rankingRows(type.key(), range, partnerId);
        int pageNo = Math.max(1, request.getPageNo());
        int pageSize = Math.max(1, request.getPageSize());
        int fromIndex = Math.min((pageNo - 1) * pageSize, rows.size());
        int toIndex = Math.min(fromIndex + pageSize, rows.size());
        RankRow mine = rows.stream().filter(row -> Objects.equals(row.partnerId(), partnerId)).findFirst()
                .orElse(null);

        PartnerLeaderboardRespVO response = new PartnerLeaderboardRespVO();
        response.setPeriod(range.period());
        response.setPeriodLabel(range.label());
        response.setType(type.key());
        response.setTypeLabel(type.label());
        response.setValueLabel(type.valueLabel());
        response.setValueUnit(type.valueUnit());
        response.setRuleText(type.ruleText());
        response.setTotal((long) rows.size());
        response.setPageNo(pageNo);
        response.setPageSize(pageSize);
        response.setList(rows.subList(fromIndex, toIndex).stream().map(this::toRankMember).toList());
        response.setTop3(rows.stream().limit(3).map(this::toRankMember).toList());
        response.setMyRank(mine == null ? null : toRankMember(mine));
        response.setPreviousGap(gapToRank(mine, rows, mine == null ? null : mine.rank() - 1, type.valueUnit()));
        response.setTop10Gap(gapToRank(mine, rows, 10, type.valueUnit()));
        response.setNearbyRanks(nearbyRows(mine, rows).stream().map(this::toRankMember).toList());
        return response;
    }

    @Override
    public PartnerLeadActivityRespVO getLeadActivity(Long partnerId, Long leadId) {
        requireEnabledPartner(partnerId);
        LeadDO lead = leadMapper.selectById(leadId);
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        if (!Objects.equals(lead.getPartnerId(), partnerId)) throw exception(LEAD_PERMISSION_DENIED);

        PartnerLeadActivityRespVO response = new PartnerLeadActivityRespVO();
        response.setCurrentStatus(currentStatus(lead));
        List<LeadFollowUpRecordDO> followUps = followUpRecordMapper.selectListByLeadId(leadId);
        Map<Long, List<LeadFollowUpImageDO>> images = followUpImages(followUps);
        response.setFollowUps(followUps.stream().map(record -> toFollowUp(record,
                images.getOrDefault(record.getId(), List.of()))).toList());
        response.setCashbackItems(cashbackMapper.selectListByLeadIdAndPartner(leadId, partnerId).stream()
                .map(this::toCashbackItem).toList());
        response.setComplaints(complaintMapper.selectListByLeadId(leadId).stream()
                .filter(row -> Objects.equals(row.getPartnerId(), partnerId))
                .map(this::toComplaintItem).toList());
        response.setOrders(salesOrderMapper.selectByLeadId(leadId).stream().map(this::toOrderItem).toList());
        response.setTimeline(timeline(lead, followUps, response.getCashbackItems(),
                response.getComplaints(), response.getOrders()));
        return response;
    }

    @Override
    public PartnerLeadFilterOptionsRespVO getLeadFilterOptions() {
        return new PartnerLeadFilterOptionsRespVO(
                List.of(
                        new PartnerLeadFilterOptionsRespVO.Option(APPEAL_STATUS_SALES_MANAGER_REVIEWING, "销售主管复核中"),
                        new PartnerLeadFilterOptionsRespVO.Option(APPEAL_STATUS_QUALITY_REVIEWING, "质控复核中"),
                        new PartnerLeadFilterOptionsRespVO.Option(APPEAL_STATUS_CHAIRMAN_REVIEWING, "董事长终审中"),
                        new PartnerLeadFilterOptionsRespVO.Option(APPEAL_STATUS_OVERTURNED, "已改判有效"),
                        new PartnerLeadFilterOptionsRespVO.Option(APPEAL_STATUS_UPHELD, "维持无效"),
                        new PartnerLeadFilterOptionsRespVO.Option("withdrawn", "已撤回")),
                List.of(
                        new PartnerLeadFilterOptionsRespVO.Option(STATUS_PENDING_APPROVAL, "审核中"),
                        new PartnerLeadFilterOptionsRespVO.Option(STATUS_REVISION_REQUIRED, "需补正"),
                        new PartnerLeadFilterOptionsRespVO.Option(STATUS_EFFECTIVE, "已生效"),
                        new PartnerLeadFilterOptionsRespVO.Option(STATUS_SUPERSEDED, "已接续"),
                        new PartnerLeadFilterOptionsRespVO.Option(STATUS_TERMINATED, "已终止")));
    }

    private DateRange homeRange(String period) {
        return range(period, true);
    }

    private DateRange leaderboardRange(String period) {
        return range("year".equals(period) ? "month" : period, false);
    }

    private DateRange range(String rawPeriod, boolean includeYear) {
        String period = switch (rawPeriod == null ? "" : rawPeriod) {
            case "today", "week", "month", "total" -> rawPeriod;
            case "year" -> includeYear ? "year" : "month";
            default -> includeYear ? "total" : "month";
        };
        LocalDate today = LocalDate.now(BIZ_ZONE);
        return switch (period) {
            case "today" -> new DateRange(period, "今日", today.atStartOfDay(),
                    today.plusDays(1).atStartOfDay());
            case "week" -> {
                LocalDate start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new DateRange(period, "本周", start.atStartOfDay(), start.plusWeeks(1).atStartOfDay());
            }
            case "month" -> {
                LocalDate start = today.withDayOfMonth(1);
                yield new DateRange(period, "本月", start.atStartOfDay(), start.plusMonths(1).atStartOfDay());
            }
            case "year" -> {
                LocalDate start = today.withDayOfYear(1);
                yield new DateRange(period, "本年", start.atStartOfDay(), start.plusYears(1).atStartOfDay());
            }
            default -> new DateRange("total", "累计", null, null);
        };
    }

    private String normalizeMetric(String metric) {
        return switch (metric == null ? "" : metric) {
            case "lead_count", "withdrawn_amount", "valid_lead_count", "converted_lead_count" -> metric;
            default -> "lead_count";
        };
    }

    private List<RankRow> rankingRows(String type, DateRange range, Long currentPartnerId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        List<PartnerLeaderboardMetricRow> source = switch (type) {
            case "estimated_income" -> cashbackMapper.selectPartnerEstimatedIncomeRanking(tenantId,
                    range.from(), range.to());
            case "withdrawn_amount" -> withdrawalMapper.selectPartnerWithdrawnAmountRanking(tenantId,
                    range.from(), range.to());
            case "valid_lead_count" -> leadMapper.selectPartnerValidLeadCountRanking(tenantId,
                    range.from(), range.to());
            default -> leadMapper.selectPartnerLeadCountRanking(tenantId, range.from(), range.to());
        };
        Map<Long, BigDecimal> values = source.stream()
                .filter(row -> row.getPartnerId() != null)
                .collect(Collectors.toMap(PartnerLeaderboardMetricRow::getPartnerId,
                        row -> zeroIfNull(row.getValue()), BigDecimal::add, LinkedHashMap::new));
        values.putIfAbsent(currentPartnerId, BigDecimal.ZERO);
        Map<Long, PartnerDO> partners = partnerMap(values.keySet());
        List<RankSeed> seeds = values.entrySet().stream()
                .map(entry -> new RankSeed(entry.getKey(), entry.getValue(),
                        displayName(partners.get(entry.getKey()))))
                .sorted(Comparator.comparing(RankSeed::value).reversed()
                        .thenComparing(RankSeed::partnerId))
                .toList();
        List<RankRow> rows = new ArrayList<>(seeds.size());
        for (int i = 0; i < seeds.size(); i++) {
            RankSeed seed = seeds.get(i);
            BigDecimal gap = i == 0 ? null : seeds.get(i - 1).value().subtract(seed.value()).max(BigDecimal.ZERO);
            rows.add(new RankRow(seed.partnerId(), seed.displayName(), i + 1, seed.value(),
                    Objects.equals(seed.partnerId(), currentPartnerId), gap));
        }
        return rows;
    }

    private Map<Long, PartnerDO> partnerMap(Collection<Long> partnerIds) {
        List<Long> ids = partnerIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return partnerMapper.selectListByIds(ids).stream()
                .collect(Collectors.toMap(PartnerDO::getId, Function.identity(), (a, b) -> a));
    }

    private PartnerLeaderboardRespVO.Member toRankMember(RankRow row) {
        PartnerLeaderboardRespVO.Member member = new PartnerLeaderboardRespVO.Member();
        member.setPartnerId(row.partnerId());
        member.setDisplayName(row.displayName());
        member.setRank(row.rank());
        member.setValue(row.value());
        member.setIsMe(row.isMe());
        member.setGapToPrevious(row.gapToPrevious());
        return member;
    }

    private PartnerLeaderboardRespVO.Gap gapToRank(RankRow mine, List<RankRow> rows,
                                                   Integer targetRank, String valueUnit) {
        if (mine == null || targetRank == null || targetRank < 1 || targetRank > rows.size()) return null;
        PartnerLeaderboardRespVO.Gap gap = new PartnerLeaderboardRespVO.Gap();
        gap.setTargetRank(targetRank);
        if (mine.rank() <= targetRank) {
            gap.setValue(BigDecimal.ZERO);
            gap.setDisplayValue("已达成");
            gap.setTargetReached(true);
            return gap;
        }
        BigDecimal value = rows.get(targetRank - 1).value().subtract(mine.value()).max(BigDecimal.ZERO);
        gap.setValue(value);
        gap.setDisplayValue("money".equals(valueUnit) ? "¥" + value : value.toPlainString());
        gap.setTargetReached(false);
        return gap;
    }

    private List<RankRow> nearbyRows(RankRow mine, List<RankRow> rows) {
        if (mine == null) return List.of();
        int start = Math.max(0, mine.rank() - 3);
        int end = Math.min(rows.size(), mine.rank() + 2);
        return rows.subList(start, end);
    }

    private Map<Long, LeadIntendedProductDO> primaryProducts(List<LeadDO> leads) {
        List<Long> ids = leads.stream().map(LeadDO::getId).toList();
        if (ids.isEmpty()) return Map.of();
        return intendedProductMapper.selectListByLeadIds(ids).stream()
                .filter(item -> Boolean.TRUE.equals(item.getIsPrimary()))
                .collect(Collectors.toMap(LeadIntendedProductDO::getLeadId,
                        Function.identity(), (a, b) -> a));
    }

    private PartnerHomeStatisticsDetailRespVO.LeadItem toLeadItem(LeadDO lead,
                                                                  LeadIntendedProductDO product) {
        PartnerHomeStatisticsDetailRespVO.LeadItem item = new PartnerHomeStatisticsDetailRespVO.LeadItem();
        item.setId(lead.getId());
        item.setLeadNo(lead.getLeadNo());
        item.setSubmittedName(lead.getSubmittedName());
        item.setStatus(lead.getStatus());
        item.setCourseName(productName(product));
        item.setSubmittedAt(lead.getSubmittedAt());
        item.setSourceLabel(sourceLabel(lead.getSourceType()));
        item.setMobileMasked(lead.getSubmittedMobile() == null ? null
                : DesensitizedUtil.mobilePhone(lead.getSubmittedMobile()));
        item.setLocation(location(lead.getProvinceName(), lead.getCityName()));
        item.setTimeline(compactLeadTimeline(lead));
        return item;
    }

    private PartnerHomeStatisticsDetailRespVO.WithdrawalItem toWithdrawalItem(WithdrawalDO row) {
        PartnerHomeStatisticsDetailRespVO.WithdrawalItem item =
                new PartnerHomeStatisticsDetailRespVO.WithdrawalItem();
        item.setId(row.getId());
        item.setWithdrawalNo(row.getWithdrawalNo());
        item.setStatus(row.getStatus());
        item.setApplicationAmount(row.getApplicationAmount());
        item.setApprovedAmount(row.getApprovedAmount());
        item.setSubmittedAt(row.getSubmittedAt());
        item.setPaidAt(row.getPaidAt());
        item.setAccountNameSnapshot(row.getAccountNameSnapshot());
        item.setBankNameSnapshot(row.getBankNameSnapshot());
        item.setMaskedCardNumber(maskCard(row.getCardNumberSnapshot()));
        return item;
    }

    private PartnerLeadActivityRespVO.CurrentStatus currentStatus(LeadDO lead) {
        PartnerLeadActivityRespVO.CurrentStatus status = new PartnerLeadActivityRespVO.CurrentStatus();
        status.setCode(lead.getStatus());
        status.setText(leadStatusLabel(lead.getStatus()));
        status.setDescription(leadStatusDescription(lead));
        status.setTone(statusTone(lead.getStatus()));
        status.setUpdatedAt(lead.getLastActivityAt() == null ? lead.getUpdateTime() : lead.getLastActivityAt());
        return status;
    }

    private PartnerLeadActivityRespVO.FollowUpItem toFollowUp(LeadFollowUpRecordDO record,
                                                              List<LeadFollowUpImageDO> images) {
        PartnerLeadActivityRespVO.FollowUpItem item = new PartnerLeadActivityRespVO.FollowUpItem();
        item.setId(record.getId());
        item.setLeadId(record.getLeadId());
        item.setAssignmentHistoryId(record.getAssignmentHistoryId());
        item.setOccurredAt(record.getOccurredAt());
        item.setFirstInAssignment(record.getFirstInAssignment());
        item.setResult(record.getResultValue());
        item.setResultLabel(record.getResultLabelSnapshot());
        item.setMethod(record.getMethodValue());
        item.setMethodLabel(record.getMethodLabelSnapshot());
        item.setNextFollowUpAt(record.getNextFollowUpAt() == null ? null : record.getNextFollowUpAt().toString());
        item.setImages(images.stream().map(image -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", image.getId());
            result.put("infraFileId", image.getInfraFileId());
            result.put("originalName", image.getOriginalName());
            result.put("contentType", image.getContentType());
            result.put("fileSize", image.getFileSize());
            result.put("sort", image.getSort());
            return (Object) result;
        }).toList());
        return item;
    }

    private Map<Long, List<LeadFollowUpImageDO>> followUpImages(List<LeadFollowUpRecordDO> records) {
        List<Long> ids = records.stream().map(LeadFollowUpRecordDO::getId).toList();
        if (ids.isEmpty()) return Map.of();
        return followUpImageMapper.selectListByRecordIds(ids).stream()
                .collect(Collectors.groupingBy(LeadFollowUpImageDO::getFollowUpRecordId,
                        LinkedHashMap::new, Collectors.toList()));
    }

    private PartnerLeadActivityRespVO.CashbackItem toCashbackItem(CashbackDO row) {
        PartnerLeadActivityRespVO.CashbackItem item = new PartnerLeadActivityRespVO.CashbackItem();
        item.setId(row.getId());
        item.setTypeText(cashbackTypeLabel(row.getType()));
        item.setStatusText(cashbackStatusLabel(row.getStatus()));
        item.setAmount(row.getAmount());
        item.setAvailableAt(row.getAvailableAt());
        return item;
    }

    private PartnerLeadActivityRespVO.ComplaintItem toComplaintItem(LeadComplaintDO row) {
        PartnerLeadActivityRespVO.ComplaintItem item = new PartnerLeadActivityRespVO.ComplaintItem();
        item.setId(row.getId());
        item.setRecordNo("CP-" + row.getId());
        item.setStatus(row.getStatus());
        item.setStatusText("handled".equals(row.getStatus()) ? "已处理" : "处理中");
        item.setContent(row.getReason());
        item.setResult(complaintResultLabel(row.getResult()));
        item.setCreatedAt(row.getCreateTime());
        item.setAttachments(List.of());
        return item;
    }

    private PartnerLeadActivityRespVO.OrderItem toOrderItem(SalesOrderDO row) {
        PartnerLeadActivityRespVO.OrderItem item = new PartnerLeadActivityRespVO.OrderItem();
        item.setId(row.getId());
        item.setOrderNo(row.getOrderNo());
        item.setStatus(row.getStatus());
        item.setStatusText(orderStatusLabel(row.getStatus()));
        item.setPurchaseTypeText(orderTypeLabel(row.getOrderType()));
        item.setTotalAmount(row.getTotalAmount());
        item.setCreatedAt(row.getSubmittedAt() == null ? row.getCreateTime() : row.getSubmittedAt());
        return item;
    }

    private List<PartnerLeadActivityRespVO.TimelineItem> timeline(LeadDO lead,
                                                                  List<LeadFollowUpRecordDO> followUps,
                                                                  List<PartnerLeadActivityRespVO.CashbackItem> cashbackItems,
                                                                  List<PartnerLeadActivityRespVO.ComplaintItem> complaints,
                                                                  List<PartnerLeadActivityRespVO.OrderItem> orders) {
        List<PartnerLeadActivityRespVO.TimelineItem> items = new ArrayList<>();
        addTimeline(items, "lead-submitted", "lead", "客资已提交",
                sourceLabel(lead.getSourceType()), lead.getSubmittedAt(), "primary", false);
        if (lead.getQualifiedAt() != null) {
            addTimeline(items, "lead-qualified", "lead", leadStatusLabel(lead.getStatus()),
                    lead.getValidDescription(), lead.getQualifiedAt(), statusTone(lead.getStatus()), false);
        }
        followUps.stream().limit(3).forEach(record -> addTimeline(items, "follow-up-" + record.getId(),
                "follow_up", "销售已跟进", record.getResultLabelSnapshot(),
                record.getOccurredAt(), "default", false));
        orders.stream().limit(3).forEach(order -> addTimeline(items, "order-" + order.getId(),
                "order", "成交订单更新", order.getStatusText(), order.getCreatedAt(), "success", false));
        cashbackItems.stream().limit(3).forEach(cashback -> addTimeline(items, "cashback-" + cashback.getId(),
                "cashback", "返现更新", cashback.getStatusText(), cashback.getAvailableAt(), "success", false));
        complaints.stream().limit(3).forEach(complaint -> addTimeline(items, "complaint-" + complaint.getId(),
                "complaint", "投诉处理", complaint.getStatusText(), complaint.getCreatedAt(), "warning", false));
        if (lead.getClosedAt() != null) {
            addTimeline(items, "lead-closed", "lead", "客资已关闭", lead.getCloseReason(),
                    lead.getClosedAt(), "default", false);
        }
        items.sort(Comparator.comparing(PartnerLeadActivityRespVO.TimelineItem::getOccurredAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        if (!items.isEmpty()) items.get(0).setCurrent(true);
        return items;
    }

    private void addTimeline(List<PartnerLeadActivityRespVO.TimelineItem> items, String id, String type,
                             String title, String description, LocalDateTime occurredAt, String tone,
                             boolean current) {
        if (occurredAt == null) return;
        PartnerLeadActivityRespVO.TimelineItem item = new PartnerLeadActivityRespVO.TimelineItem();
        item.setId(id);
        item.setType(type);
        item.setTitle(title);
        item.setDescription(description);
        item.setOccurredAt(occurredAt);
        item.setTone(tone);
        item.setCurrent(current);
        items.add(item);
    }

    private List<PartnerHomeStatisticsDetailRespVO.TimelineItem> compactLeadTimeline(LeadDO lead) {
        List<PartnerHomeStatisticsDetailRespVO.TimelineItem> items = new ArrayList<>();
        addCompactTimeline(items, "submitted", "客资提交", sourceLabel(lead.getSourceType()), lead.getSubmittedAt());
        addCompactTimeline(items, "qualified", leadStatusLabel(lead.getStatus()), lead.getValidDescription(),
                lead.getQualifiedAt());
        addCompactTimeline(items, "converted", "客资成交", null, lead.getConvertedAt());
        addCompactTimeline(items, "closed", "客资关闭", lead.getCloseReason(), lead.getClosedAt());
        return items;
    }

    private void addCompactTimeline(List<PartnerHomeStatisticsDetailRespVO.TimelineItem> items,
                                    String id, String title, String description, LocalDateTime occurredAt) {
        if (occurredAt == null) return;
        PartnerHomeStatisticsDetailRespVO.TimelineItem item =
                new PartnerHomeStatisticsDetailRespVO.TimelineItem();
        item.setId(id);
        item.setTitle(title);
        item.setDescription(description);
        item.setOccurredAt(occurredAt);
        items.add(item);
    }

    private PartnerDO requireEnabledPartner(Long partnerId) {
        PartnerDO partner = partnerMapper.selectById(partnerId);
        if (partner == null) throw exception(PARTNER_NOT_EXISTS);
        if (!PARTNER_STATUS_ENABLED.equals(partner.getStatus())) throw exception(PARTNER_ACCOUNT_DISABLED);
        return partner;
    }

    private String displayName(PartnerDO partner) {
        String name = partner == null || partner.getName() == null || partner.getName().isBlank()
                ? "合作方" : partner.getName();
        return name.length() <= 1 ? name : DesensitizedUtil.chineseName(name);
    }

    private String productName(LeadIntendedProductDO product) {
        if (product == null) return "未选择课程";
        if (product.getSkuNameSnapshot() != null && !product.getSkuNameSnapshot().isBlank()) {
            return product.getSkuNameSnapshot();
        }
        if (product.getSpuNameSnapshot() != null && !product.getSpuNameSnapshot().isBlank()) {
            return product.getSpuNameSnapshot();
        }
        return "未选择课程";
    }

    private String sourceLabel(String sourceType) {
        if (sourceType == null) return "来源未配置";
        return switch (sourceType) {
            case SOURCE_PARTNER -> "兼职提交";
            case SOURCE_INTERNAL_NEW_MEDIA -> "新媒体提交";
            case SOURCE_SALES_SELF -> "销售自拓录";
            default -> "来源未配置";
        };
    }

    private String leadStatusLabel(String status) {
        return switch (status == null ? "" : status) {
            case STATUS_SUBMITTED -> "待判定";
            case STATUS_SUSPENDED -> "已挂起";
            case STATUS_VALID -> "有效";
            case STATUS_CONVERTED -> "已转化";
            case STATUS_INVALID -> "无效";
            case STATUS_WON -> "已成交";
            case STATUS_CLOSED -> "已关闭";
            default -> "未知状态";
        };
    }

    private String leadStatusDescription(LeadDO lead) {
        if (STATUS_INVALID.equals(lead.getStatus())) {
            return lead.getInvalidReasonLabelSnapshot() == null ? lead.getInvalidDescription()
                    : lead.getInvalidReasonLabelSnapshot();
        }
        if (STATUS_VALID.equals(lead.getStatus()) || STATUS_WON.equals(lead.getStatus())) {
            return lead.getValidDescription();
        }
        return switch (lead.getAssignmentStatus() == null ? "" : lead.getAssignmentStatus()) {
            case ASSIGNMENT_UNASSIGNED -> "等待系统分配销售";
            case ASSIGNMENT_PENDING -> "销售待接收";
            case ASSIGNMENT_OWNED -> "销售跟进中";
            case ASSIGNMENT_PUBLIC_POOL -> "抢单池待认领";
            case ASSIGNMENT_RECYCLE_PENDING -> "待回收处理";
            default -> null;
        };
    }

    private String statusTone(String status) {
        return switch (status == null ? "" : status) {
            case STATUS_VALID, STATUS_CONVERTED, STATUS_WON -> "success";
            case STATUS_INVALID -> "danger";
            case STATUS_SUBMITTED, STATUS_SUSPENDED -> "warning";
            default -> "default";
        };
    }

    private String cashbackTypeLabel(String type) {
        return switch (type == null ? "" : type) {
            case TYPE_VALID -> "有效客资奖励";
            case TYPE_DEAL -> "成交奖励";
            default -> "返现奖励";
        };
    }

    private String cashbackStatusLabel(String status) {
        return switch (status == null ? "" : status) {
            case STATUS_PENDING -> "待结算";
            case STATUS_AVAILABLE -> "可提现";
            case STATUS_WITHDRAWING -> "提现中";
            case STATUS_WITHDRAWN -> "已提现";
            case STATUS_CANCELLED -> "已取消";
            default -> "未知状态";
        };
    }

    private String complaintResultLabel(String result) {
        return switch (result == null ? "" : result) {
            case "founded" -> "投诉成立";
            case "unfounded" -> "投诉不成立";
            default -> null;
        };
    }

    private String orderStatusLabel(String status) {
        return switch (status == null ? "" : status) {
            case STATUS_PENDING_APPROVAL -> "审核中";
            case STATUS_REVISION_REQUIRED -> "需补正";
            case STATUS_EFFECTIVE -> "已生效";
            case STATUS_SUPERSEDED -> "已接续";
            case STATUS_TERMINATED -> "已终止";
            default -> "未知状态";
        };
    }

    private String orderTypeLabel(String orderType) {
        return switch (orderType == null ? "" : orderType) {
            case ORDER_TYPE_FIRST_PURCHASE -> "首次购买";
            case ORDER_TYPE_REPURCHASE -> "复购";
            default -> null;
        };
    }

    private String location(String provinceName, String cityName) {
        if (provinceName == null || provinceName.isBlank()) return cityName;
        if (cityName == null || cityName.isBlank() || Objects.equals(provinceName, cityName)) return provinceName;
        return provinceName + " " + cityName;
    }

    private String maskCard(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) return null;
        String normalized = cardNumber.replaceAll("\\s+", "");
        if (normalized.length() <= 4) return "****" + normalized;
        return "**** **** **** " + normalized.substring(normalized.length() - 4);
    }

    private Long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record DateRange(String period, String label, LocalDateTime from, LocalDateTime to) {}

    private record TypeDef(String key, String label, String valueLabel, String valueUnit, String ruleText) {}

    private record RankSeed(Long partnerId, BigDecimal value, String displayName) {}

    private record RankRow(Long partnerId, String displayName, Integer rank, BigDecimal value, Boolean isMe,
                           BigDecimal gapToPrevious) {}
}
