package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.hutool.core.util.DesensitizedUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.validation.ValidationUtils;
import cn.iocoder.yudao.framework.ip.core.Area;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.ip.AreaApi;
import cn.iocoder.yudao.module.system.api.ip.dto.AreaRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class LeadSubmissionServiceImpl implements LeadSubmissionService {

    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    @Resource private PersonMapper personMapper;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;
    @Resource private LeadMapper leadMapper;
    @Resource private LeadActivationMapper activationMapper;
    @Resource private LeadDuplicateReviewMapper duplicateReviewMapper;
    @Resource private LeadIntendedProductMapper intendedProductMapper;
    @Resource private LeadAttachmentMapper attachmentMapper;
    @Resource private AreaApi areaApi;
    @Resource private DictDataApi dictDataApi;
    @Resource private ZsjosProductSkuService productSkuService;
    @Resource private LeadDispatchService dispatchService;
    @Resource private LeadAttachmentService attachmentService;
    @Resource private LeadNotifyEventPublisher notifyEventPublisher;
    @Resource private LeadDuplicateMatcher duplicateMatcher;
    @Resource private LeadSubmissionIdentityService identityService;
    @Resource private LeadFollowUpRuleService followUpRuleService;
    @Lazy @Resource private LeadDuplicateReviewService duplicateReviewService;
    @Resource private LeadNumberService leadNumberService;
    @Resource private PersonIdentityWriteService personIdentityWriteService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeadCreateRespVO create(LeadCreateReqVO reqVO, Long submitterUserId) {
        LeadSubmissionIdentityService.Resolution identity = identityService.requireOrdinarySubmitter(submitterUserId);
        validateOrdinaryDispatch(reqVO, submitterUserId, identity.identity());
        return create(reqVO, submitterUserId, submitterUserId, identity, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeadCreateRespVO createForPartner(LeadCreateReqVO reqVO, Long accountId, Long partnerId) {
        reqVO.setDispatchMode(DISPATCH_AUTO);
        reqVO.setSpecifiedSalesUserId(null);
        LeadSubmissionIdentityService.Resolution identity = new LeadSubmissionIdentityService.Resolution(
                LeadSubmissionIdentityService.Identity.PARTNER, partnerId);
        validateOrdinaryDispatch(reqVO, null, identity.identity());
        return create(reqVO, accountId, null, identity, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeadCreateRespVO createSelfSourced(LeadCreateReqVO reqVO, Long salesUserId) {
        identityService.requireSales(salesUserId);
        if (reqVO.getNewMediaProviderUserId() != null) {
            identityService.requireNewMediaProvider(reqVO.getNewMediaProviderUserId());
        }
        reqVO.setDispatchMode(DISPATCH_SELF);
        reqVO.setSpecifiedSalesUserId(salesUserId);
        return create(reqVO, salesUserId, selfSourcedSourceUserId(reqVO.getNewMediaProviderUserId(), salesUserId),
                new LeadSubmissionIdentityService.Resolution(LeadSubmissionIdentityService.Identity.SALES, null), true);
    }

    @Override
    public List<LeadAssignmentUserRespVO> getNewMediaProviders() {
        List<AdminUserRespDTO> users = identityService.getEnabledNewMediaProviders();
        Set<Long> deptIds = users.stream().map(AdminUserRespDTO::getDeptId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, DeptRespDTO> departments = (deptIds.isEmpty() ? List.<DeptRespDTO>of()
                : deptApi.getDeptList(deptIds)).stream()
                .collect(Collectors.toMap(DeptRespDTO::getId, Function.identity()));
        return users.stream().map(user -> {
            LeadAssignmentUserRespVO result = new LeadAssignmentUserRespVO();
            result.setId(user.getId()); result.setNickname(user.getNickname());
            result.setMaskedMobile(user.getMobile() == null ? null : DesensitizedUtil.mobilePhone(user.getMobile()));
            result.setDeptId(user.getDeptId()); result.setAvatar(user.getAvatar()); result.setStatus(user.getStatus());
            DeptRespDTO dept = departments.get(user.getDeptId());
            result.setDeptName(dept == null ? null : dept.getName());
            return result;
        }).toList();
    }

    private LeadCreateRespVO create(LeadCreateReqVO reqVO, Long actorUserId, Long sourceUserId,
                                    LeadSubmissionIdentityService.Resolution identity, boolean selfSourced) {
        LeadCreateRespVO idempotent = findIdempotent(reqVO.getIdempotencyKey(), identity);
        if (idempotent != null) return idempotent;

        String mobile = StrUtil.trimToNull(reqVO.getMobile());
        String wechatId = StrUtil.trimToNull(reqVO.getWechatId());
        validateContact(mobile, wechatId);
        RegionSnapshot region = validateRegion(reqVO.getProvinceCode(), reqVO.getCityCode());
        dictDataApi.validateDictDataList(DICT_SOURCE_CHANNEL, List.of(reqVO.getSourceChannel()));
        dictDataApi.validateDictDataList(DICT_CATEGORY, List.of(reqVO.getLeadCategory()));
        List<LeadProductSnapshot> products = validateProducts(reqVO.getEffectiveProducts());
        Map<Long, FileInfoRespDTO> attachments = identity.identity() == LeadSubmissionIdentityService.Identity.PARTNER
                ? attachmentService.validatePartnerReferences(reqVO.getAttachments(), actorUserId)
                : attachmentService.validateReferences(reqVO.getAttachments(), actorUserId);
        validateDispatch(reqVO, selfSourced);

        LeadDuplicateReviewDO existingReview = duplicateReviewMapper.selectByIdempotencyKey(reqVO.getIdempotencyKey());
        if (existingReview != null) {
            requireSamePartnerIdentity(existingReview.getSubmissionPartnerId(), identity);
            return duplicateReviewResponse(existingReview);
        }
        LeadDuplicateMatcher.MatchResult match = duplicateMatcher.match(reqVO, null);
        if (match.hasMatches()) {
            LeadDuplicateReviewDO review = new LeadDuplicateReviewDO();
            review.setStatus("pending");
            review.setSubmitterUserId(actorUserId);
            review.setSubmissionSourceType(sourceType(identity));
            review.setSubmissionPartnerId(identity.partnerId());
            review.setSubmissionSnapshot(JsonUtils.toJsonString(reqVO));
            review.setMatchRules(JsonUtils.toJsonString(match.candidates().stream()
                    .flatMap(candidate -> candidate.rules().stream()).distinct().toList()));
            review.setCandidateSnapshot(JsonUtils.toJsonString(match.candidates()));
            review.setSubmissionIdempotencyKey(reqVO.getIdempotencyKey());
            review.setVersion(0);
            duplicateReviewMapper.insert(review);
            if (Boolean.TRUE.equals(followUpRuleService.requireEnabledRule().getDuplicateAutoResolutionEnabled())) {
                Long matchedLeadId = newestMatchedLeadId(match.candidates());
                if (matchedLeadId != null) {
                    return duplicateReviewService.resolveAutomatically(review.getId(), matchedLeadId, actorUserId);
                }
            }
            return LeadCreateRespVO.reviewPending(review.getId());
        }

        return createApproved(reqVO, actorUserId, sourceUserId, null, products, region, attachments, identity);
    }

    @Transactional(rollbackFor = Exception.class)
    public LeadCreateRespVO createApproved(LeadCreateReqVO reqVO, Long submitterUserId, Long reusePersonId) {
        return createApprovedFromReview(reqVO, submitterUserId, reusePersonId, SOURCE_INTERNAL_NEW_MEDIA, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public LeadCreateRespVO createApprovedFromReview(LeadCreateReqVO reqVO, Long submitterUserId, Long reusePersonId,
                                                      String sourceType, Long partnerId) {
        String mobile = StrUtil.trimToNull(reqVO.getMobile());
        String wechatId = StrUtil.trimToNull(reqVO.getWechatId());
        validateContact(mobile, wechatId);
        RegionSnapshot region = validateRegion(reqVO.getProvinceCode(), reqVO.getCityCode());
        dictDataApi.validateDictDataList(DICT_SOURCE_CHANNEL, List.of(reqVO.getSourceChannel()));
        dictDataApi.validateDictDataList(DICT_CATEGORY, List.of(reqVO.getLeadCategory()));
        List<LeadProductSnapshot> products = validateProducts(reqVO.getEffectiveProducts());
        LeadSubmissionIdentityService.Resolution identity = identityService.resolveHistoricalSubmission(
                submitterUserId, sourceType, partnerId);
        Map<Long, FileInfoRespDTO> attachments = identity.identity() == LeadSubmissionIdentityService.Identity.PARTNER
                ? attachmentService.validatePartnerReferences(reqVO.getAttachments(), submitterUserId)
                : attachmentService.validateReferences(reqVO.getAttachments(), submitterUserId);
        boolean selfSourced = identity.identity() == LeadSubmissionIdentityService.Identity.SALES;
        if (!selfSourced) {
            validateOrdinaryDispatch(reqVO, submitterUserId, identity.identity());
        }
        validateDispatch(reqVO, selfSourced);
        Long sourceUserId = selfSourced
                ? selfSourcedSourceUserId(reqVO.getNewMediaProviderUserId(), submitterUserId)
                : submitterUserId;
        return createApproved(reqVO, submitterUserId, sourceUserId, reusePersonId, products, region, attachments, identity);
    }

    private LeadCreateRespVO duplicateReviewResponse(LeadDuplicateReviewDO review) {
        if (!"completed".equals(review.getStatus()) || review.getMatchedLeadId() == null) {
            return LeadCreateRespVO.reviewPending(review.getId());
        }
        LeadDO lead = leadMapper.selectById(review.getMatchedLeadId());
        if (lead == null) return LeadCreateRespVO.reviewPending(review.getId());
        return "reactivate_lead".equals(review.getResultType())
                ? LeadCreateRespVO.activated(lead.getId(), lead.getLeadNo(), lead.getAssignmentStatus())
                : LeadCreateRespVO.duplicateAutoClosed(lead.getId(), lead.getLeadNo(), lead.getStatus());
    }

    private Long newestMatchedLeadId(List<LeadDuplicateMatcher.Candidate> candidates) {
        List<Long> leadIds = candidates.stream().map(LeadDuplicateMatcher.Candidate::leadId)
                .filter(Objects::nonNull).distinct().toList();
        if (leadIds.isEmpty()) return null;
        return leadMapper.selectBatchIds(leadIds).stream()
                .max(Comparator.comparing(LeadDO::getSubmittedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(LeadDO::getId))
                .map(LeadDO::getId).orElse(null);
    }

    private LeadCreateRespVO createApproved(LeadCreateReqVO reqVO, Long actorUserId, Long sourceUserId, Long reusePersonId,
                                            List<LeadProductSnapshot> products, RegionSnapshot region,
                                            Map<Long, FileInfoRespDTO> attachments,
                                            LeadSubmissionIdentityService.Resolution identity) {
        String mobile = StrUtil.trimToNull(reqVO.getMobile());
        String wechatId = StrUtil.trimToNull(reqVO.getWechatId());
        PersonDO person = reusePersonId == null
                ? personIdentityWriteService.createNew(reqVO.getName().trim(), mobile, wechatId, "lead")
                : personIdentityWriteService.update(reusePersonId, reqVO.getName().trim(), mobile, wechatId);
        if (person == null) throw exception(LEAD_DUPLICATE_REVIEW_RESULT_INVALID);
        if (leadMapper.selectLatestByPersonId(person.getId()) != null) {
            throw exception(LEAD_DUPLICATE_REVIEW_RESULT_INVALID);
        }
        LocalDateTime submittedAt = LocalDateTime.now(BEIJING);
        LeadDO lead = createLead(person, reqVO, mobile, wechatId, region, sourceUserId, identity, submittedAt);
        insertProducts(lead.getId(), reqVO.getEffectiveProducts(), products);
        insertAttachments(lead.getId(), reqVO.getAttachments(), attachments);
        notifyEventPublisher.publish(CREATED, lead.getId(), "lead-created:" + lead.getId(), actorUserId,
                lead.getSubmittedAt(), eventContext(lead, actorUserId));
        dispatchService.start(lead, reqVO.getSpecifiedSalesUserId(), actorUserId);
        return response(leadMapper.selectById(lead.getId()), "created");
    }

    private LeadCreateRespVO findIdempotent(String key, LeadSubmissionIdentityService.Resolution identity) {
        LeadDO lead = leadMapper.selectByIdempotencyKey(key);
        if (lead != null) {
            requireSamePartnerIdentity(lead.getPartnerId(), identity);
            return response(lead, "created");
        }
        LeadActivationDO activation = activationMapper.selectByIdempotencyKey(key);
        if (activation == null) return null;
        requireSamePartnerIdentity(activation.getPartnerId(), identity);
        LeadDO activatedLead = leadMapper.selectById(activation.getLeadId());
        return response(activatedLead, "activated");
    }

    private void requireSamePartnerIdentity(Long existingPartnerId,
                                            LeadSubmissionIdentityService.Resolution identity) {
        if (!Objects.equals(existingPartnerId, identity.partnerId())) {
            throw exception(LEAD_SUBMISSION_DUPLICATE);
        }
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

    private void validateDispatch(LeadCreateReqVO reqVO, boolean selfSourced) {
        if (!(selfSourced ? Set.of(DISPATCH_SELF) : Set.of(DISPATCH_AUTO, DISPATCH_SPECIFIED)).contains(reqVO.getDispatchMode())) {
            throw exception(LEAD_DISPATCH_MODE_INVALID);
        }
        if (!selfSourced && reqVO.getNewMediaProviderUserId() != null) {
            throw exception(LEAD_DISPATCH_MODE_INVALID);
        }
        if (DISPATCH_SPECIFIED.equals(reqVO.getDispatchMode()) && reqVO.getSpecifiedSalesUserId() == null) {
            throw exception(LEAD_SPECIFIED_SALES_REQUIRED);
        }
    }

    private void validateOrdinaryDispatch(LeadCreateReqVO reqVO, Long userId,
                                          LeadSubmissionIdentityService.Identity identity) {
        if (identity == LeadSubmissionIdentityService.Identity.PARTNER
                && !DISPATCH_AUTO.equals(reqVO.getDispatchMode())) throw exception(LEAD_DISPATCH_MODE_INVALID);
        if (!DISPATCH_SPECIFIED.equals(reqVO.getDispatchMode())) return;
        boolean allowed = identity == LeadSubmissionIdentityService.Identity.NEW_MEDIA_MANAGER
                ? dispatchService.getEligibleSalesUsers().stream().anyMatch(user -> Objects.equals(user.getId(), reqVO.getSpecifiedSalesUserId()))
                : dispatchService.getAssignableSalesUsers(userId).stream().anyMatch(user -> Objects.equals(user.getId(), reqVO.getSpecifiedSalesUserId()));
        if (!allowed) throw exception(LEAD_SPECIFIED_SALES_REQUIRED);
    }

    private LeadDO createLead(PersonDO person, LeadCreateReqVO reqVO, String mobile, String wechatId,
                              RegionSnapshot region, Long sourceUserId,
                              LeadSubmissionIdentityService.Resolution identity, LocalDateTime submittedAt) {
        LeadDO lead = new LeadDO();
        lead.setLeadNo(leadNumberService.next(submittedAt));
        lead.setPersonId(person.getId()); lead.setSubmittedName(reqVO.getName().trim());
        lead.setSubmittedMobile(mobile); lead.setSubmittedWechatId(wechatId);
        lead.setSourceType(sourceType(identity));
        lead.setSourceUserId(sourceUserId); lead.setPartnerId(identity.partnerId());
        if (identity.identity() == LeadSubmissionIdentityService.Identity.SALES) {
            lead.setSourceProviderUserId(reqVO.getNewMediaProviderUserId());
            lead.setSourceProviderRecorded(true);
        }
        AdminUserRespDTO submitter = sourceUserId == null ? null : adminUserApi.getUser(sourceUserId);
        lead.setSourceDeptId(submitter == null ? null : submitter.getDeptId());
        lead.setSourceChannelId(reqVO.getSourceChannel());
        applyRegion(lead, region); lead.setLeadCategory(reqVO.getLeadCategory()); lead.setRemark(reqVO.getRemark());
        lead.setStatus(STATUS_SUBMITTED); lead.setAssignmentStatus(ASSIGNMENT_UNASSIGNED);
        lead.setDispatchMode(reqVO.getDispatchMode()); lead.setAssignmentAttemptCount(0);
        lead.setSubmissionIdempotencyKey(reqVO.getIdempotencyKey()); lead.setSubmittedAt(submittedAt);
        lead.setVersion(0); leadMapper.insert(lead);
        return lead;
    }

    private static String sourceType(LeadSubmissionIdentityService.Resolution identity) {
        return identity.identity() == LeadSubmissionIdentityService.Identity.PARTNER ? SOURCE_PARTNER
                : identity.identity() == LeadSubmissionIdentityService.Identity.SALES ? SOURCE_SALES_SELF
                : SOURCE_INTERNAL_NEW_MEDIA;
    }

    static Long selfSourcedSourceUserId(Long providerUserId, Long salesUserId) {
        return Objects.requireNonNullElse(providerUserId, salesUserId);
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

    private Map<String, Object> eventContext(LeadDO lead, Long actorUserId) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("submitterUserId", lead.getSourceUserId());
        context.put("ownerUserId", lead.getOwnerUserId());
        context.put("pendingSalesUserId", lead.getPendingAssigneeUserId());
        if (SOURCE_SALES_SELF.equals(lead.getSourceType())
                && !Objects.equals(lead.getSourceUserId(), actorUserId)) {
            context.put("newMediaProviderUserId", lead.getSourceUserId());
        }
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
        LeadCreateRespVO response = new LeadCreateRespVO(
                lead.getId(), outcome, lead.getAssignmentStatus(), lead.getPendingAssigneeUserId());
        response.setLeadNo(lead.getLeadNo());
        return response;
    }

    public record RegionSnapshot(String provinceCode, String provinceName, String cityCode, String cityName) {}
}
