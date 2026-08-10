package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.validation.ValidationUtils;
import cn.iocoder.yudao.framework.ip.core.Area;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.ip.AreaApi;
import cn.iocoder.yudao.module.system.api.ip.dto.AreaRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class LeadSubmissionServiceImpl implements LeadSubmissionService {

    @Resource private PersonMapper personMapper;
    @Resource private LeadMapper leadMapper;
    @Resource private LeadActivationMapper activationMapper;
    @Resource private LeadIntendedProductMapper intendedProductMapper;
    @Resource private LeadAttachmentMapper attachmentMapper;
    @Resource private AreaApi areaApi;
    @Resource private DictDataApi dictDataApi;
    @Resource private ZsjosProductSkuService productSkuService;
    @Resource private LeadDispatchService dispatchService;
    @Resource private LeadAttachmentService attachmentService;
    @Resource private LeadNotifyEventPublisher notifyEventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeadCreateRespVO create(LeadCreateReqVO reqVO, Long submitterUserId) {
        LeadCreateRespVO idempotent = findIdempotent(reqVO.getIdempotencyKey());
        if (idempotent != null) return idempotent;

        String mobile = StrUtil.trimToNull(reqVO.getMobile());
        String wechatId = StrUtil.trimToNull(reqVO.getWechatId());
        validateContact(mobile, wechatId);
        RegionSnapshot region = validateRegion(reqVO.getProvinceCode(), reqVO.getCityCode());
        dictDataApi.validateDictDataList(DICT_SOURCE_CHANNEL, List.of(reqVO.getSourceChannel()));
        dictDataApi.validateDictDataList(DICT_CATEGORY, List.of(reqVO.getLeadCategory()));
        List<LeadProductSnapshot> products = validateProducts(reqVO.getEffectiveProducts());
        Map<Long, FileInfoRespDTO> attachments = attachmentService.validateReferences(
                reqVO.getAttachments(), submitterUserId);
        validateDispatch(reqVO);

        PersonDO byMobile = mobile == null ? null : personMapper.selectByMobile(mobile);
        PersonDO byWechat = wechatId == null ? null : personMapper.selectByWechatId(wechatId);
        if (byMobile != null && byWechat != null && !Objects.equals(byMobile.getId(), byWechat.getId())) {
            throw exception(LEAD_CONTACT_CONFLICT);
        }
        PersonDO person = byMobile != null ? byMobile : byWechat;
        if (person == null) {
            person = createPerson(reqVO.getName().trim(), mobile, wechatId);
        } else {
            person.setLastSeenAt(LocalDateTime.now());
            personMapper.updateById(person);
        }

        LeadDO existing = leadMapper.selectLatestByPersonId(person.getId());
        if (existing != null) {
            LeadActivationDO activation = createActivation(existing, person, reqVO, products, region, submitterUserId);
            notifyEventPublisher.publish(ACTIVATED, existing.getId(), "lead-activated:" + activation.getId(),
                    submitterUserId, activation.getActivatedAt(), eventContext(existing, submitterUserId));
            if (existing.getOwnerUserId() != null) {
                dispatchService.notifyActivation(existing);
            } else if (!ASSIGNMENT_PENDING.equals(existing.getAssignmentStatus())) {
                existing.setDispatchMode(reqVO.getDispatchMode());
                dispatchService.start(existing, reqVO.getSpecifiedSalesUserId(), submitterUserId);
            }
            return response(existing, "activated");
        }

        LeadDO lead = createLead(person, reqVO, mobile, wechatId, region, submitterUserId);
        insertProducts(lead.getId(), reqVO.getEffectiveProducts(), products);
        insertAttachments(lead.getId(), reqVO.getAttachments(), attachments);
        notifyEventPublisher.publish(CREATED, lead.getId(), "lead-created:" + lead.getId(), submitterUserId,
                lead.getSubmittedAt(), eventContext(lead, submitterUserId));
        dispatchService.start(lead, reqVO.getSpecifiedSalesUserId(), submitterUserId);
        return response(leadMapper.selectById(lead.getId()), "created");
    }

    private LeadCreateRespVO findIdempotent(String key) {
        LeadDO lead = leadMapper.selectByIdempotencyKey(key);
        if (lead != null) return response(lead, "created");
        LeadActivationDO activation = activationMapper.selectByIdempotencyKey(key);
        if (activation == null) return null;
        LeadDO activatedLead = leadMapper.selectById(activation.getLeadId());
        return response(activatedLead, "activated");
    }

    private void validateContact(String mobile, String wechatId) {
        if (mobile == null && wechatId == null) throw exception(LEAD_CONTACT_REQUIRED);
        if (mobile != null && !ValidationUtils.isMobile(mobile)) throw exception(LEAD_MOBILE_INVALID);
    }

    private RegionSnapshot validateRegion(String provinceCode, String cityCode) {
        if (REGION_OTHER.equals(provinceCode)) {
            if (!REGION_OTHER.equals(cityCode)) throw exception(LEAD_REGION_INVALID);
            AreaRespDTO province = areaApi.getAreaByParentIdAndSelectionCode(Area.ID_CHINA, REGION_OTHER);
            if (!isEnabledArea(province, 2)) throw exception(LEAD_REGION_INVALID);
            AreaRespDTO city = areaApi.getAreaByParentIdAndSelectionCode(province.getId(), REGION_OTHER);
            if (!isEnabledArea(city, 3)) throw exception(LEAD_REGION_INVALID);
            return new RegionSnapshot(REGION_OTHER, province.getName(), REGION_OTHER, city.getName());
        }
        AreaRespDTO province;
        try {
            province = areaApi.getArea(Integer.valueOf(provinceCode));
        } catch (NumberFormatException ex) {
            throw exception(LEAD_REGION_INVALID);
        }
        if (!isEnabledArea(province, 2)) throw exception(LEAD_REGION_INVALID);
        if (REGION_OTHER.equals(cityCode)) {
            AreaRespDTO city = areaApi.getAreaByParentIdAndSelectionCode(province.getId(), REGION_OTHER);
            if (city != null) {
                if (!isEnabledArea(city, 3)) throw exception(LEAD_REGION_INVALID);
                return new RegionSnapshot(provinceCode, province.getName(), REGION_OTHER, city.getName());
            }
            if (Boolean.TRUE.equals(province.getLeafSelectable())) {
                return new RegionSnapshot(provinceCode, province.getName(), REGION_OTHER, null);
            }
            throw exception(LEAD_REGION_INVALID);
        }
        AreaRespDTO city;
        try {
            city = areaApi.getArea(Integer.valueOf(cityCode));
        } catch (NumberFormatException ex) {
            throw exception(LEAD_REGION_INVALID);
        }
        if (city == null || !Integer.valueOf(3).equals(city.getType())
                || !CommonStatusEnum.ENABLE.getStatus().equals(city.getStatus())
                || !Objects.equals(city.getParentId(), province.getId())) throw exception(LEAD_REGION_INVALID);
        return new RegionSnapshot(provinceCode, province.getName(), cityCode, city.getName());
    }

    private static boolean isEnabledArea(AreaRespDTO area, int type) {
        return area != null && Integer.valueOf(type).equals(area.getType())
                && CommonStatusEnum.ENABLE.getStatus().equals(area.getStatus());
    }

    private List<LeadProductSnapshot> validateProducts(List<LeadProductReqVO> requested) {
        Set<String> keys = new HashSet<>();
        if (requested.stream().filter(item -> Boolean.TRUE.equals(item.getPrimary())).count() != 1) {
            throw exception(LEAD_PRODUCT_REQUIRED);
        }
        List<LeadProductSnapshot> result = new ArrayList<>();
        for (LeadProductReqVO item : requested) {
            String key = Boolean.TRUE.equals(item.getSpuUnknown()) ? "UNKNOWN"
                    : item.effectiveSpuRef() + "|" + (Boolean.TRUE.equals(item.getSkuUnknown()) ? "UNKNOWN" : item.getSkuRef());
            if (!keys.add(key)) throw exception(LEAD_PRODUCT_DUPLICATE);
            result.add(productSkuService.validateLeadProduct(item.effectiveSpuRef(), Boolean.TRUE.equals(item.getSpuUnknown()),
                    item.getSkuRef(), Boolean.TRUE.equals(item.getSkuUnknown())));
        }
        return result;
    }

    private void validateDispatch(LeadCreateReqVO reqVO) {
        if (!Set.of(DISPATCH_AUTO, DISPATCH_SPECIFIED).contains(reqVO.getDispatchMode())) {
            throw exception(LEAD_DISPATCH_MODE_INVALID);
        }
        if (DISPATCH_SPECIFIED.equals(reqVO.getDispatchMode()) && reqVO.getSpecifiedSalesUserId() == null) {
            throw exception(LEAD_SPECIFIED_SALES_REQUIRED);
        }
    }

    private PersonDO createPerson(String name, String mobile, String wechatId) {
        PersonDO person = new PersonDO();
        person.setPersonNo("P" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        person.setName(name); person.setMobile(mobile); person.setWechatId(wechatId);
        person.setIdentityStatus("lead"); person.setFirstSeenAt(LocalDateTime.now());
        person.setLastSeenAt(person.getFirstSeenAt()); person.setVersion(0);
        personMapper.insert(person);
        return person;
    }

    private LeadDO createLead(PersonDO person, LeadCreateReqVO reqVO, String mobile, String wechatId,
                              RegionSnapshot region, Long submitterUserId) {
        LeadDO lead = new LeadDO();
        lead.setPersonId(person.getId()); lead.setSubmittedName(reqVO.getName().trim());
        lead.setSubmittedMobile(mobile); lead.setSubmittedWechatId(wechatId);
        lead.setSourceType(SOURCE_INTERNAL_NEW_MEDIA); lead.setSourceUserId(submitterUserId);
        lead.setSourceChannelId(reqVO.getSourceChannel());
        applyRegion(lead, region); lead.setLeadCategory(reqVO.getLeadCategory()); lead.setRemark(reqVO.getRemark());
        lead.setStatus(STATUS_SUBMITTED); lead.setAssignmentStatus(ASSIGNMENT_UNASSIGNED);
        lead.setDispatchMode(reqVO.getDispatchMode()); lead.setAssignmentAttemptCount(0);
        lead.setSubmissionIdempotencyKey(reqVO.getIdempotencyKey()); lead.setSubmittedAt(LocalDateTime.now());
        lead.setVersion(0); leadMapper.insert(lead);
        return lead;
    }

    private LeadActivationDO createActivation(LeadDO lead, PersonDO person, LeadCreateReqVO reqVO,
                                  List<LeadProductSnapshot> products, RegionSnapshot region,
                                  Long submitterUserId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", reqVO.getName()); snapshot.put("mobile", reqVO.getMobile());
        snapshot.put("wechatId", reqVO.getWechatId()); snapshot.put("region", region);
        List<Map<String, Object>> productSnapshots = new ArrayList<>();
        for (int i = 0; i < reqVO.getEffectiveProducts().size(); i++) {
            LeadProductReqVO item = reqVO.getEffectiveProducts().get(i); LeadProductSnapshot product = products.get(i);
            Map<String, Object> productMap = new LinkedHashMap<>();
            productMap.put("spuRef", product.productRef()); productMap.put("spuName", product.name());
            productMap.put("skuRef", product.skuRef()); productMap.put("skuName", product.skuName());
            productMap.put("price", product.price()); productMap.put("selectedAttrValues", product.selectedAttrValuesJson());
            productMap.put("categoryId", product.categoryId()); productMap.put("categoryName", product.categoryName());
            productMap.put("categoryPath", product.categoryPath());
            productMap.put("level1CategoryId", product.level1CategoryId()); productMap.put("level1CategoryName", product.level1CategoryName());
            productMap.put("level2CategoryId", product.level2CategoryId()); productMap.put("level2CategoryName", product.level2CategoryName());
            productMap.put("spuUnknown", product.spuUnknown()); productMap.put("skuUnknown", product.skuUnknown());
            productMap.put("primary", item.getPrimary()); productSnapshots.add(productMap);
        }
        snapshot.put("products", productSnapshots);
        snapshot.put("sourceChannel", reqVO.getSourceChannel()); snapshot.put("leadCategory", reqVO.getLeadCategory());
        snapshot.put("remark", reqVO.getRemark()); snapshot.put("attachments", reqVO.getAttachments());
        snapshot.put("dispatchMode", reqVO.getDispatchMode());
        LeadActivationDO activation = new LeadActivationDO();
        activation.setPersonId(person.getId()); activation.setLeadId(lead.getId());
        activation.setSourceType(SOURCE_INTERNAL_NEW_MEDIA); activation.setSourceUserId(submitterUserId);
        activation.setSourceChannelId(reqVO.getSourceChannel()); activation.setSubmissionSnapshot(JsonUtils.toJsonString(snapshot));
        activation.setNotificationTargets(lead.getOwnerUserId() == null ? null
                : JsonUtils.toJsonString(List.of(lead.getOwnerUserId())));
        activation.setActivatedAt(LocalDateTime.now()); activation.setIdempotencyKey(reqVO.getIdempotencyKey());
        activationMapper.insert(activation);
        return activation;
    }

    private Map<String, Object> eventContext(LeadDO lead, Long submitterUserId) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("submitterUserId", submitterUserId);
        context.put("ownerUserId", lead.getOwnerUserId());
        context.put("pendingSalesUserId", lead.getPendingAssigneeUserId());
        return context;
    }

    private void insertProducts(Long leadId, List<LeadProductReqVO> requested,
                                List<LeadProductSnapshot> snapshots) {
        for (int i = 0; i < requested.size(); i++) {
            LeadProductReqVO item = requested.get(i);
            LeadIntendedProductDO record = new LeadIntendedProductDO();
            LeadProductSnapshot snapshot = snapshots.get(i);
            record.setLeadId(leadId); record.setProductRef(snapshot.productRef());
            record.setProductNameSnapshot(snapshot.name());
            record.setSpuRef(snapshot.productRef()); record.setSpuNameSnapshot(snapshot.name());
            record.setSkuRef(snapshot.skuRef()); record.setSkuNameSnapshot(snapshot.skuName());
            record.setSelectedAttrValuesJson(snapshot.selectedAttrValuesJson()); record.setPriceSnapshot(snapshot.price());
            record.setSpuUnknown(snapshot.spuUnknown()); record.setSkuUnknown(snapshot.skuUnknown());
            record.setCategoryId(snapshot.categoryId()); record.setCategoryNameSnapshot(snapshot.categoryName());
            record.setCategoryPathSnapshot(JsonUtils.toJsonString(snapshot.categoryPath()));
            record.setLevel1CategoryId(snapshot.level1CategoryId());
            record.setLevel1CategoryNameSnapshot(snapshot.level1CategoryName());
            record.setLevel2CategoryId(snapshot.level2CategoryId());
            record.setLevel2CategoryNameSnapshot(snapshot.level2CategoryName());
            record.setIsPrimary(item.getPrimary()); record.setSort(i);
            intendedProductMapper.insert(record);
        }
    }

    private void insertAttachments(Long leadId, List<LeadAttachmentReqVO> requested,
                                   Map<Long, FileInfoRespDTO> files) {
        for (int i = 0; i < requested.size(); i++) {
            LeadAttachmentReqVO item = requested.get(i);
            FileInfoRespDTO file = files.get(item.getInfraFileId());
            LeadAttachmentDO record = new LeadAttachmentDO();
            record.setLeadId(leadId); record.setInfraFileId(file.getId()); record.setFileUrl(null);
            record.setOriginalName(file.getName()); record.setContentType(file.getType());
            record.setFileSize(file.getSize()); record.setSort(i);
            attachmentMapper.insert(record);
        }
    }

    private static void applyRegion(LeadDO lead, RegionSnapshot region) {
        lead.setProvinceCode(region.provinceCode()); lead.setProvinceName(region.provinceName());
        lead.setCityCode(region.cityCode()); lead.setCityName(region.cityName());
    }

    private static LeadCreateRespVO response(LeadDO lead, String outcome) {
        return new LeadCreateRespVO(lead.getId(), outcome, lead.getAssignmentStatus(), lead.getPendingAssigneeUserId());
    }

    public record RegionSnapshot(String provinceCode, String provinceName, String cityCode, String cityName) {}
}
