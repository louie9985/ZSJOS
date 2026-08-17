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
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAttachmentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAttachmentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadIntendedProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderObjectPermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.PERMISSION_QUERY_OWNED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.PERMISSION_QUERY_SUBMITTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
    @Mock
    private OpportunityMapper opportunityMapper;
    @Mock
    private SalesOrderMapper salesOrderMapper;
    @Mock
    private LeadAgingPoolService agingPoolService;
    @Mock
    private AdvancedFilterService advancedFilterService;
    @Mock
    private SalesOrderObjectPermissionService salesOrderPermissionService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(advancedFilterService.matchLeadIds(org.mockito.ArgumentMatchers.any())).thenReturn(null);
        org.mockito.Mockito.lenient().when(agingPoolService.canOperate(
                        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.nullable(Long.class),
                        org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(invocation -> java.util.Objects.equals(invocation.getArgument(1), invocation.getArgument(2)));
    }

    @Test
    void pageRestrictsOrdinaryUserToRelatedLeads() {
        LeadManagementPageReqVO reqVO = new LeadManagementPageReqVO();
        LeadDO lead = lead(1L, 10L, 20L);
        when(securityFrameworkService.hasPermission(PERMISSION_QUERY_SUBMITTED)).thenReturn(true);
        when(securityFrameworkService.hasPermission(PERMISSION_QUERY_OWNED)).thenReturn(true);
        when(leadObjectPermissionService.getRelatedAndManagedUserIds(10L)).thenReturn(Set.of(10L));
        when(leadMapper.selectManagementPageByScope(reqVO, List.of(10L), List.of(10L), false,
                List.of(), List.of(), List.of(), false, null))
                .thenReturn(new PageResult<>(List.of(lead), 1L));
        when(intendedProductMapper.selectListByLeadIds(List.of(1L))).thenReturn(List.of());
        when(adminUserApi.getUserMap(anyCollection())).thenReturn(Map.of());

        PageResult<LeadManagementRespVO> result = service.getLeadPage(reqVO, 10L);

        assertEquals(1L, result.getTotal());
        assertEquals(List.of("submitter"), result.getList().getFirst().getRelationTypes());
        assertEquals("13800138000", result.getList().getFirst().getSubmittedMobile());
        verify(leadMapper).selectManagementPageByScope(reqVO, List.of(10L), List.of(10L), false,
                List.of(), List.of(), List.of(), false, null);
    }

    @Test
    void pageAllowsQueryAllPermissionWithoutRelationScope() {
        LeadManagementPageReqVO reqVO = new LeadManagementPageReqVO();
        when(leadObjectPermissionService.hasQueryAll()).thenReturn(true);
        when(leadMapper.selectManagementPageByScope(reqVO, List.of(), List.of(), true,
                List.of(), List.of(), List.of(), false, null)).thenReturn(PageResult.empty());

        service.getLeadPage(reqVO, 99L);

        verify(leadMapper).selectManagementPageByScope(reqVO, List.of(), List.of(), true,
                List.of(), List.of(), List.of(), false, null);
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
    void detailBlindsSubmitterAndOwnerIdentitiesForOrdinaryCounterpart() {
        LeadDO lead = actionLead("submitted", "owned", true);
        AdminUserRespDTO submitter = user(10L, 0); submitter.setNickname("提交销售");
        AdminUserRespDTO owner = user(20L, 0); owner.setNickname("负责销售");
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(adminUserApi.getUserMap(anyCollection())).thenReturn(Map.of(10L, submitter, 20L, owner));
        when(intendedProductMapper.selectListByLeadId(1L)).thenReturn(List.of());
        when(attachmentMapper.selectListByLeadId(1L)).thenReturn(List.of());
        when(leadObjectPermissionService.canRead(lead, 10L)).thenReturn(true);
        when(leadObjectPermissionService.canViewUnmaskedIdentity(10L, 20L)).thenReturn(false);

        LeadManagementRespVO result = service.getLead(1L, 10L);

        assertEquals("提交销售", result.getSourceUserName());
        assertEquals(10L, result.getSourceUserId());
        assertNotEquals("负责销售", result.getOwnerUserName());
        assertEquals(null, result.getOwnerUserId());
    }

    @Test
    void detailKeepsIdentitiesForManagerScope() {
        LeadDO lead = actionLead("submitted", "owned", true);
        AdminUserRespDTO submitter = user(10L, 0); submitter.setNickname("提交销售");
        AdminUserRespDTO owner = user(20L, 0); owner.setNickname("负责销售");
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(adminUserApi.getUserMap(anyCollection())).thenReturn(Map.of(10L, submitter, 20L, owner));
        when(intendedProductMapper.selectListByLeadId(1L)).thenReturn(List.of());
        when(attachmentMapper.selectListByLeadId(1L)).thenReturn(List.of());
        when(leadObjectPermissionService.canRead(lead, 30L)).thenReturn(true);
        when(leadObjectPermissionService.canViewUnmaskedIdentity(30L, 20L)).thenReturn(true);

        LeadManagementRespVO result = service.getLead(1L, 30L);

        assertEquals("提交销售", result.getSourceUserName());
        assertEquals("负责销售", result.getOwnerUserName());
        assertEquals(20L, result.getOwnerUserId());
    }

    @Test
    void detailProjectsOwnerActionsForEachLifecycleStage() {
        when(securityFrameworkService.hasPermission("zsjos:lead:update")).thenReturn(true);
        when(securityFrameworkService.hasPermission("zsjos:lead-follow-up:create")).thenReturn(true);
        when(securityFrameworkService.hasPermission("zsjos:lead:qualify")).thenReturn(true);
        when(securityFrameworkService.hasPermission("zsjos:sales-order:create")).thenReturn(true);
        when(salesOrderPermissionService.canRevise(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(20L))).thenReturn(true);

        assertActions(actionLead("submitted", "owned", false), null,
                "EDIT_BASIC_INFO", "ADD_FOLLOW_UP");
        assertActions(actionLead("submitted", "owned", true), null,
                "EDIT_BASIC_INFO", "ADD_FOLLOW_UP", "JUDGE_VALID", "JUDGE_INVALID");
        OpportunityDO opportunity = new OpportunityDO();
        opportunity.setId(30L); opportunity.setStatus("open");
        assertActions(actionLead("valid", "owned", true), opportunity,
                "EDIT_BASIC_INFO", "ADD_FOLLOW_UP", "JUDGE_INVALID", "ENTER_DEAL");
        assertActions(actionLead("valid", "owned", true), null,
                "EDIT_BASIC_INFO", "ADD_FOLLOW_UP", "JUDGE_INVALID", "ENTER_DEAL");
        SalesOrderDO pendingOrder = new SalesOrderDO();
        pendingOrder.setId(40L); pendingOrder.setStatus("pending_approval");
        assertActions(actionLead("valid", "owned", true), null, pendingOrder,
                "EDIT_BASIC_INFO", "ADD_FOLLOW_UP", "JUDGE_INVALID");
        SalesOrderDO revisionOrder = new SalesOrderDO();
        revisionOrder.setId(41L); revisionOrder.setStatus("revision_required");
        LeadManagementRespVO revisionResult = assertActions(actionLead("valid", "owned", true), null, revisionOrder,
                "EDIT_BASIC_INFO", "ADD_FOLLOW_UP", "JUDGE_INVALID", "REVISE_DEAL");
        assertEquals(41L, revisionResult.getActiveSalesOrderId());
        assertEquals("revision_required", revisionResult.getActiveSalesOrderStatus());
        assertActions(actionLead("invalid", "owned", true), null);
        assertActions(actionLead("submitted", "public_pool", false), null);
    }

    @Test
    void detailProjectsQualificationAndFollowUpIndependently() {
        LeadDO firstFollow = actionLead("submitted", "owned", false);
        assertProjection(firstFollow, null, "pending", "first_follow_pending", "active");
        LeadDO following = actionLead("submitted", "owned", true);
        assertProjection(following, null, "pending", "following", "active");
        assertProjection(actionLead("valid", "owned", true), null, "valid", "following", "active");
        assertProjection(actionLead("invalid", "owned", true), null, "invalid", null, "active");
        LeadDO suspended = actionLead("suspended", "owned", true);
        assertProjection(suspended, null, "pending", "following", "suspended");
    }

    @Test
    void detailNeverProjectsWriteActionsForNonOwnerViewer() {
        LeadDO lead = actionLead("submitted", "owned", true);
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(leadObjectPermissionService.canRead(lead, 99L)).thenReturn(true);
        when(adminUserApi.getUserMap(anyCollection())).thenReturn(Map.of());
        when(intendedProductMapper.selectListByLeadId(1L)).thenReturn(List.of());
        when(attachmentMapper.selectListByLeadId(1L)).thenReturn(List.of());

        assertEquals(List.of(), service.getLead(1L, 99L).getAvailableActions());
    }

    @Test
    void activePoolCollaboratorOnlyReceivesFollowUpAndDealActions() {
        LeadDO lead = actionLead("valid", "owned", true);
        OpportunityDO opportunity = new OpportunityDO();
        opportunity.setId(30L); opportunity.setStatus("following");
        cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAgingPoolCycleDO cycle =
                new cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAgingPoolCycleDO();
        cycle.setStatus("assigned"); cycle.setCollaboratorUserId(30L);
        when(agingPoolService.getActiveCycle(1L)).thenReturn(cycle);
        when(agingPoolService.canOperate(1L, 20L, 30L)).thenReturn(true);
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(leadObjectPermissionService.canRead(lead, 30L)).thenReturn(true);
        when(adminUserApi.getUserMap(anyCollection())).thenReturn(Map.of());
        when(intendedProductMapper.selectListByLeadId(1L)).thenReturn(List.of());
        when(attachmentMapper.selectListByLeadId(1L)).thenReturn(List.of());
        when(opportunityMapper.selectByLeadId(1L)).thenReturn(opportunity);
        when(securityFrameworkService.hasPermission("zsjos:lead:update")).thenReturn(true);
        when(securityFrameworkService.hasPermission("zsjos:lead-follow-up:create")).thenReturn(true);
        when(securityFrameworkService.hasPermission("zsjos:lead:qualify")).thenReturn(true);
        when(securityFrameworkService.hasPermission("zsjos:sales-order:create")).thenReturn(true);

        LeadManagementRespVO result = service.getLead(1L, 30L);

        assertEquals(List.of("ADD_FOLLOW_UP", "ENTER_DEAL"), result.getAvailableActions().stream()
                .map(LeadManagementRespVO.ActionVO::getCode).toList());
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
        when(securityFrameworkService.hasPermission(PERMISSION_QUERY_SUBMITTED)).thenReturn(true);
        when(securityFrameworkService.hasPermission(PERMISSION_QUERY_OWNED)).thenReturn(true);
        when(leadObjectPermissionService.getRelatedAndManagedUserIds(10L)).thenReturn(Set.of(10L, 20L));
        when(leadMapper.selectManagementStatusCountsByScope(List.of(10L, 20L), List.of(10L, 20L), false))
                .thenReturn(Map.of("valid", 2L));

        Map<String, Long> result = service.getStatusCounts(10L);

        assertEquals(Map.of("valid", 2L), result);
        verify(leadMapper).selectManagementStatusCountsByScope(List.of(10L, 20L), List.of(10L, 20L), false);
    }

    @Test
    void statusCountsAllowQueryAllWithoutRelationScope() {
        when(leadObjectPermissionService.hasQueryAll()).thenReturn(true);
        when(leadMapper.selectManagementStatusCountsByScope(List.of(), List.of(), true)).thenReturn(Map.of("valid", 3L));

        Map<String, Long> result = service.getStatusCounts(99L);

        assertEquals(Map.of("valid", 3L), result);
        verify(leadMapper).selectManagementStatusCountsByScope(List.of(), List.of(), true);
    }

    @Test
    void inboxFilterProfileDoesNotQueryCounts() {
        LeadInboxFilterConfigVO config = filterConfig();
        when(securityFrameworkService.hasPermission(PERMISSION_QUERY_SUBMITTED)).thenReturn(true);
        when(inboxFilterConfigService.getPublishedConfig("submitter")).thenReturn(config);
        LeadInboxFilterProfileRespVO result = service.getInboxFilterProfile(10L, "submitter");

        LeadInboxFilterProfileRespVO.GroupVO pending = result.getGroups().get(1);
        assertEquals("pending", pending.getKey());
        assertEquals("owned", pending.getSections().getFirst().getOptions().getFirst().getKey());
        verify(leadMapper, never()).selectManagementInboxStateCounts(any(), any());
    }

    @Test
    void inboxFilterProfileSeparatesFirstFollowAndQualificationStages() {
        LeadInboxFilterConfigVO config = filterConfig();
        config.getGroups().get(1).getOptions().getFirst().setKey("first_follow_pending");
        LeadInboxFilterConfigVO.OptionVO qualification = new LeadInboxFilterConfigVO.OptionVO();
        qualification.setKey("qualification_pending"); qualification.setLabel("待判定");
        qualification.setSort(20); qualification.setEnabled(true);
        config.getGroups().get(1).setOptions(List.of(config.getGroups().get(1).getOptions().getFirst(), qualification));
        when(securityFrameworkService.hasPermission(PERMISSION_QUERY_SUBMITTED)).thenReturn(true);
        when(inboxFilterConfigService.getPublishedConfig("submitter")).thenReturn(config);
        LeadInboxFilterProfileRespVO result = service.getInboxFilterProfile(10L, "submitter");

        assertEquals(List.of("first_follow_pending", "qualification_pending"),
                result.getGroups().get(1).getSections().getFirst().getOptions().stream()
                        .map(LeadInboxFilterProfileRespVO.OptionVO::getKey).toList());
        verify(leadMapper, never()).selectManagementInboxStateCounts(any(), any());
    }

    @Test
    void pageUsesOwnerAudienceEvenForQueryAllUser() {
        LeadManagementPageReqVO reqVO = new LeadManagementPageReqVO();
        reqVO.setAudience("owner");
        reqVO.setInboxGroup("all");
        LeadInboxFilterConfigVO config = filterConfig();
        when(securityFrameworkService.hasPermission(PERMISSION_QUERY_OWNED)).thenReturn(true);
        when(leadObjectPermissionService.hasQueryAll()).thenReturn(true);
        when(leadObjectPermissionService.getRelatedAndManagedUserIds(99L)).thenReturn(Set.of(99L));
        when(inboxFilterConfigService.getPublishedConfig("owner")).thenReturn(config);
        when(inboxFilterConfigService.resolveQuery(config, "all", null))
                .thenReturn(new LeadInboxFilterQuery(java.util.Set.of(), java.util.Set.of(), false));
        when(leadMapper.selectManagementPageByScope(reqVO, List.of(), List.of(99L), false,
                List.of(), List.of(), List.of(), false, null)).thenReturn(PageResult.empty());

        service.getLeadPage(reqVO, 99L);

        verify(leadMapper).selectManagementPageByScope(reqVO, List.of(), List.of(99L), false,
                List.of(), List.of(), List.of(), false, null);
    }

    @Test
    void pagePassesHandlingStageToDatabaseFilter() {
        LeadManagementPageReqVO reqVO = new LeadManagementPageReqVO();
        reqVO.setAudience("owner"); reqVO.setInboxGroup("pending"); reqVO.setInboxStage("first_follow_pending");
        LeadInboxFilterConfigVO config = filterConfig();
        when(securityFrameworkService.hasPermission(PERMISSION_QUERY_OWNED)).thenReturn(true);
        when(leadObjectPermissionService.getRelatedAndManagedUserIds(10L)).thenReturn(Set.of(10L));
        when(inboxFilterConfigService.getPublishedConfig("owner")).thenReturn(config);
        when(inboxFilterConfigService.resolveQuery(config, "pending", "first_follow_pending"))
                .thenReturn(new LeadInboxFilterQuery(Set.of("submitted"), Set.of("owned"),
                        Set.of("first_follow_pending"), false, Map.of()));
        when(leadMapper.selectManagementPageByScope(reqVO, List.of(), List.of(10L), false,
                List.of("submitted"), List.of("owned"), List.of("first_follow_pending"), false, null))
                .thenReturn(PageResult.empty());

        service.getLeadPage(reqVO, 10L);

        verify(leadMapper).selectManagementPageByScope(reqVO, List.of(), List.of(10L), false,
                List.of("submitted"), List.of("owned"), List.of("first_follow_pending"), false, null);
    }

    @Test
    void advancedFilterKeepsInboxConstraintsAndAddsMatchedIds() {
        LeadManagementPageReqVO reqVO = new LeadManagementPageReqVO();
        reqVO.setAudience("owner"); reqVO.setInboxGroup("pending");
        reqVO.setAdvancedFilter(new AdvancedFilterGroupReqVO());
        LeadInboxFilterConfigVO config = filterConfig();
        when(securityFrameworkService.hasPermission(PERMISSION_QUERY_OWNED)).thenReturn(true);
        when(leadObjectPermissionService.getRelatedAndManagedUserIds(10L)).thenReturn(Set.of(10L));
        when(inboxFilterConfigService.getPublishedConfig("owner")).thenReturn(config);
        when(inboxFilterConfigService.resolveQuery(config, "pending", null))
                .thenReturn(new LeadInboxFilterQuery(Set.of("submitted"), Set.of("owned"), false));
        when(advancedFilterService.matchLeadIds(reqVO.getAdvancedFilter())).thenReturn(List.of(7L, 8L));
        when(leadMapper.selectManagementPageByScope(reqVO, List.of(), List.of(10L), false,
                List.of("submitted"), List.of("owned"), List.of(), false, List.of(7L, 8L)))
                .thenReturn(PageResult.empty());

        service.getLeadPage(reqVO, 10L);

        verify(leadMapper).selectManagementPageByScope(reqVO, List.of(), List.of(10L), false,
                List.of("submitted"), List.of("owned"), List.of(), false, List.of(7L, 8L));
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

    @Test
    void pageIncludesManagedDepartmentOwnersForTeamView() {
        LeadManagementPageReqVO reqVO = new LeadManagementPageReqVO();
        when(securityFrameworkService.hasPermission(PERMISSION_QUERY_SUBMITTED)).thenReturn(true);
        when(securityFrameworkService.hasPermission(PERMISSION_QUERY_OWNED)).thenReturn(true);
        when(leadObjectPermissionService.getRelatedAndManagedUserIds(10L)).thenReturn(Set.of(10L, 20L, 21L));
        when(leadMapper.selectManagementPageByScope(reqVO, List.of(10L, 20L, 21L),
                List.of(10L, 20L, 21L), false, List.of(), List.of(), List.of(), false, null))
                .thenReturn(PageResult.empty());

        service.getLeadPage(reqVO, 10L);

        verify(leadMapper).selectManagementPageByScope(reqVO, List.of(10L, 20L, 21L),
                List.of(10L, 20L, 21L), false, List.of(), List.of(), List.of(), false, null);
    }

    @Test
    void visibleUsersExcludeParallelDepartmentUsersAndDisabledAccounts() {
        AdminUserRespDTO self = user(10L, 0);
        AdminUserRespDTO managed = user(20L, 0);
        AdminUserRespDTO disabled = user(21L, 1);
        when(leadObjectPermissionService.hasQueryAll()).thenReturn(false);
        when(leadObjectPermissionService.getRelatedAndManagedUserIds(10L)).thenReturn(Set.of(10L, 20L, 21L));
        when(adminUserApi.getUserList(Set.of(10L, 20L, 21L))).thenReturn(List.of(self, managed, disabled));

        assertEquals(List.of(10L, 20L), service.getVisibleUsers(10L).stream().map(LeadAssignmentUserRespVO::getId).toList());
    }

    @Test
    void visibleUsersAllowQueryAll() {
        List<AdminUserRespDTO> users = List.of(user(20L, 0), user(30L, 0));
        when(adminUserApi.getUserListByStatus(0)).thenReturn(users);
        when(leadObjectPermissionService.hasQueryAll()).thenReturn(true);

        assertEquals(List.of(20L, 30L), service.getVisibleUsers(10L).stream().map(LeadAssignmentUserRespVO::getId).toList());
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

    private static AdminUserRespDTO user(Long id, Integer status) {
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(id);
        user.setNickname("用户" + id);
        user.setStatus(status);
        return user;
    }

    private LeadDO actionLead(String status, String assignmentStatus, boolean qualificationPending) {
        LeadDO lead = lead(1L, 10L, 20L);
        lead.setStatus(status); lead.setAssignmentStatus(assignmentStatus);
        lead.setQualificationDeadlineAt(qualificationPending ? java.time.LocalDateTime.now().plusHours(1) : null);
        return lead;
    }

    private LeadManagementRespVO assertActions(LeadDO lead, OpportunityDO opportunity, String... expected) {
        return assertActions(lead, opportunity, null, expected);
    }

    private LeadManagementRespVO assertActions(LeadDO lead, OpportunityDO opportunity,
                                                SalesOrderDO activeOrder, String... expected) {
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(leadObjectPermissionService.canRead(lead, 20L)).thenReturn(true);
        when(adminUserApi.getUserMap(anyCollection())).thenReturn(Map.of());
        when(intendedProductMapper.selectListByLeadId(1L)).thenReturn(List.of());
        when(attachmentMapper.selectListByLeadId(1L)).thenReturn(List.of());
        when(opportunityMapper.selectByLeadId(1L)).thenReturn(opportunity);
        when(salesOrderMapper.selectActiveByLeadId(org.mockito.ArgumentMatchers.eq(1L), anyCollection()))
                .thenReturn(activeOrder);

        LeadManagementRespVO result = service.getLead(1L, 20L);

        assertEquals(List.of(expected), result.getAvailableActions().stream()
                .map(LeadManagementRespVO.ActionVO::getCode).toList());
        if (Set.of(expected).contains("ENTER_DEAL") || Set.of(expected).contains("REVISE_DEAL")) {
            assertEquals(true, result.getAvailableActions().getLast().getEnabled());
        }
        return result;
    }

    private void assertProjection(LeadDO lead, OpportunityDO opportunity, String qualification,
                                  String followUp, String operational) {
        when(leadMapper.selectById(1L)).thenReturn(lead);
        when(leadObjectPermissionService.canRead(lead, 20L)).thenReturn(true);
        when(adminUserApi.getUserMap(anyCollection())).thenReturn(Map.of());
        when(intendedProductMapper.selectListByLeadId(1L)).thenReturn(List.of());
        when(attachmentMapper.selectListByLeadId(1L)).thenReturn(List.of());
        when(opportunityMapper.selectByLeadId(1L)).thenReturn(opportunity);
        LeadManagementRespVO result = service.getLead(1L, 20L);
        assertEquals(qualification, result.getQualificationStatus());
        assertEquals(followUp, result.getFollowUpStatus());
        assertEquals(operational, result.getOperationalStatus());
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
