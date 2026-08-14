package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadInboxFilterProfileRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadBasicInfoUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.inboxfilter.LeadInboxFilterConfigVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAttachmentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadIntendedProductDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.SalesOrderDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAttachmentMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadIntendedProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.SalesOrderMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
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

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_NOT_EXISTS;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.ATTACHMENT_URL_EXPIRATION_SECONDS;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.INBOX_AUDIENCE_OWNER;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.INBOX_AUDIENCE_SUBMITTER;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.PERMISSION_QUERY_OWNED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.PERMISSION_QUERY_SUBMITTED;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;

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

    @Override
    public PageResult<LeadManagementRespVO> getLeadPage(LeadManagementPageReqVO reqVO, Long userId) {
        validateInboxAudiencePermission(reqVO.getAudience());
        boolean queryAll = leadObjectPermissionService.hasQueryAll();
        LeadInboxFilterQuery inboxQuery = reqVO.getAudience() == null
                ? new LeadInboxFilterQuery(Set.of(), Set.of(), false)
                : inboxFilterConfigService.resolveQuery(
                        inboxFilterConfigService.getPublishedConfig(reqVO.getAudience()),
                        reqVO.getInboxGroup(), reqVO.getInboxStage());
        Long visibleUserId = reqVO.getAudience() != null || !queryAll ? userId : null;
        List<Long> managedOwnerUserIds = reqVO.getAudience() == null && !queryAll
                ? sortedUserIds(leadObjectPermissionService.getManagedUserIds(userId)) : List.of();
        PageResult<LeadDO> page = managedOwnerUserIds.isEmpty()
                ? leadMapper.selectManagementPage(reqVO, visibleUserId,
                        List.copyOf(inboxQuery.statuses()), List.copyOf(inboxQuery.assignmentStatuses()), inboxQuery.matchNone())
                : leadMapper.selectManagementPage(reqVO, visibleUserId, managedOwnerUserIds,
                        List.copyOf(inboxQuery.statuses()), List.copyOf(inboxQuery.assignmentStatuses()), inboxQuery.matchNone());
        if (page.getList().isEmpty()) {
            return PageResult.empty(page.getTotal());
        }
        List<Long> leadIds = page.getList().stream().map(LeadDO::getId).toList();
        Map<Long, List<LeadIntendedProductDO>> products = groupByLeadId(
                intendedProductMapper.selectListByLeadIds(leadIds), LeadIntendedProductDO::getLeadId);
        Map<Long, AdminUserRespDTO> users = getUserMap(page.getList());
        List<LeadManagementRespVO> result = page.getList().stream()
                .map(lead -> convert(lead, userId, users, products.getOrDefault(lead.getId(), List.of()),
                        List.of(), Map.of(), false))
                .toList();
        return new PageResult<>(result, page.getTotal());
    }

    @Override
    @ZsjosPermission(bizType = "lead", bizId = "#id", action = "read")
    public LeadManagementRespVO getLead(Long id, Long userId) {
        LeadDO lead = leadMapper.selectById(id);
        if (lead == null) {
            throw exception(LEAD_NOT_EXISTS);
        }
        if (!leadObjectPermissionService.canRead(lead, userId)) {
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
        boolean queryAll = leadObjectPermissionService.hasQueryAll();
        List<Long> managedUserIds = queryAll ? List.of()
                : sortedUserIds(leadObjectPermissionService.getManagedUserIds(userId));
        return leadMapper.selectManagementStatusCounts(queryAll ? null : userId, managedUserIds);
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
        List<Map<String, Object>> rows = leadMapper.selectManagementInboxStateCounts(userId, audience);
        List<LeadInboxFilterProfileRespVO.GroupVO> groups = config.getGroups().stream()
                .filter(group -> Boolean.TRUE.equals(group.getEnabled()))
                .map(group -> toProfileGroup(config, group, rows))
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

    private LeadInboxFilterProfileRespVO.GroupVO toProfileGroup(
            LeadInboxFilterConfigVO config, LeadInboxFilterConfigVO.GroupVO group, List<Map<String, Object>> rows) {
        LeadInboxFilterQuery groupQuery = inboxFilterConfigService.resolveQuery(config, group.getKey(), "all");
        long groupCount = countRows(rows, groupQuery);
        List<LeadInboxFilterProfileRespVO.SectionVO> sections = group.getOptions().isEmpty() ? List.of() : List.of(
                new LeadInboxFilterProfileRespVO.SectionVO("current_stage",
                        group.getSectionLabel() == null || group.getSectionLabel().isBlank()
                                ? "当前环节" : group.getSectionLabel(),
                        group.getOptions().stream().filter(option -> Boolean.TRUE.equals(option.getEnabled()))
                                .map(option -> new LeadInboxFilterProfileRespVO.OptionVO(option.getKey(), option.getLabel(),
                                        countRows(rows, inboxFilterConfigService.resolveQuery(
                                                config, group.getKey(), option.getKey()))))
                                .toList()));
        return new LeadInboxFilterProfileRespVO.GroupVO(group.getKey(), group.getLabel(), groupCount, sections);
    }

    private static long countRows(List<Map<String, Object>> rows, LeadInboxFilterQuery query) {
        return rows.stream().filter(row -> query.matches(stringValue(row.get("status")),
                        stringValue(row.get("assignment_status"))))
                .mapToLong(row -> row.get("total") instanceof Number number ? number.longValue() : 0L)
                .sum();
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private LeadManagementRespVO convert(LeadDO lead, Long currentUserId,
                                          Map<Long, AdminUserRespDTO> users,
                                          List<LeadIntendedProductDO> products,
                                          List<LeadAttachmentDO> attachments,
                                          Map<Long, String> attachmentUrls,
                                          boolean detail) {
        LeadManagementRespVO result = BeanUtils.toBean(lead, LeadManagementRespVO.class);
        result.setSourceChannel(lead.getSourceChannelId());
        result.setSourceUserName(userName(users, lead.getSourceUserId()));
        result.setOwnerUserName(userName(users, lead.getOwnerUserId()));
        result.setPendingAssigneeUserName(userName(users, lead.getPendingAssigneeUserId()));
        result.setHandlingStage(LeadHandlingStage.resolve(lead));
        result.setQualifiedByUserName(userName(users, lead.getQualifiedByUserId()));
        result.setRecycleSourceOwnerUserName(userName(users, lead.getRecycleSourceOwnerUserId()));
        List<String> relationTypes = new ArrayList<>(2);
        if (Objects.equals(currentUserId, lead.getSourceUserId())) {
            relationTypes.add("submitter");
        }
        if (Objects.equals(currentUserId, lead.getOwnerUserId())) {
            relationTypes.add("owner");
        }
        result.setRelationTypes(relationTypes);
        result.setPrimaryProduct(products.stream().filter(item -> Boolean.TRUE.equals(item.getIsPrimary()))
                .findFirst().map(this::convertProduct).orElse(null));
        OpportunityDO opportunity = opportunityMapper.selectByLeadId(lead.getId());
        result.setQualificationStatus(LeadStateProjection.qualification(lead));
        result.setFollowUpStatus(LeadStateProjection.followUp(lead, opportunity));
        result.setOperationalStatus(LeadStateProjection.operational(lead));
        if (detail) {
            result.setIntendedProducts(products.stream().map(this::convertProduct).toList());
            result.setAttachments(attachments.stream()
                    .map(attachment -> convertAttachment(attachment, attachmentUrls)).toList());
            result.setInvalidEvidence(convertEvidence(lead.getInvalidEvidenceRefs()));
            if (opportunity != null) {
                LeadManagementRespVO.OpportunityVO opportunityVO = new LeadManagementRespVO.OpportunityVO();
                opportunityVO.setId(opportunity.getId()); opportunityVO.setStatus(opportunity.getStatus());
                opportunityVO.setNextFollowUpAt(opportunity.getNextFollowUpAt()); result.setOpportunity(opportunityVO);
            }
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

    private List<LeadManagementRespVO.ActionVO> resolveActions(LeadDO lead, OpportunityDO opportunity,
                                                                SalesOrderDO activeOrder,
                                                                Long currentUserId) {
        if (!Objects.equals(currentUserId, lead.getOwnerUserId())
                || OPERATIONAL_SUSPENDED.equals(LeadStateProjection.operational(lead))) return List.of();
        List<LeadManagementRespVO.ActionVO> actions = new ArrayList<>();
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
        } else if ((STATUS_VALID.equals(lead.getStatus()) || "converted".equals(lead.getStatus()))
                && (opportunity == null || Set.of(OPPORTUNITY_STATUS_OPEN, OPPORTUNITY_STATUS_FOLLOWING)
                .contains(opportunity.getStatus()))) {
            if (canUpdate) actions.add(new LeadManagementRespVO.ActionVO(ACTION_EDIT_BASIC, true));
            if (canFollow) actions.add(new LeadManagementRespVO.ActionVO(ACTION_ADD_FOLLOW_UP, true));
            if (canQualify) actions.add(new LeadManagementRespVO.ActionVO(ACTION_JUDGE_INVALID, true));
            boolean canCreateOrder = securityFrameworkService.hasPermission("zsjos:sales-order:create");
            if (activeOrder == null) {
                actions.add(new LeadManagementRespVO.ActionVO(ACTION_ENTER_DEAL, canCreateOrder));
            } else if (cn.iocoder.yudao.module.zsjos.enums.SalesOrderConstants.STATUS_REVISION_REQUIRED.equals(activeOrder.getStatus())) {
                actions.add(new LeadManagementRespVO.ActionVO(ACTION_REVISE_DEAL, canCreateOrder));
            }
        }
        return actions;
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
