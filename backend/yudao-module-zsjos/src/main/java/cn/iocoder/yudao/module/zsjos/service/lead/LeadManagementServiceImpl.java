package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.hutool.core.util.DesensitizedUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.CursorPageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerLeadFollowUpSummaryRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadInboxFilterProfileRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadBasicInfoUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter.LeadInboxFilterConfigVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAttachmentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAgingPoolCycleDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadIntendedProductDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PartnerDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAttachmentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadIntendedProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.task.BusinessTaskMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PartnerMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_NOT_EXISTS;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.ATTACHMENT_URL_EXPIRATION_SECONDS;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.INBOX_AUDIENCE_OWNER;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.INBOX_AUDIENCE_SUBMITTER;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.PERMISSION_QUERY_OWNED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.PERMISSION_QUERY_SUBMITTED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.service.lead.SupervisorLeadActionPolicy.Action.*;

@Service
public class LeadManagementServiceImpl implements LeadManagementService {

    private static final String QUERY_ALL_PERMISSION = "zsjos:lead:query-all";

    @Resource
    private LeadMapper leadMapper;
    @Resource
    private LeadIntendedProductMapper intendedProductMapper;
    @Resource
    private LeadAttachmentMapper attachmentMapper;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private SecurityFrameworkService securityFrameworkService;
    @Resource
    private FileApi fileApi;
    @Resource
    private LeadInboxFilterConfigService inboxFilterConfigService;
    @Resource private LeadObjectPermissionService leadObjectPermissionService;
    @Resource private OpportunityMapper opportunityMapper;
    @Resource private LeadBasicInfoService leadBasicInfoService;
    @Resource private SalesOrderMapper salesOrderMapper;
    @Resource private LeadAgingPoolService agingPoolService;
    @Resource private cn.iocoder.yudao.module.zsjos.service.order.SalesOrderObjectPermissionService salesOrderPermissionService;
    @Resource private AdvancedFilterService advancedFilterService;
    @Resource private PartnerMapper partnerMapper;
    @Resource private BusinessTaskMapper businessTaskMapper;

    @Override
    public PageResult<LeadManagementRespVO> getLeadPage(LeadManagementPageReqVO reqVO, Long userId) {
        String relationScope = resolveRelationScope(reqVO);
        validateRelationScopePermission(relationScope);
        LeadInboxFilterQuery inboxQuery = reqVO.getAudience() == null
                ? new LeadInboxFilterQuery(Set.of(), Set.of(), false)
                : inboxFilterConfigService.resolveQuery(
                        inboxFilterConfigService.getPublishedConfig(reqVO.getAudience()),
                        reqVO.getInboxGroup(), reqVO.getInboxStage());
        LeadVisibilityScope visibility = resolveVisibilityScope(relationScope, userId);
        List<Long> matchedLeadIds = advancedFilterService.matchLeadIds(reqVO.getAdvancedFilter());
        List<String> statuses = List.copyOf(inboxQuery.statuses());
        List<String> assignmentStatuses = List.copyOf(inboxQuery.assignmentStatuses());
        List<String> handlingStages = List.copyOf(inboxQuery.handlingStages());
        boolean matchNone = inboxQuery.matchNone();
        PageResult<LeadDO> page = leadMapper.selectManagementPageByScope(reqVO,
                visibility.sourceUserIds(), visibility.ownerUserIds(), visibility.queryAll(),
                statuses, assignmentStatuses, handlingStages, matchNone, matchedLeadIds);
        if (page.getList().isEmpty()) {
            return PageResult.empty(page.getTotal());
        }
        List<Long> leadIds = page.getList().stream().map(LeadDO::getId).toList();
        Map<Long, List<LeadIntendedProductDO>> products = groupByLeadId(
                intendedProductMapper.selectListByLeadIds(leadIds), LeadIntendedProductDO::getLeadId);
        Map<Long, OpportunityDO> opportunities = getOpportunityMap(leadIds);
        Map<Long, PartnerDO> partners = getPartnerMap(page.getList());
        Map<Long, AdminUserRespDTO> users = getUserMap(page.getList());
        List<LeadManagementRespVO> result = page.getList().stream()
                .map(lead -> convert(lead, userId, users, products.getOrDefault(lead.getId(), List.of()),
                        List.of(), Map.of(), false, opportunities, partners))
                .toList();
        return new PageResult<>(result, page.getTotal());
    }

    @Override
    public PageResult<LeadManagementRespVO> getPartnerLeadPage(LeadManagementPageReqVO reqVO, Long partnerId) {
        PageResult<LeadDO> page = leadMapper.selectPartnerPage(reqVO, partnerId);
        if (page.getList().isEmpty()) return PageResult.empty(page.getTotal());
        List<Long> leadIds = page.getList().stream().map(LeadDO::getId).toList();
        Map<Long, List<LeadIntendedProductDO>> products = groupByLeadId(
                intendedProductMapper.selectListByLeadIds(leadIds), LeadIntendedProductDO::getLeadId);
        Map<Long, OpportunityDO> opportunities = getOpportunityMap(leadIds);
        Map<Long, PartnerDO> partners = getPartnerMap(page.getList());
        Map<Long, AdminUserRespDTO> users = getUserMap(page.getList());
        return new PageResult<>(page.getList().stream().map(lead -> convert(lead, null, users,
                products.getOrDefault(lead.getId(), List.of()), List.of(), Map.of(), false,
                opportunities, partners)).toList(), page.getTotal());
    }

    @Override
    public PartnerLeadFollowUpSummaryRespVO getPartnerLeadFollowUpSummary(Long partnerId) {
        PartnerLeadFollowUpSummaryRespVO result = new PartnerLeadFollowUpSummaryRespVO();
        result.setFollowUpPendingCount(leadMapper.countPartnerFollowUpPending(partnerId));
        result.setUnreachableCount(leadMapper.countPartnerUnreachable(partnerId));
        result.setInvalidCount(leadMapper.countPartnerInvalid(partnerId));
        return result;
    }

    @Override
    public CursorPageResult<LeadManagementRespVO> getLeadCursor(LeadManagementPageReqVO reqVO, Long userId) {
        LeadCursor cursor = decodeCursor(reqVO.getCursor(), reqVO, userId);
        reqVO.setCursorActivityAt(cursor == null ? null : cursor.time());
        reqVO.setCursorId(cursor == null ? null : cursor.id());
        int limit = reqVO.getLimit() == null ? 20 : reqVO.getLimit();
        reqVO.setPageNo(1);
        reqVO.setPageSize(limit + 1);
        PageResult<LeadManagementRespVO> page = getLeadPage(reqVO, userId);
        boolean hasMore = page.getList().size() > limit;
        List<LeadManagementRespVO> list = hasMore ? page.getList().subList(0, limit) : page.getList();
        String next = hasMore && !list.isEmpty() ? encodeCursor(list.get(list.size() - 1), reqVO, userId) : null;
        return new CursorPageResult<>(list, next, hasMore);
    }

    private String encodeCursor(LeadManagementRespVO item, LeadManagementPageReqVO reqVO, Long userId) {
        String raw = item.getLastActivityAt() + "|" + item.getId() + "|" + userId + "|"
                + cursorContext(reqVO);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private LeadCursor decodeCursor(String value, LeadManagementPageReqVO reqVO, Long userId) {
        if (value == null || value.isBlank()) return null;
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", 4);
            if (parts.length != 4 || !parts[2].equals(String.valueOf(userId)) || !parts[3].equals(cursorContext(reqVO))) {
                throw new IllegalArgumentException("cursor context mismatch");
            }
            return new LeadCursor(LocalDateTime.parse(parts[0]), Long.valueOf(parts[1]));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid lead cursor", ex);
        }
    }

    private String cursorContext(LeadManagementPageReqVO reqVO) {
        return Integer.toHexString(Objects.hash(reqVO.getRelationScope(), reqVO.getAudience(),
                reqVO.getInboxGroup(), reqVO.getInboxStage(), reqVO.getSimpleStatus(),
                reqVO.getKeyword(), reqVO.getStatus(), reqVO.getAssignmentStatus(), reqVO.getSourceChannel(),
                reqVO.getLeadCategory(), reqVO.getSourceUserId(), reqVO.getOwnerUserId(), reqVO.getAdvancedFilter()));
    }

    private record LeadCursor(LocalDateTime time, Long id) {}

    @Override
    public PageResult<LeadManagementRespVO> getManagedOwnerLeadPage(LeadManagementPageReqVO reqVO,
                                                                     Long managerUserId, Long ownerUserId) {
        if (!leadObjectPermissionService.getManagedUserIds(managerUserId).contains(ownerUserId)) {
            throw exception(LEAD_PERMISSION_DENIED);
        }
        reqVO.setAudience(INBOX_AUDIENCE_OWNER);
        reqVO.setRelationScope("owned");
        reqVO.setOwnerUserId(ownerUserId);
        PageResult<LeadDO> page = leadMapper.selectManagementPage(reqVO, ownerUserId,
                List.of(), List.of(), List.of(), false, advancedFilterService.matchLeadIds(reqVO.getAdvancedFilter()));
        if (page.getList().isEmpty()) return PageResult.empty(page.getTotal());
        List<Long> leadIds = page.getList().stream().map(LeadDO::getId).toList();
        Map<Long, List<LeadIntendedProductDO>> products = groupByLeadId(
                intendedProductMapper.selectListByLeadIds(leadIds), LeadIntendedProductDO::getLeadId);
        Map<Long, OpportunityDO> opportunities = getOpportunityMap(leadIds);
        Map<Long, PartnerDO> partners = getPartnerMap(page.getList());
        Map<Long, AdminUserRespDTO> users = getUserMap(page.getList());
        return new PageResult<>(page.getList().stream()
                .map(lead -> convert(lead, managerUserId, users,
                        products.getOrDefault(lead.getId(), List.of()), List.of(), Map.of(), false,
                        opportunities, partners))
                .toList(), page.getTotal());
    }

    @Override
    @ZsjosPermission(bizType = "lead", bizId = "#id", action = "read")
    public LeadManagementRespVO getLead(Long id, Long userId) {
        LeadDO lead = leadMapper.selectById(id);
        if (lead == null) {
            throw exception(LEAD_NOT_EXISTS);
        }
        if (!leadObjectPermissionService.canReadDetail(lead, userId)) {
            throw exception(LEAD_PERMISSION_DENIED);
        }
        Map<Long, AdminUserRespDTO> users = getUserMap(List.of(lead));
        List<LeadIntendedProductDO> products = intendedProductMapper.selectListByLeadId(id);
        List<LeadAttachmentDO> attachments = attachmentMapper.selectListByLeadId(id);
        return convert(lead, userId, users, products, attachments, resolveAttachmentUrls(attachments), true);
    }

    @Override
    public void updateBasicInfo(Long id, Long userId, LeadBasicInfoUpdateReqVO reqVO) {
        leadBasicInfoService.update(id, userId, reqVO);
    }

    @Override
    public Map<String, Long> getStatusCounts(Long userId) {
        LeadVisibilityScope visibility = resolveVisibilityScope("all", userId);
        return leadMapper.selectManagementStatusCountsByScope(visibility.sourceUserIds(),
                visibility.ownerUserIds(), visibility.queryAll());
    }

    @Override
    public List<LeadAssignmentUserRespVO> getVisibleUsers(Long userId) {
        List<AdminUserRespDTO> users;
        if (leadObjectPermissionService.hasQueryAll()) {
            users = adminUserApi.getUserListByStatus(CommonStatusEnum.ENABLE.getStatus());
        } else {
            users = adminUserApi.getUserList(leadObjectPermissionService.getRelatedAndManagedUserIds(userId)).stream()
                    .filter(user -> CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()))
                    .toList();
        }
        return users.stream()
                .sorted(Comparator.comparing(AdminUserRespDTO::getNickname,
                                Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(AdminUserRespDTO::getId))
                .map(user -> {
                    LeadAssignmentUserRespVO result = new LeadAssignmentUserRespVO();
                    result.setId(user.getId());
                    result.setNickname(user.getNickname());
                    result.setDeptId(user.getDeptId());
                    return result;
                }).toList();
    }

    private static List<Long> sortedUserIds(Collection<Long> userIds) {
        return userIds.stream().sorted().toList();
    }

    @Override
    public LeadInboxFilterProfileRespVO getInboxFilterProfile(Long userId, String audience) {
        validateInboxAudiencePermission(audience);
        LeadInboxFilterConfigVO config = inboxFilterConfigService.getPublishedConfig(audience);
        List<LeadInboxFilterProfileRespVO.GroupVO> groups = config.getGroups().stream()
                .filter(group -> Boolean.TRUE.equals(group.getEnabled()))
                .map(this::toProfileGroup)
                .toList();
        return new LeadInboxFilterProfileRespVO(groups);
    }

    private void validateInboxAudiencePermission(String audience) {
        String permission = switch (audience) {
            case INBOX_AUDIENCE_SUBMITTER -> PERMISSION_QUERY_SUBMITTED;
            case INBOX_AUDIENCE_OWNER -> PERMISSION_QUERY_OWNED;
            case null -> null;
            default -> throw exception(LEAD_PERMISSION_DENIED);
        };
        if (permission != null && !securityFrameworkService.hasPermission(permission)) {
            throw exception(LEAD_PERMISSION_DENIED);
        }
    }

    private LeadInboxFilterProfileRespVO.GroupVO toProfileGroup(LeadInboxFilterConfigVO.GroupVO group) {
        List<LeadInboxFilterProfileRespVO.SectionVO> sections = group.getOptions().isEmpty() ? List.of() : List.of(
                new LeadInboxFilterProfileRespVO.SectionVO("current_stage",
                        group.getSectionLabel() == null || group.getSectionLabel().isBlank()
                                ? "当前环节" : group.getSectionLabel(),
                        group.getOptions().stream().filter(option -> Boolean.TRUE.equals(option.getEnabled()))
                                .map(option -> new LeadInboxFilterProfileRespVO.OptionVO(option.getKey(), option.getLabel()))
                                .toList()));
        return new LeadInboxFilterProfileRespVO.GroupVO(group.getKey(), group.getLabel(), sections);
    }

    private Map<Long, OpportunityDO> getOpportunityMap(Collection<Long> leadIds) {
        if (leadIds.isEmpty()) return Map.of();
        return opportunityMapper.selectListByLeadIds(leadIds).stream()
                .collect(Collectors.toMap(OpportunityDO::getLeadId, Function.identity(), (first, ignored) -> first));
    }

    private Map<Long, PartnerDO> getPartnerMap(Collection<LeadDO> leads) {
        List<Long> partnerIds = leads.stream().map(LeadDO::getPartnerId).filter(Objects::nonNull).distinct().toList();
        if (partnerIds.isEmpty()) return Map.of();
        return partnerMapper.selectListByIds(partnerIds).stream()
                .collect(Collectors.toMap(PartnerDO::getId, Function.identity()));
    }

    private LeadManagementRespVO convert(LeadDO lead, Long currentUserId,
                                          Map<Long, AdminUserRespDTO> users,
                                          List<LeadIntendedProductDO> products,
                                          List<LeadAttachmentDO> attachments,
                                          Map<Long, String> attachmentUrls,
                                          boolean detail) {
        return convert(lead, currentUserId, users, products, attachments, attachmentUrls, detail, Map.of(), Map.of());
    }

    private LeadManagementRespVO convert(LeadDO lead, Long currentUserId,
                                          Map<Long, AdminUserRespDTO> users,
                                          List<LeadIntendedProductDO> products,
                                          List<LeadAttachmentDO> attachments,
                                          Map<Long, String> attachmentUrls,
                                          boolean detail,
                                          Map<Long, OpportunityDO> opportunities,
                                          Map<Long, PartnerDO> partners) {
        LeadManagementRespVO result = BeanUtils.toBean(lead, LeadManagementRespVO.class);
        result.setSourceChannel(lead.getSourceChannelId());
        boolean blindIdentity = isBlindIdentity(lead, currentUserId);
        boolean viewerIsOwner = Objects.equals(currentUserId, lead.getOwnerUserId());
        boolean viewerIsSubmitter = PROVIDER_OWNER_SYSTEM_USER.equals(lead.getProviderOwnerType())
                && Objects.equals(currentUserId, lead.getProviderOwnerId());
        boolean selfSourcedWithoutProvider = SOURCE_SALES_SELF.equals(lead.getSourceType())
                && Boolean.TRUE.equals(lead.getSourceProviderRecorded())
                && lead.getSourceProviderUserId() == null;
        result.setSourceUserId(blindIdentity && viewerIsOwner ? null : lead.getSourceUserId());
        result.setSourceUserName(selfSourcedWithoutProvider ? null : (blindIdentity && viewerIsOwner
                ? maskedUserName(users, lead.getSourceUserId()) : userName(users, lead.getSourceUserId())));
        result.setOwnerUserId(blindIdentity && viewerIsSubmitter ? null : lead.getOwnerUserId());
        result.setOwnerUserName(blindIdentity && viewerIsSubmitter
                ? maskedUserName(users, lead.getOwnerUserId()) : userName(users, lead.getOwnerUserId()));
        result.setSourceLabel(sourceLabel(lead.getSourceType()));
        if (SOURCE_PARTNER.equals(lead.getSourceType()) && lead.getPartnerId() != null) {
            var partner = detail ? partnerMapper.selectById(lead.getPartnerId()) : partners.get(lead.getPartnerId());
            String partnerName = partner == null ? null : partner.getName();
            result.setSourceUserName(blindIdentity && viewerIsOwner && partnerName != null
                    ? DesensitizedUtil.chineseName(partnerName) : partnerName);
            result.setSourceUserId(null);
        }
        result.setPendingAssigneeUserName(userName(users, lead.getPendingAssigneeUserId()));
        result.setPartnerOwnerNameSnapshot(lead.getPartnerOwnerNameSnapshot());
        result.setHandlingStage(LeadHandlingStage.resolve(lead));
        result.setQualifiedByUserName(userName(users, lead.getQualifiedByUserId()));
        result.setRecycleSourceOwnerUserName(userName(users, lead.getRecycleSourceOwnerUserId()));
        List<String> relationTypes = new ArrayList<>(2);
        if (PROVIDER_OWNER_SYSTEM_USER.equals(lead.getProviderOwnerType())
                && Objects.equals(currentUserId, lead.getProviderOwnerId())) {
            relationTypes.add("submitter");
        }
        if (Objects.equals(currentUserId, lead.getOwnerUserId())) {
            relationTypes.add("owner");
        }
        if (detail && currentUserId != null
                && leadObjectPermissionService.canReadStudentSalesHistory(lead, currentUserId)) {
            relationTypes.add("student_service_owner");
        }
        result.setRelationTypes(relationTypes);
        result.setOverviewVisible(true);
        List<String> visibleTabs = detail ? resolveVisibleTabs(lead, currentUserId) : List.of();
        result.setVisibleTabs(visibleTabs);
        result.setNextFollowUpAt(null);
        result.setIdentityMaskMode(blindIdentity && (viewerIsOwner || viewerIsSubmitter)
                ? "counterparty_masked" : "full");
        result.setPrimaryProduct(products.stream().filter(item -> Boolean.TRUE.equals(item.getIsPrimary()))
                .findFirst().map(this::convertProduct).orElse(null));
        OpportunityDO opportunity = detail ? opportunityMapper.selectByLeadId(lead.getId()) : opportunities.get(lead.getId());
        // Keep the legacy Lead timestamp internal; customer-facing conversion is opportunity won time.
        result.setConvertedAt(opportunity == null ? null : opportunity.getWonAt());
        result.setQualificationStatus(LeadStateProjection.qualification(lead));
        result.setFollowUpStatus(LeadStateProjection.followUp(lead, opportunity));
        result.setOperationalStatus(LeadStateProjection.operational(lead));
        if (detail) {
            if (visibleTabs.contains(DETAIL_TAB_FOLLOW_UPS)) {
                BusinessTaskDO pendingFollowUp = businessTaskMapper.selectPendingFollowUpReminderByLeadId(lead.getId());
                result.setNextFollowUpAt(pendingFollowUp == null ? null : pendingFollowUp.getDueAt());
            }
            result.setIntendedProducts(products.stream().map(this::convertProduct).toList());
            result.setAttachments(attachments.stream()
                    .map(attachment -> convertAttachment(attachment, attachmentUrls)).toList());
            result.setInvalidEvidence(convertEvidence(lead.getInvalidEvidenceRefs()));
            if (opportunity != null) {
                LeadManagementRespVO.OpportunityVO opportunityVO = new LeadManagementRespVO.OpportunityVO();
                opportunityVO.setId(opportunity.getId()); opportunityVO.setStatus(opportunity.getStatus());
                opportunityVO.setNextFollowUpAt(opportunity.getNextFollowUpAt());
                opportunityVO.setWonAt(opportunity.getWonAt()); result.setOpportunity(opportunityVO);
            }
            SalesOrderDO latestFirstPurchase = salesOrderMapper.selectLatestFirstPurchaseByLeadId(lead.getId());
            if (latestFirstPurchase != null) result.setSalesOrderSubmittedAt(latestFirstPurchase.getSubmittedAt());
            SalesOrderDO activeOrder = salesOrderMapper.selectActiveByLeadId(lead.getId(),
                    cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.ACTIVE_ORDER_STATUSES);
            if (activeOrder != null) {
                result.setActiveSalesOrderId(activeOrder.getId());
                result.setActiveSalesOrderStatus(activeOrder.getStatus());
            }
            result.setAvailableActions(resolveActions(lead, opportunity, activeOrder, currentUserId));
        }
        return result;
    }

    private String resolveRelationScope(LeadManagementPageReqVO reqVO) {
        if (reqVO.getRelationScope() != null) return reqVO.getRelationScope();
        if (INBOX_AUDIENCE_SUBMITTER.equals(reqVO.getAudience())) return "submitted";
        if (INBOX_AUDIENCE_OWNER.equals(reqVO.getAudience())) return "owned";
        return "all";
    }

    private void validateRelationScopePermission(String relationScope) {
        String permission = switch (relationScope) {
            case "submitted" -> PERMISSION_QUERY_SUBMITTED;
            case "owned" -> PERMISSION_QUERY_OWNED;
            case "all" -> null;
            default -> throw exception(LEAD_PERMISSION_DENIED);
        };
        if (permission != null && !securityFrameworkService.hasPermission(permission)) {
            throw exception(LEAD_PERMISSION_DENIED);
        }
    }

    private LeadVisibilityScope resolveVisibilityScope(String relationScope, Long userId) {
        if ("all".equals(relationScope) && leadObjectPermissionService.hasQueryAll()) {
            return new LeadVisibilityScope(List.of(), List.of(), true);
        }
        List<Long> relatedUserIds = sortedUserIds(leadObjectPermissionService.getRelatedAndManagedUserIds(userId));
        boolean submitted = ("all".equals(relationScope) || "submitted".equals(relationScope))
                && securityFrameworkService.hasPermission(PERMISSION_QUERY_SUBMITTED);
        boolean owned = ("all".equals(relationScope) || "owned".equals(relationScope))
                && securityFrameworkService.hasPermission(PERMISSION_QUERY_OWNED);
        return new LeadVisibilityScope(submitted ? relatedUserIds : List.of(),
                owned ? relatedUserIds : List.of(), false);
    }

    private record LeadVisibilityScope(List<Long> sourceUserIds, List<Long> ownerUserIds, boolean queryAll) {}

    @Override
    public LeadManagementRespVO getPartnerLead(Long id, Long partnerId) {
        LeadDO lead = leadMapper.selectById(id);
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        if (!Objects.equals(lead.getPartnerId(), partnerId)) throw exception(LEAD_PERMISSION_DENIED);
        Map<Long, AdminUserRespDTO> users = getUserMap(List.of(lead));
        List<LeadIntendedProductDO> products = intendedProductMapper.selectListByLeadId(id);
        List<LeadAttachmentDO> attachments = attachmentMapper.selectListByLeadId(id);
        return convert(lead, null, users, products, attachments, resolveAttachmentUrls(attachments), true);
    }

    private boolean isBlindIdentity(LeadDO lead, Long currentUserId) {
        // A specified assignment is an explicit mutual identity disclosure between submitter and sales owner.
        return !DISPATCH_SPECIFIED.equals(lead.getDispatchMode())
                && ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus())
                && lead.getSourceUserId() != null && lead.getOwnerUserId() != null
                && !Objects.equals(lead.getSourceUserId(), lead.getOwnerUserId())
                && !leadObjectPermissionService.canViewUnmaskedIdentity(currentUserId, lead);
    }

    private List<String> resolveVisibleTabs(LeadDO lead, Long userId) {
        List<String> tabs = new ArrayList<>();
        tabs.add(DETAIL_TAB_OVERVIEW);
        if (leadObjectPermissionService.canReadSubordinatePartnerLead(lead, userId)) {
            tabs.add(DETAIL_TAB_FOLLOW_UPS);
            tabs.add(DETAIL_TAB_APPEALS);
            tabs.add(DETAIL_TAB_COMPLAINTS);
            tabs.add(DETAIL_TAB_ORDERS);
            tabs.add(DETAIL_TAB_FLOW_HISTORY);
            return tabs;
        }
        if (securityFrameworkService.hasPermission(PERMISSION_DETAIL_FOLLOW_UP_READ)) tabs.add(DETAIL_TAB_FOLLOW_UPS);
        if (canReadAppealRecords(lead, userId)) tabs.add(DETAIL_TAB_APPEALS);
        if (securityFrameworkService.hasPermission(PERMISSION_DETAIL_COMPLAINT_READ)) tabs.add(DETAIL_TAB_COMPLAINTS);
        if (securityFrameworkService.hasPermission(PERMISSION_DETAIL_ORDER_READ)) tabs.add(DETAIL_TAB_ORDERS);
        if (securityFrameworkService.hasPermission(PERMISSION_DETAIL_FLOW_READ)) tabs.add(DETAIL_TAB_FLOW_HISTORY);
        return tabs;
    }

    private boolean canReadAppealRecords(LeadDO lead, Long userId) {
        return PROVIDER_OWNER_SYSTEM_USER.equals(lead.getProviderOwnerType())
                && Objects.equals(lead.getProviderOwnerId(), userId)
                || securityFrameworkService.hasAnyPermissions(PERMISSION_DETAIL_APPEAL_READ,
                PERMISSION_APPEAL_REVIEW_SALES_MANAGER, PERMISSION_APPEAL_REVIEW_QUALITY,
                PERMISSION_APPEAL_REVIEW_CHAIRMAN);
    }

    private static String sourceLabel(String sourceType) {
        if (sourceType == null) return "来源未配置";
        return switch (sourceType) {
            case SOURCE_PARTNER -> "兼职提交";
            case SOURCE_INTERNAL_NEW_MEDIA -> "新媒体提交";
            case SOURCE_SALES_SELF -> "销售自拓录";
            default -> "来源未配置";
        };
    }

    private List<LeadManagementRespVO.ActionVO> resolveActions(LeadDO lead, OpportunityDO opportunity,
                                                                SalesOrderDO activeOrder,
                                                                Long currentUserId) {
        LeadAgingPoolCycleDO agingPoolCycle = agingPoolService.getActiveCycle(lead.getId());
        List<LeadManagementRespVO.ActionVO> actions = new ArrayList<>();
        boolean suspended = STATUS_SUSPENDED.equals(lead.getStatus())
                && ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus());
        boolean recyclePending = ASSIGNMENT_RECYCLE_PENDING.equals(lead.getAssignmentStatus());
        boolean canManageQualification = securityFrameworkService.hasPermission("zsjos:lead:qualification:manage")
                && leadObjectPermissionService.canManageQualificationException(lead, currentUserId);
        if (canManageQualification && (suspended || recyclePending)) {
            if (suspended) {
                actions.add(new LeadManagementRespVO.ActionVO(ACTION_QUALIFICATION_RESTORE, true));
                actions.add(new LeadManagementRespVO.ActionVO(ACTION_QUALIFICATION_RECYCLE, true));
            }
            actions.add(new LeadManagementRespVO.ActionVO(ACTION_QUALIFICATION_TRANSFER, true));
            actions.add(new LeadManagementRespVO.ActionVO(ACTION_QUALIFICATION_RELEASE, true));
        }
        addSupervisorActions(actions, lead, currentUserId);
        if (PROVIDER_OWNER_SYSTEM_USER.equals(lead.getProviderOwnerType())
                && Objects.equals(lead.getProviderOwnerId(), currentUserId)
                && lead.getStatus() != null
                && !Set.of(STATUS_INVALID, STATUS_CLOSED, STATUS_WON).contains(lead.getStatus())) {
            if (securityFrameworkService.hasPermission("zsjos:lead:submitter-supplement")) {
                actions.add(new LeadManagementRespVO.ActionVO(ACTION_SUBMITTER_SUPPLEMENT, true));
            }
            if (lead.getOwnerUserId() != null && securityFrameworkService.hasPermission("zsjos:lead:urge")) {
                actions.add(new LeadManagementRespVO.ActionVO(ACTION_SUBMITTER_URGE, true));
            }
            if (lead.getOwnerUserId() != null && securityFrameworkService.hasPermission("zsjos:lead-complaint:create")) {
                actions.add(new LeadManagementRespVO.ActionVO(ACTION_SUBMITTER_COMPLAINT, true));
            }
        }
        if (!agingPoolService.canOperate(lead.getId(), lead.getOwnerUserId(), currentUserId)
                || OPERATIONAL_SUSPENDED.equals(LeadStateProjection.operational(lead))) return actions;
        boolean canUpdate = securityFrameworkService.hasPermission("zsjos:lead:update");
        boolean canFollow = securityFrameworkService.hasPermission("zsjos:lead-follow-up:create");
        boolean canQualify = securityFrameworkService.hasPermission("zsjos:lead:qualify");
        if (STATUS_SUBMITTED.equals(lead.getStatus()) && ASSIGNMENT_OWNED.equals(lead.getAssignmentStatus())) {
            if (canUpdate) actions.add(new LeadManagementRespVO.ActionVO(ACTION_EDIT_BASIC, true));
            if (canFollow) actions.add(new LeadManagementRespVO.ActionVO(ACTION_ADD_FOLLOW_UP, true));
            if (lead.getQualificationDeadlineAt() != null && canQualify) {
                actions.add(new LeadManagementRespVO.ActionVO(ACTION_JUDGE_VALID, true));
                actions.add(new LeadManagementRespVO.ActionVO(ACTION_JUDGE_INVALID, true));
            }
        } else if (STATUS_VALID.equals(lead.getStatus())
                && (opportunity == null || Set.of(OPPORTUNITY_STATUS_OPEN, OPPORTUNITY_STATUS_FOLLOWING)
                .contains(opportunity.getStatus()))) {
            if (agingPoolCycle == null && canUpdate) actions.add(new LeadManagementRespVO.ActionVO(ACTION_EDIT_BASIC, true));
            if (canFollow) actions.add(new LeadManagementRespVO.ActionVO(ACTION_ADD_FOLLOW_UP, true));
            if (agingPoolCycle == null && canQualify) actions.add(new LeadManagementRespVO.ActionVO(ACTION_JUDGE_INVALID, true));
            boolean canCreateOrder = securityFrameworkService.hasPermission("zsjos:sales-order:create");
            boolean formalOwner = agingPoolCycle == null || Objects.equals(lead.getOwnerUserId(), currentUserId);
            if (activeOrder == null) {
                actions.add(new LeadManagementRespVO.ActionVO(ACTION_ENTER_DEAL, canCreateOrder && formalOwner));
            } else if (cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.STATUS_REVISION_REQUIRED.equals(activeOrder.getStatus())) {
                actions.add(new LeadManagementRespVO.ActionVO(ACTION_REVISE_DEAL,
                        canCreateOrder && formalOwner && salesOrderPermissionService.canRevise(activeOrder, currentUserId)));
            }
        } else if (STATUS_WON.equals(lead.getStatus())) {
            boolean enabled = securityFrameworkService.hasPermission("zsjos:sales-order:create")
                    && lead.getSuspendedAt() == null
                    && agingPoolService.canOperate(lead.getId(), lead.getOwnerUserId(), currentUserId)
                    && salesOrderMapper.hasEffectiveOrder(lead.getPersonId())
                    && salesOrderMapper.selectActiveRepurchaseByPersonId(lead.getPersonId(),
                    cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.ACTIVE_ORDER_STATUSES) == null;
            actions.add(new LeadManagementRespVO.ActionVO(ACTION_ENTER_REPURCHASE, enabled));
        }
        return actions;
    }

    private void addSupervisorActions(List<LeadManagementRespVO.ActionVO> actions, LeadDO lead,
                                      Long currentUserId) {
        if (currentUserId == null) return;
        Long scopedOwner = lead.getOwnerUserId() != null ? lead.getOwnerUserId() : lead.getRecycleSourceOwnerUserId();
        if (scopedOwner == null || !leadObjectPermissionService.getManagedUserIds(currentUserId).contains(scopedOwner)) return;
        if (SupervisorLeadActionPolicy.isAllowed(TRANSFER, lead)
                && securityFrameworkService.hasPermission(PERMISSION_SUPERVISOR_TRANSFER)) {
            actions.add(new LeadManagementRespVO.ActionVO(ACTION_SUPERVISOR_TRANSFER, true));
        }
        if (SupervisorLeadActionPolicy.isAllowed(RECYCLE, lead)
                && securityFrameworkService.hasPermission(PERMISSION_SUPERVISOR_RECYCLE)) {
            actions.add(new LeadManagementRespVO.ActionVO(ACTION_SUPERVISOR_RECYCLE, true));
        }
        if (SupervisorLeadActionPolicy.isAllowed(RELEASE_CLAIM_POOL, lead)
                && securityFrameworkService.hasPermission(PERMISSION_SUPERVISOR_RELEASE_CLAIM_POOL)) {
            actions.add(new LeadManagementRespVO.ActionVO(ACTION_SUPERVISOR_RELEASE_CLAIM_POOL, true));
        }
        if (SupervisorLeadActionPolicy.isAllowed(RELEASE_PUBLIC_SEA, lead)
                && securityFrameworkService.hasPermission(PERMISSION_SUPERVISOR_RELEASE_PUBLIC_SEA)
                && agingPoolService.canEnterManually(lead.getId())) {
            actions.add(new LeadManagementRespVO.ActionVO(ACTION_SUPERVISOR_RELEASE_PUBLIC_SEA, true));
        }
        if (SupervisorLeadActionPolicy.isAllowed(RESTORE, lead)
                && securityFrameworkService.hasPermission(PERMISSION_SUPERVISOR_RESTORE)) {
            actions.add(new LeadManagementRespVO.ActionVO(ACTION_SUPERVISOR_RESTORE, true));
        }
    }

    private LeadManagementRespVO.LeadProductVO convertProduct(LeadIntendedProductDO source) {
        LeadManagementRespVO.LeadProductVO result = new LeadManagementRespVO.LeadProductVO();
        result.setId(source.getId());
        result.setSpuRef(source.getSpuRef());
        result.setSpuName(source.getSpuNameSnapshot());
        result.setSkuRef(source.getSkuRef());
        result.setSkuName(source.getSkuNameSnapshot());
        result.setSelectedAttrValues(source.getSelectedAttrValuesJson());
        result.setPrice(source.getPriceSnapshot());
        result.setCategoryName(source.getCategoryNameSnapshot());
        result.setPrimary(source.getIsPrimary());
        return result;
    }

    private LeadManagementRespVO.LeadAttachmentVO convertAttachment(LeadAttachmentDO source,
                                                                     Map<Long, String> attachmentUrls) {
        LeadManagementRespVO.LeadAttachmentVO result = new LeadManagementRespVO.LeadAttachmentVO();
        result.setId(source.getId());
        String signedUrl = source.getId() == null ? null : attachmentUrls.get(source.getId());
        result.setFileUrl(signedUrl != null ? signedUrl : source.getFileUrl());
        result.setOriginalName(source.getOriginalName());
        result.setContentType(source.getContentType());
        result.setFileSize(source.getFileSize());
        return result;
    }

    private Map<Long, String> resolveAttachmentUrls(List<LeadAttachmentDO> attachments) {
        List<Long> fileIds = attachments.stream().map(LeadAttachmentDO::getInfraFileId)
                .filter(Objects::nonNull).distinct().toList();
        if (fileIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> urlsByFileId = fileApi.presignGetUrls(fileIds, ATTACHMENT_URL_EXPIRATION_SECONDS);
        return attachments.stream().filter(attachment -> attachment.getInfraFileId() != null)
                .collect(Collectors.toMap(LeadAttachmentDO::getId,
                        attachment -> urlsByFileId.get(attachment.getInfraFileId())));
    }

    private List<LeadManagementRespVO.LeadEvidenceVO> convertEvidence(String json) {
        List<EvidenceRef> refs = json == null ? List.of() : JsonUtils.parseArray(json, EvidenceRef.class);
        List<Long> fileIds = refs.stream().map(EvidenceRef::getInfraFileId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> urls = fileIds.isEmpty() ? Map.of()
                : fileApi.presignGetUrls(fileIds, ATTACHMENT_URL_EXPIRATION_SECONDS);
        return refs.stream().map(ref -> {
            LeadManagementRespVO.LeadEvidenceVO result = new LeadManagementRespVO.LeadEvidenceVO();
            result.setInfraFileId(ref.getInfraFileId());
            result.setFileUrl(ref.getInfraFileId() == null ? ref.getFileUrl()
                    : urls.getOrDefault(ref.getInfraFileId(), ref.getFileUrl()));
            result.setOriginalName(ref.getOriginalName());
            result.setContentType(ref.getContentType());
            result.setFileSize(ref.getFileSize());
            result.setSort(ref.getSort());
            return result;
        }).toList();
    }

    private Map<Long, AdminUserRespDTO> getUserMap(Collection<LeadDO> leads) {
        Set<Long> userIds = new LinkedHashSet<>();
        for (LeadDO lead : leads) {
            addIfPresent(userIds, lead.getSourceUserId());
            if (PROVIDER_OWNER_SYSTEM_USER.equals(lead.getProviderOwnerType())) {
                addIfPresent(userIds, lead.getProviderOwnerId());
            }
            addIfPresent(userIds, lead.getOwnerUserId());
            addIfPresent(userIds, lead.getPendingAssigneeUserId());
            addIfPresent(userIds, lead.getQualifiedByUserId());
            addIfPresent(userIds, lead.getRecycleSourceOwnerUserId());
        }
        return userIds.isEmpty() ? Collections.emptyMap() : adminUserApi.getUserMap(userIds);
    }

    private static void addIfPresent(Set<Long> values, Long value) {
        if (value != null) {
            values.add(value);
        }
    }

    private static String userName(Map<Long, AdminUserRespDTO> users, Long userId) {
        AdminUserRespDTO user = userId == null ? null : users.get(userId);
        return user == null ? null : user.getNickname();
    }

    private static String maskedUserName(Map<Long, AdminUserRespDTO> users, Long userId) {
        String name = userName(users, userId);
        return name == null ? null : DesensitizedUtil.chineseName(name);
    }

    private static <T> Map<Long, List<T>> groupByLeadId(List<T> values, Function<T, Long> keyFunction) {
        return values.stream().collect(Collectors.groupingBy(keyFunction));
    }

    @Data
    private static class EvidenceRef {
        private Long infraFileId;
        private String fileUrl;
        private String originalName;
        private String contentType;
        private Long fileSize;
        private Integer sort;
    }
}
