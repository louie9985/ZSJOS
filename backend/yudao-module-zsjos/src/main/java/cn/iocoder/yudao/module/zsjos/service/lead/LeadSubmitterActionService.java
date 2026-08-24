package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.ip.core.Area;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.ip.AreaApi;
import cn.iocoder.yudao.module.system.api.ip.dto.AreaRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadSubmitterSupplementReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadUrgeReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadProductReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class LeadSubmitterActionService {
    @Resource private LeadMapper leadMapper; @Resource private LeadIntendedProductMapper productMapper;
    @Resource private AreaApi areaApi;
    @Resource private ZsjosProductSkuService productSkuService; @Resource private BusinessEventMapper eventMapper;
    @Resource private LeadUrgeMapper urgeMapper; @Resource private LeadNotifyEventPublisher notifyPublisher;
    @Resource private LeadSubmissionIdentityService identityService;
    @Resource private LeadCategorySnapshotService categorySnapshotService;

    @Transactional(rollbackFor = Exception.class)
    public void supplement(Long leadId, Long userId, LeadSubmitterSupplementReqVO req) {
        supplementInternal(leadId, userId, null, req);
    }

    @Transactional(rollbackFor = Exception.class)
    public void supplementForPartner(Long leadId, Long partnerId, LeadSubmitterSupplementReqVO req) {
        supplementInternal(leadId, null, partnerId, req);
    }

    private void supplementInternal(Long leadId, Long userId, Long partnerId, LeadSubmitterSupplementReqVO req) {
        if (eventMapper.selectByIdempotencyKey(req.getIdempotencyKey()) != null) return;
        LeadDO lead = requireSubmitterLeadForUpdate(leadId, userId, partnerId);
        requireActionable(lead);
        Region region = region(req.getProvinceCode(), req.getCityCode());
        LeadCategorySnapshotService.Selection category = categorySnapshotService.requireEnabled(req.getLeadCategory());
        List<LeadProductSnapshot> snapshots = products(req.getIntendedProducts());
        Map<String,Object> before = Map.of("provinceCode", Objects.toString(lead.getProvinceCode(), ""),
                "cityCode", Objects.toString(lead.getCityCode(), ""), "leadCategory", Objects.toString(lead.getLeadCategory(), ""),
                "remark", Objects.toString(lead.getRemark(), ""));
        lead.setProvinceCode(region.provinceCode()); lead.setProvinceName(region.provinceName());
        lead.setCityCode(region.cityCode()); lead.setCityName(region.cityName());
        if (!Objects.equals(lead.getLeadCategory(), category.value())) {
            lead.setLeadCategory(category.value());
            lead.setLeadCategoryLabelSnapshot(category.labelSnapshot());
        }
        lead.setRemark(StrUtil.trimToNull(req.getRemark())); leadMapper.updateById(lead);
        productMapper.deleteByLeadId(leadId); insertProducts(leadId, req.getIntendedProducts(), snapshots);
        BusinessEventDO event = new BusinessEventDO(); event.setEventType("lead_submitter_supplemented");
        event.setAggregateType(BIZ_TYPE_LEAD); event.setAggregateId(leadId); event.setOperatorUserId(userId);
        event.setRelatedObjectRefs(JsonUtils.toJsonString(Map.of("before", before))); event.setOccurredAt(LocalDateTime.now());
        event.setIdempotencyKey(req.getIdempotencyKey()); eventMapper.insert(event);
    }

    @Transactional(rollbackFor = Exception.class)
    public void urge(Long leadId, Long userId, LeadUrgeReqVO req) {
        urgeInternal(leadId, userId, null, req);
    }

    @Transactional(rollbackFor = Exception.class)
    public void urgeForPartner(Long leadId, Long partnerId, LeadUrgeReqVO req) {
        urgeInternal(leadId, null, partnerId, req);
    }

    private void urgeInternal(Long leadId, Long userId, Long partnerId, LeadUrgeReqVO req) {
        LeadDO lead = requireSubmitterLeadForUpdate(leadId, userId, partnerId);
        requireActionable(lead);
        if (lead.getOwnerUserId() == null) throw exception(LEAD_SUBMITTER_ACTION_STATE_INVALID);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        LeadUrgeDO row = new LeadUrgeDO(); row.setLeadId(leadId); row.setSubmitterUserId(userId); row.setPartnerId(partnerId);
        row.setTargetSalesUserId(lead.getOwnerUserId()); row.setUrgeDate(now.toLocalDate()); row.setReason(req.getReason().trim()); row.setUrgedAt(now);
        try { urgeMapper.insert(row); } catch (DuplicateKeyException ex) { throw exception(LEAD_URGE_DAILY_LIMIT); }
        Map<String, Object> context = new HashMap<>();
        context.put("submitterUserId", userId); context.put("partnerId", partnerId);
        context.put("ownerUserId", lead.getOwnerUserId()); context.put("urge.reason", row.getReason());
        notifyPublisher.publish("zsjos.lead.submitter_urged", leadId, "lead-urge:" + row.getId(), userId, now, context);
    }

    private LeadDO requireSubmitterLeadForUpdate(Long leadId, Long userId, Long partnerId) {
        LeadDO lead = leadMapper.selectByIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        if (partnerId != null) {
            if (!Objects.equals(lead.getPartnerId(), partnerId)) throw exception(LEAD_PERMISSION_DENIED);
        } else {
            if (!Objects.equals(lead.getSourceUserId(), userId)) throw exception(LEAD_PERMISSION_DENIED);
            identityService.requireHistoricalSubmitter(lead, userId);
        }
        return lead;
    }

    private void requireActionable(LeadDO lead) {
        if (Set.of(STATUS_INVALID, STATUS_CLOSED, STATUS_WON).contains(lead.getStatus())) {
            throw exception(LEAD_SUBMITTER_ACTION_STATE_INVALID);
        }
    }
    private Region region(String provinceCode, String cityCode) {
        AreaRespDTO province = REGION_OTHER.equals(provinceCode) ? areaApi.getAreaByParentIdAndSelectionCode(Area.ID_CHINA, REGION_OTHER) : parse(provinceCode);
        if (!enabledArea(province, 2)) throw exception(LEAD_REGION_INVALID);
        AreaRespDTO city = REGION_OTHER.equals(cityCode) ? areaApi.getAreaByParentIdAndSelectionCode(province.getId(), REGION_OTHER) : parse(cityCode);
        if (city == null && REGION_OTHER.equals(cityCode) && Boolean.TRUE.equals(province.getLeafSelectable())) {
            return new Region(provinceCode, province.getName(), cityCode, null);
        }
        if (!enabledArea(city, 3) || !Objects.equals(city.getParentId(), province.getId())) {
            throw exception(LEAD_REGION_INVALID);
        }
        return new Region(provinceCode, province.getName(), cityCode, city == null ? null : city.getName());
    }
    private boolean enabledArea(AreaRespDTO area, int type) { return area != null && Integer.valueOf(type).equals(area.getType())
            && CommonStatusEnum.ENABLE.getStatus().equals(area.getStatus()); }
    private AreaRespDTO parse(String code) { try { return areaApi.getArea(Integer.valueOf(code)); } catch (NumberFormatException ex) { throw exception(LEAD_REGION_INVALID); } }
    private List<LeadProductSnapshot> products(List<LeadProductReqVO> requested) {
        if (requested == null || requested.stream().filter(x -> Boolean.TRUE.equals(x.getPrimary())).count() != 1) throw exception(LEAD_PRODUCT_REQUIRED);
        List<LeadProductSnapshot> result = new ArrayList<>(); Set<String> keys = new HashSet<>();
        for (LeadProductReqVO item : requested) { String key = item.effectiveSpuRef()+"|"+item.getSkuRef()+"|"+item.getSpuUnknown()+"|"+item.getSkuUnknown();
            if (!keys.add(key)) throw exception(LEAD_PRODUCT_DUPLICATE); result.add(productSkuService.validateLeadProduct(item.effectiveSpuRef(), Boolean.TRUE.equals(item.getSpuUnknown()), item.getSkuRef(), Boolean.TRUE.equals(item.getSkuUnknown()))); }
        return result;
    }
    private void insertProducts(Long leadId, List<LeadProductReqVO> requested, List<LeadProductSnapshot> snapshots) {
        for(int i=0;i<requested.size();i++){ LeadProductReqVO item=requested.get(i); LeadProductSnapshot s=snapshots.get(i); LeadIntendedProductDO row=new LeadIntendedProductDO();
            row.setLeadId(leadId); row.setProductRef(s.productRef()); row.setProductNameSnapshot(s.name()); row.setSpuRef(s.productRef()); row.setSpuNameSnapshot(s.name());
            row.setSkuRef(s.skuRef()); row.setSkuNameSnapshot(s.skuName()); row.setSelectedAttrValuesJson(s.selectedAttrValuesJson()); row.setPriceSnapshot(s.price());
            row.setSpuUnknown(s.spuUnknown()); row.setSkuUnknown(s.skuUnknown()); row.setIsPrimary(item.getPrimary()); row.setSort(i); productMapper.insert(row); }
    }
    private record Region(String provinceCode,String provinceName,String cityCode,String cityName) {}
}
