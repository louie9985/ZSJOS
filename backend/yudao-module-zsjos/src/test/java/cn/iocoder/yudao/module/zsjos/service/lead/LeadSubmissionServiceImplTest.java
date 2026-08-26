package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.ip.AreaApi;
import cn.iocoder.yudao.module.system.api.ip.dto.AreaRespDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadCreateRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadProductReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDuplicateReviewDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadFollowUpRuleDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerAccountMapper;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductCatalogPort;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_CONTACT_REQUIRED;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_MOBILE_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_REGION_INVALID;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_SUBMISSION_DUPLICATE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LeadSubmissionServiceImplTest {
    @InjectMocks private LeadSubmissionServiceImpl service;
    @Mock private PersonMapper personMapper;
    @Mock private LeadMapper leadMapper;
    @Mock private LeadActivationMapper activationMapper;
    @Mock private LeadIntendedProductMapper intendedProductMapper;
    @Mock private LeadAttachmentMapper attachmentMapper;
    @Mock private AreaApi areaApi;
    @Mock private DictDataApi dictDataApi;
    @Mock private DeptApi deptApi;
    @Mock private LeadProductCatalogPort productCatalogPort;
    @Mock private LeadDispatchService dispatchService;
    @Mock private LeadAttachmentService attachmentService;
    @Mock private LeadSubmissionIdentityService identityService;
    @Mock private LeadDuplicateReviewMapper duplicateReviewMapper;
    @Mock private LeadDuplicateMatcher duplicateMatcher;
    @Mock private LeadFollowUpRuleService followUpRuleService;
    @Mock private LeadDuplicateReviewService duplicateReviewService;
    @Mock private ZsjosProductSkuService productSkuService;
    @Mock private LeadCategorySnapshotService categorySnapshotService;
    @Mock private PartnerAccountMapper partnerAccountMapper;
    @Mock private cn.iocoder.yudao.module.zsjos.service.personnel.PartnerOwnershipService partnerOwnershipService;

    @org.junit.jupiter.api.BeforeEach
    void setUpIdentity() {
        org.mockito.Mockito.lenient().when(identityService.requireOrdinarySubmitter(1L)).thenReturn(
                new LeadSubmissionIdentityService.Resolution(LeadSubmissionIdentityService.Identity.NEW_MEDIA, null));
        org.mockito.Mockito.lenient().when(categorySnapshotService.requireEnabled(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new LeadCategorySnapshotService.Selection("test", "提交时分类"));
    }

    @Test
    void createRejectsMissingMobileAndWechat() {
        LeadCreateReqVO req = baseRequest();

        ServiceException error = assertThrows(ServiceException.class, () -> service.create(req, 1L));

        assertEquals(LEAD_CONTACT_REQUIRED.getCode(), error.getCode());
    }

    @Test
    void createRejectsMalformedMobileBeforeRemoteValidation() {
        LeadCreateReqVO req = baseRequest();
        req.setMobile("12345");

        ServiceException error = assertThrows(ServiceException.class, () -> service.create(req, 1L));

        assertEquals(LEAD_MOBILE_INVALID.getCode(), error.getCode());
    }

    @Test
    void partnerCannotReplayAnotherPartnersIdempotencyKey() {
        LeadCreateReqVO req = baseRequest();
        when(partnerAccountMapper.selectById(20L)).thenReturn(new PartnerAccountDO().setId(20L).setPartnerId(10L)
                .setStatus(CommonStatusEnum.ENABLE.getStatus()));
        LeadDO existing = new LeadDO().setId(100L).setPartnerId(99L)
                .setSubmissionIdempotencyKey(req.getIdempotencyKey());
        when(leadMapper.selectByIdempotencyKey(req.getIdempotencyKey())).thenReturn(existing);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.createForPartner(req, 20L, 10L));

        assertEquals(LEAD_SUBMISSION_DUPLICATE.getCode(), error.getCode());
        verify(activationMapper, never()).selectByIdempotencyKey(any());
    }

    @Test
    void partnerSubmissionRejectsAccountFromAnotherPartner() {
        when(partnerAccountMapper.selectById(20L)).thenReturn(new PartnerAccountDO().setId(20L).setPartnerId(99L)
                .setStatus(CommonStatusEnum.ENABLE.getStatus()));

        ServiceException error = assertThrows(ServiceException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "validatePartnerSubmissionAccount", 20L, 10L));

        assertEquals(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_SUBMITTER_IDENTITY_INVALID.getCode(), error.getCode());
    }

    @Test
    void partnerSubmissionRejectsDisabledAccount() {
        when(partnerAccountMapper.selectById(20L)).thenReturn(new PartnerAccountDO().setId(20L).setPartnerId(10L)
                .setStatus(CommonStatusEnum.DISABLE.getStatus()));

        ServiceException error = assertThrows(ServiceException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "validatePartnerSubmissionAccount", 20L, 10L));

        assertEquals(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.PARTNER_ACCOUNT_DISABLED.getCode(), error.getCode());
    }

    @Test
    void validateRegionAcceptsEnabledProvinceAndCity() {
        when(areaApi.getArea(110000)).thenReturn(area(110000, 2, 1, 0));
        when(areaApi.getArea(110100)).thenReturn(area(110100, 3, 110000, 0));

        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service, "validateRegion", "110000", "110100"));
    }

    @Test
    void validateRegionRejectsDisabledOrCrossProvinceCity() {
        when(areaApi.getArea(110000)).thenReturn(area(110000, 2, 1, 0));
        when(areaApi.getArea(110100)).thenReturn(area(110100, 3, 120000, 0));
        ServiceException crossProvince = assertThrows(ServiceException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "validateRegion", "110000", "110100"));
        assertEquals(LEAD_REGION_INVALID.getCode(), crossProvince.getCode());

        when(areaApi.getArea(110000)).thenReturn(area(110000, 2, 1, 1));
        ServiceException disabled = assertThrows(ServiceException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "validateRegion", "110000", "OTHER"));
        assertEquals(LEAD_REGION_INVALID.getCode(), disabled.getCode());
    }

    @Test
    void validateRegionAcceptsConfiguredProvinceOther() {
        AreaRespDTO province = area(110000, 2, 1, 0);
        AreaRespDTO otherCity = area(900000011, 3, 110000, 0);
        otherCity.setName("其他地区");
        otherCity.setSelectionCode("OTHER");
        when(areaApi.getArea(110000)).thenReturn(province);
        when(areaApi.getAreaByParentIdAndSelectionCode(110000, "OTHER")).thenReturn(otherCity);

        LeadSubmissionServiceImpl.RegionSnapshot snapshot = ReflectionTestUtils.invokeMethod(
                service, "validateRegion", "110000", "OTHER");

        assertEquals("其他地区", snapshot.cityName());
    }

    @Test
    void validateRegionAcceptsConfiguredDirectProvinceLeaf() {
        AreaRespDTO hongKong = area(810000, 2, 1, 0);
        hongKong.setLeafSelectable(true);
        when(areaApi.getArea(810000)).thenReturn(hongKong);
        when(areaApi.getAreaByParentIdAndSelectionCode(810000, "OTHER")).thenReturn(null);

        LeadSubmissionServiceImpl.RegionSnapshot snapshot = ReflectionTestUtils.invokeMethod(
                service, "validateRegion", "810000", "OTHER");

        assertEquals("810000", snapshot.provinceCode());
        assertEquals("OTHER", snapshot.cityCode());
        assertNull(snapshot.cityName());
    }

    @Test
    void validateRegionRejectsDisabledConfiguredOther() {
        when(areaApi.getArea(110000)).thenReturn(area(110000, 2, 1, 0));
        when(areaApi.getAreaByParentIdAndSelectionCode(110000, "OTHER"))
                .thenReturn(area(900000011, 3, 110000, 1));

        ServiceException error = assertThrows(ServiceException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "validateRegion", "110000", "OTHER"));

        assertEquals(LEAD_REGION_INVALID.getCode(), error.getCode());
    }

    @Test
    void validateRegionAcceptsConfiguredOtherProvinceAndCity() {
        AreaRespDTO otherProvince = area(990000000, 2, 1, 0);
        otherProvince.setName("其他省份");
        otherProvince.setSelectionCode("OTHER");
        AreaRespDTO otherCity = area(990000001, 3, 990000000, 0);
        otherCity.setName("其他城市");
        otherCity.setSelectionCode("OTHER");
        when(areaApi.getAreaByParentIdAndSelectionCode(1, "OTHER")).thenReturn(otherProvince);
        when(areaApi.getAreaByParentIdAndSelectionCode(990000000, "OTHER")).thenReturn(otherCity);

        LeadSubmissionServiceImpl.RegionSnapshot snapshot = ReflectionTestUtils.invokeMethod(
                service, "validateRegion", "OTHER", "OTHER");

        assertEquals("其他省份", snapshot.provinceName());
        assertEquals("其他城市", snapshot.cityName());
    }

    @Test
    void duplicateMatchEntersManualReviewWhenAutoResolutionIsDisabled() {
        LeadCreateReqVO req = validDuplicateRequest();
        LeadDuplicateMatcher.Candidate candidate = candidate(10L, "valid");
        when(duplicateMatcher.match(req, null)).thenReturn(
                new LeadDuplicateMatcher.MatchResult(candidate, List.of(candidate)));
        when(followUpRuleService.requireEnabledRule()).thenReturn(rule(false));
        prepareDuplicateValidation(req);
        assignReviewId();

        LeadCreateRespVO result = service.create(req, 1L);

        assertEquals("review_pending", result.getOutcome());
        assertEquals(99L, result.getReviewId());
        verify(duplicateReviewMapper).insert(org.mockito.ArgumentMatchers.argThat(
                (LeadDuplicateReviewDO review) -> "提交时分类".equals(review.getLeadCategoryLabelSnapshot())));
        verify(duplicateReviewService, never()).resolveAutomatically(any(), any(), any());
    }

    @Test
    void autoResolutionSelectsNewestMatchedLeadThenHighestId() {
        LeadCreateReqVO req = validDuplicateRequest();
        LeadDuplicateMatcher.Candidate first = candidate(10L, "invalid");
        LeadDuplicateMatcher.Candidate second = candidate(20L, "won");
        LeadDuplicateMatcher.Candidate third = candidate(19L, "closed");
        when(duplicateMatcher.match(req, null)).thenReturn(
                new LeadDuplicateMatcher.MatchResult(null, List.of(first, second, third)));
        when(followUpRuleService.requireEnabledRule()).thenReturn(rule(true));
        LeadDO older = lead(10L, LocalDateTime.of(2026, 8, 1, 10, 0));
        LeadDO newerLowerId = lead(19L, LocalDateTime.of(2026, 8, 2, 10, 0));
        LeadDO newerHigherId = lead(20L, LocalDateTime.of(2026, 8, 2, 10, 0));
        when(leadMapper.selectBatchIds(List.of(10L, 20L, 19L))).thenReturn(List.of(older, newerLowerId, newerHigherId));
        LeadCreateRespVO resolved = LeadCreateRespVO.duplicateAutoClosed(20L, "L20", "won");
        when(duplicateReviewService.resolveAutomatically(99L, 20L, 1L)).thenReturn(resolved);
        prepareDuplicateValidation(req);
        assignReviewId();

        LeadCreateRespVO result = service.create(req, 1L);

        assertEquals("duplicate_auto_closed", result.getOutcome());
        verify(duplicateReviewService).resolveAutomatically(99L, 20L, 1L);
    }

    @Test
    void selfSourcedSourceFallsBackToSubmittingSales() {
        assertEquals(10L, LeadSubmissionServiceImpl.selfSourcedSourceUserId(null, 10L));
        assertEquals(20L, LeadSubmissionServiceImpl.selfSourcedSourceUserId(20L, 10L));
    }

    @Test
    void leadCreatedContextIncludesOnlyExplicitSelfSourcedProvider() {
        LeadDO linked = new LeadDO().setSourceType("sales_self_sourced").setSourceUserId(20L);
        Map<String, Object> linkedContext = ReflectionTestUtils.invokeMethod(service, "eventContext", linked, 10L);
        assertEquals(20L, linkedContext.get("newMediaProviderUserId"));

        LeadDO fallback = new LeadDO().setSourceType("sales_self_sourced").setSourceUserId(10L);
        Map<String, Object> fallbackContext = ReflectionTestUtils.invokeMethod(service, "eventContext", fallback, 10L);
        assertFalse(fallbackContext.containsKey("newMediaProviderUserId"));

        LeadDO newMediaSubmission = new LeadDO().setSourceType("internal_new_media").setSourceUserId(20L);
        Map<String, Object> newMediaContext = ReflectionTestUtils.invokeMethod(
                service, "eventContext", newMediaSubmission, 20L);
        assertFalse(newMediaContext.containsKey("newMediaProviderUserId"));
    }

    @Test
    void newMediaProvidersMaskMobileAndLoadDepartmentsInBatch() {
        AdminUserRespDTO first = user(10L, 100L, "13800138000");
        AdminUserRespDTO second = user(20L, 100L, "13900139000");
        DeptRespDTO department = new DeptRespDTO();
        department.setId(100L); department.setName("新媒体中心");
        when(identityService.getEnabledNewMediaProviders()).thenReturn(List.of(first, second));
        when(deptApi.getDeptList(Set.of(100L))).thenReturn(List.of(department));

        List<LeadAssignmentUserRespVO> result = service.getNewMediaProviders();

        assertEquals(2, result.size());
        assertEquals("新媒体中心", result.get(0).getDeptName());
        assertNotEquals(first.getMobile(), result.get(0).getMaskedMobile());
        assertTrue(result.get(0).getMaskedMobile().contains("****"));
        verify(deptApi).getDeptList(Set.of(100L));
        verify(deptApi, never()).getDept(any());
    }

    private static AreaRespDTO area(int id, int type, int parentId, int status) {
        AreaRespDTO area = new AreaRespDTO();
        area.setId(id);
        area.setName(String.valueOf(id));
        area.setType(type);
        area.setParentId(parentId);
        area.setSelectionCode(String.valueOf(id));
        area.setLeafSelectable(false);
        area.setStatus(status == 0 ? CommonStatusEnum.ENABLE.getStatus() : CommonStatusEnum.DISABLE.getStatus());
        return area;
    }

    private static LeadCreateReqVO baseRequest() {
        LeadCreateReqVO req = new LeadCreateReqVO();
        req.setName("测试客户");
        req.setProvinceCode("OTHER");
        req.setCityCode("OTHER");
        req.setSourceChannel("test");
        req.setLeadCategory("test");
        req.setDispatchMode("auto");
        req.setIdempotencyKey("test-idempotency-key");
        return req;
    }

    private LeadCreateReqVO validDuplicateRequest() {
        LeadCreateReqVO req = baseRequest();
        req.setMobile("13800138000");
        LeadProductReqVO product = new LeadProductReqVO();
        product.setSpuUnknown(true); product.setSkuUnknown(true); product.setPrimary(true);
        req.setProducts(List.of(product));
        return req;
    }

    private void prepareDuplicateValidation(LeadCreateReqVO req) {
        AreaRespDTO otherProvince = area(990000000, 2, 1, 0);
        otherProvince.setName("其他省份"); otherProvince.setSelectionCode("OTHER");
        AreaRespDTO otherCity = area(990000001, 3, 990000000, 0);
        otherCity.setName("其他城市"); otherCity.setSelectionCode("OTHER");
        when(areaApi.getAreaByParentIdAndSelectionCode(1, "OTHER")).thenReturn(otherProvince);
        when(areaApi.getAreaByParentIdAndSelectionCode(990000000, "OTHER")).thenReturn(otherCity);
        when(productSkuService.validateLeadProduct(null, true, null, true)).thenReturn(LeadProductSnapshot.unknown());
        when(attachmentService.validateReferences(req.getAttachments(), 1L)).thenReturn(Map.of());
    }

    private void assignReviewId() {
        doAnswer(invocation -> {
            invocation.getArgument(0, LeadDuplicateReviewDO.class).setId(99L);
            return 1;
        }).when(duplicateReviewMapper).insert(any(LeadDuplicateReviewDO.class));
    }

    private static LeadDuplicateMatcher.Candidate candidate(Long leadId, String status) {
        return new LeadDuplicateMatcher.Candidate(leadId + 100L, leadId, "L" + leadId, "客户",
                status, "owned", new HashSet<>(List.of(LeadDuplicateMatcher.SAME_MOBILE)));
    }

    private static LeadFollowUpRuleDO rule(boolean auto) {
        LeadFollowUpRuleDO rule = new LeadFollowUpRuleDO();
        rule.setDuplicateAutoResolutionEnabled(auto);
        return rule;
    }

    private static LeadDO lead(Long id, LocalDateTime submittedAt) {
        LeadDO lead = new LeadDO();
        lead.setId(id); lead.setSubmittedAt(submittedAt);
        return lead;
    }

    private static AdminUserRespDTO user(Long id, Long deptId, String mobile) {
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(id); user.setDeptId(deptId); user.setMobile(mobile); user.setNickname("用户" + id);
        user.setStatus(CommonStatusEnum.ENABLE.getStatus());
        return user;
    }
}
