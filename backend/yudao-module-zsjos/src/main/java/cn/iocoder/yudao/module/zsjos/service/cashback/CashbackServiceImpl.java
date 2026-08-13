package cn.iocoder.yudao.module.zsjos.service.cashback;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.CashbackPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.CashbackRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.cashback.CashbackDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadIntendedProductDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductCategoryDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.cashback.CashbackMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadIntendedProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductCategoryMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.CashbackConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.SOURCE_PARTNER;
import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.PARTNER_STATUS_ENABLED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class CashbackServiceImpl implements CashbackService {
    static final String OBSERVATION_DAYS_KEY = "zsjos.cashback.observation-days";
    static final int DEFAULT_OBSERVATION_DAYS = 7;
    @Resource private CashbackMapper mapper;
    @Resource private LeadMapper leadMapper;
    @Resource private LeadIntendedProductMapper intendedProductMapper;
    @Resource private PartnerMapper partnerMapper;
    @Resource private ZsjosProductMapper productMapper;
    @Resource private ZsjosProductCategoryMapper categoryMapper;
    @Resource private SalesOrderMapper orderMapper;
    @Resource private ConfigApi configApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long ensureValidCashback(Long leadId) {
        String businessKey = "valid:" + leadId;
        CashbackDO existing = mapper.selectByBusinessKey(businessKey);
        if (existing != null) return reuseOrRestore(existing);
        LeadDO lead = eligibleLead(leadId);
        if (lead == null) return null;
        LeadIntendedProductDO primary = intendedProductMapper.selectPrimaryByLeadId(leadId);
        if (primary == null || primary.getProductRef() == null) throw exception(CASHBACK_RULE_NOT_CONFIGURED);
        Rule rule = resolveRule(primary.getProductRef());
        LocalDateTime now = LocalDateTime.now();
        int observationDays = observationDays();
        CashbackDO cashback = base(businessKey, TYPE_VALID, lead, primary.getProductRef(),
                primary.getProductNameSnapshot(), now, observationDays)
                .setBaseAmount(null).setRateSnapshot(null).setAmount(rule.validAmount())
                .setRuleSnapshotJson(JsonUtils.toJsonString(rule.snapshot()));
        return insertOrReuse(cashback);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long ensureDealCashback(DealCashbackCommand command) {
        if (command == null || command.orderId() == null || command.orderItemId() == null
                || command.actualAmount() == null || command.actualAmount().signum() <= 0
                || command.rateSnapshot() == null || command.rateSnapshot().signum() < 0
                || command.rateSnapshot().compareTo(BigDecimal.ONE) > 0) throw exception(CASHBACK_SOURCE_INVALID);
        String key = "deal:" + command.orderItemId();
        CashbackDO existing = mapper.selectByBusinessKey(key);
        if (existing != null) return existing.getId();
        LeadDO lead = eligibleLead(command.leadId());
        if (lead == null) return null;
        LocalDateTime now = LocalDateTime.now();
        int observationDays = observationDays();
        BigDecimal amount = command.actualAmount().multiply(command.rateSnapshot())
                .setScale(2, RoundingMode.HALF_UP);
        CashbackDO cashback = base(key, TYPE_DEAL, lead, command.productRef(), command.productName(),
                now, observationDays).setOrderId(command.orderId()).setOrderItemId(command.orderItemId())
                .setBaseAmount(command.actualAmount().setScale(2, RoundingMode.HALF_UP))
                .setRateSnapshot(command.rateSnapshot()).setAmount(amount)
                .setRuleSnapshotJson(JsonUtils.toJsonString(Map.of("source", "order_item_snapshot")));
        return insertOrReuse(cashback);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int settleMatured() {
        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        for (CashbackDO cashback : mapper.selectMatured(now)) {
            if (TYPE_DEAL.equals(cashback.getType())) {
                var order = cashback.getOrderId() == null ? null : orderMapper.selectById(cashback.getOrderId());
                if (order == null || !"effective".equals(order.getStatus())) continue;
            }
            count += mapper.transition(cashback.getId(), cashback.getVersion(), STATUS_PENDING, STATUS_AVAILABLE, now);
        }
        return count;
    }

    @Override
    public PageResult<CashbackRespVO> getPage(CashbackPageReqVO request, Long beneficiaryUserId) {
        return BeanUtils.toBean(mapper.selectPage(request, beneficiaryUserId), CashbackRespVO.class);
    }

    private Long reuseOrRestore(CashbackDO existing) {
        if (!STATUS_CANCELLED.equals(existing.getStatus())) return existing.getId();
        LocalDateTime now = LocalDateTime.now();
        if (mapper.restoreValid(existing.getId(), existing.getVersion(), now,
                now.plusDays(existing.getObservationDaysSnapshot())) != 1) throw exception(CASHBACK_STATE_INVALID);
        return existing.getId();
    }

    private CashbackDO base(String businessKey, String type, LeadDO lead, String productRef,
                            String productName, LocalDateTime now, int observationDays) {
        return new CashbackDO().setCashbackNo("CB" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 20).toUpperCase()).setBusinessKey(businessKey).setType(type).setStatus(STATUS_PENDING)
                .setBeneficiaryUserId(lead.getSourceUserId()).setPartnerId(lead.getPartnerId()).setLeadId(lead.getId())
                .setProductRefSnapshot(productRef).setProductNameSnapshot(productName)
                .setObservationDaysSnapshot(observationDays).setGeneratedAt(now)
                .setAvailableAt(now.plusDays(observationDays)).setVersion(0);
    }

    private Long insertOrReuse(CashbackDO cashback) {
        try {
            mapper.insert(cashback);
            return cashback.getId();
        } catch (DuplicateKeyException duplicate) {
            CashbackDO existing = mapper.selectByBusinessKey(cashback.getBusinessKey());
            if (existing == null) throw duplicate;
            return existing.getId();
        }
    }

    private LeadDO eligibleLead(Long leadId) {
        LeadDO lead = leadId == null ? null : leadMapper.selectById(leadId);
        if (lead == null) throw exception(CASHBACK_SOURCE_INVALID);
        if (!SOURCE_PARTNER.equals(lead.getSourceType())) return null;
        if (lead.getSourceUserId() == null || lead.getPartnerId() == null) throw exception(CASHBACK_SOURCE_INVALID);
        PartnerDO partner = partnerMapper.selectById(lead.getPartnerId());
        if (partner == null || !Objects.equals(partner.getBoundSystemUserId(), lead.getSourceUserId())) {
            throw exception(CASHBACK_SOURCE_INVALID);
        }
        if (!PARTNER_STATUS_ENABLED.equals(partner.getStatus())) return null;
        return lead;
    }

    private Rule resolveRule(String productRef) {
        ZsjosProductDO product = productMapper.selectByProductRef(productRef);
        if (product == null) throw exception(CASHBACK_RULE_NOT_CONFIGURED);
        ZsjosProductCategoryDO category = categoryMapper.selectById(product.getCategoryId());
        while (category != null && category.getParentId() != null && category.getParentId() != 0) {
            category = categoryMapper.selectById(category.getParentId());
        }
        BigDecimal amount = product.getValidCashbackAmount() != null ? product.getValidCashbackAmount()
                : category == null ? null : category.getDefaultValidCashbackAmount();
        BigDecimal rate = product.getDealCashbackRate() != null ? product.getDealCashbackRate()
                : category == null ? null : category.getDefaultDealCashbackRate();
        if (amount == null || amount.signum() < 0 || rate == null || rate.signum() < 0
                || rate.compareTo(BigDecimal.ONE) > 0) throw exception(CASHBACK_RULE_NOT_CONFIGURED);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("productId", product.getId()); snapshot.put("productRef", productRef);
        snapshot.put("level1CategoryId", category == null ? null : category.getId());
        snapshot.put("validCashbackAmount", amount); snapshot.put("dealCashbackRate", rate);
        snapshot.put("validAmountSource", product.getValidCashbackAmount() == null ? "level1_category" : "product");
        snapshot.put("dealRateSource", product.getDealCashbackRate() == null ? "level1_category" : "product");
        return new Rule(amount.setScale(2, RoundingMode.HALF_UP), rate, snapshot);
    }

    private int observationDays() {
        String configured = configApi.getConfigValueByKey(OBSERVATION_DAYS_KEY);
        if (configured == null || configured.isBlank()) return DEFAULT_OBSERVATION_DAYS;
        try {
            int value = Integer.parseInt(configured);
            return value >= 0 && value <= 365 ? value : DEFAULT_OBSERVATION_DAYS;
        } catch (NumberFormatException ignored) {
            return DEFAULT_OBSERVATION_DAYS;
        }
    }

    private record Rule(BigDecimal validAmount, BigDecimal dealRate, Map<String, Object> snapshot) {}
}
