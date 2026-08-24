package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.validation.ValidationUtils;
import cn.iocoder.yudao.framework.ip.core.Area;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.ip.AreaApi;
import cn.iocoder.yudao.module.system.api.ip.dto.AreaRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadBasicInfoUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadProductReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class LeadBasicInfoService {
    @Resource private LeadMapper leadMapper;
    @Resource private PersonMapper personMapper;
    @Resource private LeadIntendedProductMapper productMapper;
    @Resource private OpportunityMapper opportunityMapper;
    @Resource private AreaApi areaApi;
    @Resource private ZsjosProductSkuService productSkuService;
    @Resource private BusinessEventMapper eventMapper;
    @Resource private LeadDuplicateMatcher duplicateMatcher;
    @Resource private PersonIdentityWriteService personIdentityWriteService;
    @Resource private LeadCategorySnapshotService categorySnapshotService;

    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "lead", bizId = "#leadId", action = "basic-info-update")
    public void update(Long leadId, Long userId, LeadBasicInfoUpdateReqVO req) {
        LeadDO lead = leadMapper.selectByIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        if (!Objects.equals(userId, lead.getOwnerUserId())) {
            throw exception(LEAD_PERMISSION_DENIED);
        }
        if (!canEdit(lead)) {
            throw exception(LEAD_BASIC_INFO_STATE_INVALID);
        }
        String mobile = StrUtil.trimToNull(req.getMobile());
        String wechat = StrUtil.trimToNull(req.getWechatId());
        if (mobile == null && wechat == null) throw exception(LEAD_CONTACT_REQUIRED);
        if (mobile != null && !ValidationUtils.isMobile(mobile)) throw exception(LEAD_MOBILE_INVALID);
        checkIdentityConflict(lead, req, mobile, wechat);
        Region region = validateRegion(req.getProvinceCode(), req.getCityCode());
        String category = StrUtil.trimToNull(req.getLeadCategory());
        LeadCategorySnapshotService.Selection categorySelection = categorySnapshotService.requireEnabled(category);
        List<LeadProductSnapshot> snapshots = validateProducts(req.getIntendedProducts());

        PersonDO person = personMapper.selectById(lead.getPersonId());
        List<String> changedFields = new ArrayList<>();
        if (!Objects.equals(person.getName(), req.getName().trim())) changedFields.add("name");
        if (!Objects.equals(person.getMobile(), mobile)) changedFields.add("mobile");
        if (!Objects.equals(person.getWechatId(), wechat)) changedFields.add("wechatId");
        if (!Objects.equals(lead.getProvinceCode(), region.provinceCode())
                || !Objects.equals(lead.getCityCode(), region.cityCode())) changedFields.add("region");
        if (!Objects.equals(lead.getLeadCategory(), category)) changedFields.add("leadCategory");
        String productsBefore = productSummary(productMapper.selectListByLeadId(leadId));
        personIdentityWriteService.update(person.getId(), req.getName().trim(), mobile, wechat);
        lead.setSubmittedName(req.getName().trim()); lead.setSubmittedMobile(mobile); lead.setSubmittedWechatId(wechat);
        lead.setProvinceCode(region.provinceCode()); lead.setProvinceName(region.provinceName());
        lead.setCityCode(region.cityCode()); lead.setCityName(region.cityName());
        if (!Objects.equals(lead.getLeadCategory(), categorySelection.value())) {
            lead.setLeadCategory(categorySelection.value());
            lead.setLeadCategoryLabelSnapshot(categorySelection.labelSnapshot());
        }
        leadMapper.updateById(lead);
        productMapper.deleteByLeadId(leadId);
        insertProducts(leadId, req.getIntendedProducts(), snapshots);
        List<LeadIntendedProductDO> productsAfter = productMapper.selectListByLeadId(leadId);
        if (!Objects.equals(productsBefore, productSummary(productsAfter))) changedFields.add("intendedProducts");
        OpportunityDO opportunity = opportunityMapper.selectByLeadId(leadId);
        if (opportunity != null) {
            opportunity.setExpectedProductSummary(productSummary(productsAfter));
            opportunityMapper.updateById(opportunity);
        }
        BusinessEventDO event = new BusinessEventDO();
        event.setEventType("lead_basic_info_updated"); event.setAggregateType(BIZ_TYPE_LEAD);
        event.setAggregateId(leadId); event.setOperatorUserId(userId); event.setReason(req.getReason().trim());
        event.setRelatedObjectRefs(JsonUtils.toJsonString(Map.of("changedFields", changedFields)));
        event.setOccurredAt(LocalDateTime.now()); event.setIdempotencyKey("lead-basic-info:" + UUID.randomUUID());
        eventMapper.insert(event);
    }

    private boolean canEdit(LeadDO lead) {
        if (STATUS_SUBMITTED.equals(lead.getStatus())) return ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus());
        if (!STATUS_VALID.equals(lead.getStatus()) || !ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus())) return false;
        OpportunityDO opportunity = opportunityMapper.selectByLeadId(lead.getId());
        return opportunity == null || Set.of(OPPORTUNITY_STATUS_OPEN, OPPORTUNITY_STATUS_FOLLOWING).contains(opportunity.getStatus());
    }

    private void checkIdentityConflict(LeadDO lead, LeadBasicInfoUpdateReqVO req, String mobile, String wechat) {
        LeadCreateReqVO probe = new LeadCreateReqVO();
        probe.setName(req.getName()); probe.setMobile(mobile); probe.setWechatId(wechat);
        probe.setProvinceCode(req.getProvinceCode()); probe.setCityCode(req.getCityCode());
        probe.setIntendedProducts(req.getIntendedProducts());
        if (duplicateMatcher.match(probe, lead.getPersonId()).hasMatches()) {
            throw exception(LEAD_BASIC_INFO_CONTACT_CONFLICT);
        }
    }

    private Region validateRegion(String provinceCode, String cityCode) {
        AreaRespDTO province = REGION_OTHER.equals(provinceCode)
                ? areaApi.getAreaByParentIdAndSelectionCode(Area.ID_CHINA, REGION_OTHER)
                : parseArea(provinceCode);
        if (!enabled(province, 2)) throw exception(LEAD_REGION_INVALID);
        if (REGION_OTHER.equals(cityCode)) {
            AreaRespDTO city = areaApi.getAreaByParentIdAndSelectionCode(province.getId(), REGION_OTHER);
            if (city != null && enabled(city, 3)) return new Region(provinceCode, province.getName(), cityCode, city.getName());
            if (Boolean.TRUE.equals(province.getLeafSelectable())) return new Region(provinceCode, province.getName(), cityCode, null);
            throw exception(LEAD_REGION_INVALID);
        }
        AreaRespDTO city = parseArea(cityCode);
        if (!enabled(city, 3) || !Objects.equals(city.getParentId(), province.getId())) throw exception(LEAD_REGION_INVALID);
        return new Region(provinceCode, province.getName(), cityCode, city.getName());
    }

    private AreaRespDTO parseArea(String code) {
        try { return areaApi.getArea(Integer.valueOf(code)); }
        catch (NumberFormatException ex) { throw exception(LEAD_REGION_INVALID); }
    }
    private boolean enabled(AreaRespDTO area, int type) {
        return area != null && Integer.valueOf(type).equals(area.getType())
                && CommonStatusEnum.ENABLE.getStatus().equals(area.getStatus());
    }

    private List<LeadProductSnapshot> validateProducts(List<LeadProductReqVO> requested) {
        if (requested == null || requested.stream().filter(x -> Boolean.TRUE.equals(x.getPrimary())).count() != 1) {
            throw exception(LEAD_PRODUCT_REQUIRED);
        }
        Set<String> keys = new HashSet<>(); List<LeadProductSnapshot> result = new ArrayList<>();
        for (LeadProductReqVO item : requested) {
            String key = Boolean.TRUE.equals(item.getSpuUnknown()) ? "UNKNOWN"
                    : item.effectiveSpuRef() + "|" + (Boolean.TRUE.equals(item.getSkuUnknown()) ? "UNKNOWN" : item.getSkuRef());
            if (!keys.add(key)) throw exception(LEAD_PRODUCT_DUPLICATE);
            result.add(productSkuService.validateLeadProduct(item.effectiveSpuRef(), Boolean.TRUE.equals(item.getSpuUnknown()),
                    item.getSkuRef(), Boolean.TRUE.equals(item.getSkuUnknown())));
        }
        return result;
    }

    private void insertProducts(Long leadId, List<LeadProductReqVO> requested, List<LeadProductSnapshot> snapshots) {
        for (int i = 0; i < requested.size(); i++) {
            LeadProductReqVO item = requested.get(i); LeadProductSnapshot s = snapshots.get(i);
            LeadIntendedProductDO row = new LeadIntendedProductDO();
            row.setLeadId(leadId); row.setProductRef(s.productRef()); row.setProductNameSnapshot(s.name());
            row.setSpuRef(s.productRef()); row.setSpuNameSnapshot(s.name()); row.setSkuRef(s.skuRef()); row.setSkuNameSnapshot(s.skuName());
            row.setSelectedAttrValuesJson(s.selectedAttrValuesJson()); row.setPriceSnapshot(s.price()); row.setSpuUnknown(s.spuUnknown()); row.setSkuUnknown(s.skuUnknown());
            row.setCategoryId(s.categoryId()); row.setCategoryNameSnapshot(s.categoryName()); row.setCategoryPathSnapshot(JsonUtils.toJsonString(s.categoryPath()));
            row.setLevel1CategoryId(s.level1CategoryId()); row.setLevel1CategoryNameSnapshot(s.level1CategoryName());
            row.setLevel2CategoryId(s.level2CategoryId()); row.setLevel2CategoryNameSnapshot(s.level2CategoryName());
            row.setIsPrimary(item.getPrimary()); row.setSort(i); productMapper.insert(row);
        }
    }

    public static String productSummary(List<LeadIntendedProductDO> products) {
        return JsonUtils.toJsonString(products.stream().map(p -> Map.of(
                "spuRef", Objects.toString(p.getSpuRef(), ""), "spuName", Objects.toString(p.getSpuNameSnapshot(), ""),
                "skuRef", Objects.toString(p.getSkuRef(), ""), "skuName", Objects.toString(p.getSkuNameSnapshot(), ""),
                "primary", Boolean.TRUE.equals(p.getIsPrimary()))).toList());
    }

    private record Region(String provinceCode, String provinceName, String cityCode, String cityName) {}
}
