package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadInboxFilterProfileRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter.LeadInboxFilterConfigVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAttachmentDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAttachmentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadIntendedProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.PERMISSION_QUERY_OWNED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.PERMISSION_QUERY_SUBMITTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadManagementServiceImplTest {

    @InjectMocks
    private LeadManagementServiceImpl service;
    @Mock
    private LeadMapper leadMapper;
    @Mock
    private LeadIntendedProductMapper intendedProductMapper;
    @Mock
    private LeadAttachmentMapper attachmentMapper;
    @Mock
    private AdminUserApi adminUserApi;
    @Mock
    private SecurityFrameworkService securityFrameworkService;
    @Mock
    private FileApi fileApi;
    @Mock
    private LeadInboxFilterConfigService inboxFilterConfigService;
    @Mock
    private DeptApi deptApi;
    @Mock
    private LeadObjectPermissionService leadObjectPermissionService;

    @Test
    void pageRestrictsOrdinaryUserToRelatedLeads() {
        LeadManagementPageReqVO reqVO = new LeadManagementPageReqVO();
        LeadDO lead = lead(1L, 10L, 20L);
        when(securityFrameworkService.hasPermission("zsjos:lead:query-all")).thenReturn(false);
        when(leadMapper.selectManagementPage(reqVO, 10L, List.of(), List.of(), false))
                .thenReturn(new PageResult<>(List.of(lead), 1L));
        when(intendedProductMapper.selectListByLeadIds(List.of(1L))).thenReturn(List.of());
        when(adminUserApi.getUserMap(anyCollection())).thenReturn(Map.of());

        PageResult<LeadManagementRespVO> result = service.getLeadPage(reqVO, 10L);

        assertEquals(1L, result.getTotal());
        assertEquals(List.of("submitter"), result.getList().getFirst().getRelationTypes());
        assertEquals("13800138000", result.getList().getFirst().getSubmittedMobile());
        verify(leadMapper).selectManagementPage(reqVO, 10L, List.of(), List.of(), false);
    }

    @Test
    void pageAllowsQueryAllPermissionWithoutRelationScope() {
        LeadManagementPageReqVO reqVO = new LeadManagementPageReqVO();
        when(securityFrameworkService.hasPermission("zsjos:lead:query-all")).thenReturn(true);
        when(leadMapper.selectManagementPage(reqVO, null, List.of(), List.of(), false)).thenReturn(PageResult.empty());

        service.getLeadPage(reqVO, 99L);

        verify(leadMapper).selectManagementPage(reqVO, null, List.of(), List.of(), false);
    }

    @Test
    void detailAllowsOwnerAndReturnsBothRelationsWhenApplicable() {
        LeadDO lead = lead(1L, 10L, 10L);
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(10L);
        user.setNickname("销售一号");
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(adminUserApi.getUserMap(anyCollection())).thenReturn(Map.of(10L, user));
        when(intendedProductMapper.selectListByLeadId(1L)).thenReturn(List.of());
        when(attachmentMapper.selectListByLeadId(1L)).thenReturn(List.of());
        when(leadObjectPermissionService.canRead(lead, 10L)).thenReturn(true);

        LeadManagementRespVO result = service.getLead(1L, 10L);

        assertEquals(List.of("submitter", "owner"), result.getRelationTypes());
        assertEquals("销售一号", result.getOwnerUserName());
    }

    @Test
    void detailRejectsUnrelatedUserWithoutQueryAll() {
        when(leadMapper.selectById(1L)).thenReturn(lead(1L, 10L, 20L));
        when(leadObjectPermissionService.canRead(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(30L))).thenReturn(false);

        ServiceException error = assertThrows(ServiceException.class, () -> service.getLead(1L, 30L));

        assertEquals(LEAD_PERMISSION_DENIED.getCode(), error.getCode());
        verifyNoInteractions(fileApi);
    }

    @Test
    void detailAllowsUnrelatedUserWithQueryAll() {
        LeadDO lead = lead(1L, 10L, 20L);
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(adminUserApi.getUserMap(anyCollection())).thenReturn(Map.of());
        when(intendedProductMapper.selectListByLeadId(1L)).thenReturn(List.of());
        when(attachmentMapper.selectListByLeadId(1L)).thenReturn(List.of());
        when(leadObjectPermissionService.canRead(lead, 30L)).thenReturn(true);

        LeadManagementRespVO result = service.getLead(1L, 30L);

        assertEquals(1L, result.getId());
        assertEquals(List.of(), result.getRelationTypes());
    }

    @Test
    void detailSignsReferencedAttachmentsAfterAuthorization() {
        LeadDO lead = lead(1L, 10L, 20L);
        LeadAttachmentDO attachment = new LeadAttachmentDO();
        attachment.setId(30L);
        attachment.setLeadId(1L);
        attachment.setInfraFileId(40L);
        attachment.setFileUrl("https://legacy.test/image.jpg");
        attachment.setOriginalName("image.jpg");
        attachment.setContentType("image/jpeg");
        attachment.setFileSize(100L);
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(adminUserApi.getUserMap(anyCollection())).thenReturn(Map.of());
        when(intendedProductMapper.selectListByLeadId(1L)).thenReturn(List.of());
        when(attachmentMapper.selectListByLeadId(1L)).thenReturn(List.of(attachment));
        when(fileApi.presignGetUrls(List.of(40L), 600)).thenReturn(Map.of(40L, "https://signed.test/image"));
        when(leadObjectPermissionService.canRead(lead, 10L)).thenReturn(true);

        LeadManagementRespVO result = service.getLead(1L, 10L);

        assertEquals("https://signed.test/image", result.getAttachments().getFirst().getFileUrl());
    }

    @Test
    void detailSignsAndReturnsInvalidQualificationEvidence() {
        LeadDO lead = lead(1L, 10L, 20L);
        lead.setStatus("invalid");
        lead.setInvalidEvidenceRefs("[{\"infraFileId\":41,\"fileUrl\":\"https://legacy.test/evidence.jpg\","
                + "\"originalName\":\"evidence.jpg\",\"contentType\":\"image/jpeg\",\"fileSize\":120,\"sort\":0}]");
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(adminUserApi.getUserMap(anyCollection())).thenReturn(Map.of());
        when(intendedProductMapper.selectListByLeadId(1L)).thenReturn(List.of());
        when(attachmentMapper.selectListByLeadId(1L)).thenReturn(List.of());
        when(fileApi.presignGetUrls(List.of(41L), 600)).thenReturn(Map.of(41L, "https://signed.test/evidence"));
        when(leadObjectPermissionService.canRead(lead, 10L)).thenReturn(true);

        LeadManagementRespVO result = service.getLead(1L, 10L);

        assertEquals(1, result.getInvalidEvidence().size());
        assertEquals("https://signed.test/evidence", result.getInvalidEvidence().getFirst().getFileUrl());
        assertEquals("evidence.jpg", result.getInvalidEvidence().getFirst().getOriginalName());
    }

    @Test
    void statusCountsRestrictOrdinaryUserToRelatedLeads() {
        when(securityFrameworkService.hasPermission("zsjos:lead:query-all")).thenReturn(false);
        when(leadMapper.selectManagementStatusCounts(10L)).thenReturn(Map.of("valid", 2L));

        Map<String, Long> result = service.getStatusCounts(10L);

        assertEquals(Map.of("valid", 2L), result);
        verify(leadMapper).selectManagementStatusCounts(10L);
    }

    @Test
    void statusCountsAllowQueryAllWithoutRelationScope() {
        when(securityFrameworkService.hasPermission("zsjos:lead:query-all")).thenReturn(true);
        when(leadMapper.selectManagementStatusCounts(null)).thenReturn(Map.of("valid", 3L));

        Map<String, Long> result = service.getStatusCounts(99L);

        assertEquals(Map.of("valid", 3L), result);
        verify(leadMapper).selectManagementStatusCounts(null);
    }

    @Test
    void inboxFilterProfileUsesScopedRealStateCounts() {
        LeadInboxFilterConfigVO config = filterConfig();
        when(securityFrameworkService.hasPermission(PERMISSION_QUERY_SUBMITTED)).thenReturn(true);
        when(inboxFilterConfigService.getPublishedConfig("submitter")).thenReturn(config);
        when(inboxFilterConfigService.resolveQuery(config, "all", "all"))
                .thenReturn(new LeadInboxFilterQuery(java.util.Set.of(), java.util.Set.of(), false));
        when(inboxFilterConfigService.resolveQuery(config, "pending", "all"))
                .thenReturn(new LeadInboxFilterQuery(java.util.Set.of("submitted"), java.util.Set.of(), false));
        when(inboxFilterConfigService.resolveQuery(config, "pending", "owned"))
                .thenReturn(new LeadInboxFilterQuery(java.util.Set.of("submitted"), java.util.Set.of("owned"), false));
        when(leadMapper.selectManagementInboxStateCounts(10L, "submitter")).thenReturn(List.of(
                Map.of("status", "submitted", "assignment_status", "pending_acceptance", "total", 2L),
                Map.of("status", "submitted", "assignment_status", "owned", "total", 3L),
                Map.of("status", "valid", "assignment_status", "owned", "total", 4L),
                Map.of("status", "converted", "assignment_status", "closed", "total", 1L)));

        LeadInboxFilterProfileRespVO result = service.getInboxFilterProfile(10L, "submitter");

        assertEquals(10L, result.getGroups().getFirst().getCount());
        LeadInboxFilterProfileRespVO.GroupVO pending = result.getGroups().get(1);
        assertEquals("pending", pending.getKey());
        assertEquals(5L, pending.getCount());
        assertEquals(3L, pending.getSections().getFirst().getOptions().getFirst().getCount());
        verify(leadMapper).selectManagementInboxStateCounts(10L, "submitter");
    }

    @Test
    void pageUsesOwnerAudienceEvenForQueryAllUser() {
        LeadManagementPageReqVO reqVO = new LeadManagementPageReqVO();
        reqVO.setAudience("owner");
        reqVO.setInboxGroup("all");
        LeadInboxFilterConfigVO config = filterConfig();
        when(securityFrameworkService.hasPermission(PERMISSION_QUERY_OWNED)).thenReturn(true);
        when(securityFrameworkService.hasPermission("zsjos:lead:query-all")).thenReturn(true);
        when(inboxFilterConfigService.getPublishedConfig("owner")).thenReturn(config);
        when(inboxFilterConfigService.resolveQuery(config, "all", null))
                .thenReturn(new LeadInboxFilterQuery(java.util.Set.of(), java.util.Set.of(), false));
        when(leadMapper.selectManagementPage(reqVO, 99L, List.of(), List.of(), false)).thenReturn(PageResult.empty());

        service.getLeadPage(reqVO, 99L);

        verify(leadMapper).selectManagementPage(reqVO, 99L, List.of(), List.of(), false);
    }

    @Test
    void pageRejectsAudienceWithoutMatchingPermission() {
        LeadManagementPageReqVO reqVO = new LeadManagementPageReqVO();
        reqVO.setAudience("owner");
        when(securityFrameworkService.hasPermission(PERMISSION_QUERY_OWNED)).thenReturn(false);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.getLeadPage(reqVO, 10L));

        assertEquals(LEAD_PERMISSION_DENIED.getCode(), error.getCode());
        verifyNoInteractions(leadMapper, inboxFilterConfigService);
    }

    @Test
    void detailDeclaresObjectReadPermission() throws NoSuchMethodException {
        ZsjosPermission permission = LeadManagementServiceImpl.class
                .getMethod("getLead", Long.class, Long.class)
                .getAnnotation(ZsjosPermission.class);

        assertEquals("lead", permission.bizType());
        assertEquals("#id", permission.bizId());
        assertEquals("read", permission.action());
    }

    private static LeadDO lead(Long id, Long sourceUserId, Long ownerUserId) {
        LeadDO lead = new LeadDO();
        lead.setId(id);
        lead.setPersonId(100L);
        lead.setSubmittedName("测试客户");
        lead.setSubmittedMobile("13800138000");
        lead.setSourceUserId(sourceUserId);
        lead.setOwnerUserId(ownerUserId);
        return lead;
    }

    private static LeadInboxFilterConfigVO filterConfig() {
        LeadInboxFilterConfigVO config = new LeadInboxFilterConfigVO();
        LeadInboxFilterConfigVO.GroupVO all = new LeadInboxFilterConfigVO.GroupVO();
        all.setKey("all"); all.setLabel("全部"); all.setSort(0); all.setEnabled(true);
        LeadInboxFilterConfigVO.GroupVO pending = new LeadInboxFilterConfigVO.GroupVO();
        pending.setKey("pending"); pending.setLabel("待判定"); pending.setSort(10); pending.setEnabled(true);
        pending.setSectionLabel("当前环节");
        LeadInboxFilterConfigVO.OptionVO owned = new LeadInboxFilterConfigVO.OptionVO();
        owned.setKey("owned"); owned.setLabel("已归属"); owned.setSort(10); owned.setEnabled(true);
        pending.setOptions(List.of(owned));
        config.setGroups(List.of(all, pending));
        return config;
    }
}
