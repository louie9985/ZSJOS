package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.ip.core.Area;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.ip.AreaApi;
import cn.iocoder.yudao.module.system.api.ip.dto.AreaRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadSubmitterSupplementReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadSubmitterAssistRequestReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadUrgeReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentReqVO;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadProductReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.*;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.lead.product.LeadProductSnapshot;
import cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductSkuService;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerOwnershipService;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerOwnershipDO;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCommandService;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskCreateCommand;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class LeadSubmitterActionService {
    @Resource private LeadMapper leadMapper; @Resource private LeadIntendedProductMapper productMapper;
    @Resource private AreaApi areaApi;
    @Resource private ZsjosProductSkuService productSkuService; @Resource private BusinessEventMapper eventMapper;
    @Resource private LeadUrgeMapper urgeMapper; @Resource private LeadNotifyEventPublisher notifyPublisher;
    @Resource private LeadSubmissionIdentityService identityService;
    @Resource private LeadCategorySnapshotService categorySnapshotService;
    @Resource private LeadSubmitterAssistRequestMapper assistRequestMapper;
    @Resource private LeadAttachmentService attachmentService;
    @Resource private LeadObjectPermissionService objectPermissionService;
    @Resource private PartnerOwnershipService partnerOwnershipService;
    @Resource private BusinessTaskCommandService businessTaskCommandService;
    @Resource private cn.iocoder.yudao.module.system.api.user.AdminUserApi adminUserApi;
    @Resource private PartnerMapper partnerMapper;

    @Transactional(rollbackFor = Exception.class)
    public void supplement(Long leadId, Long userId, LeadSubmitterSupplementReqVO req) {
        supplementInternal(leadId, userId, null, req);
    }

    @Transactional(rollbackFor = Exception.class)
    public void supplementForPartner(Long leadId, Long partnerId, LeadSubmitterSupplementReqVO req) {
        supplementInternal(leadId, null, partnerId, req);
    }

    private void supplementInternal(Long leadId, Long userId, Long partnerId, LeadSubmitterSupplementReqVO req) {
        LeadDO lead = requireSubmitterLeadForUpdate(leadId, userId, partnerId);
        String digest = supplementDigest(req);
        String subjectType = partnerId == null ? PROVIDER_OWNER_SYSTEM_USER : PROVIDER_OWNER_PARTNER;
        Long subjectId = partnerId == null ? userId : partnerId;
        BusinessEventDO replay = eventMapper.selectByIdempotencyKeyForUpdate(req.getIdempotencyKey());
        if (replay != null) {
            Map<?, ?> payload = LeadRemarkHistoryService.payload(replay);
            if (!LeadSupplementSnapshot.EVENT.equals(replay.getEventType())
                    || !BIZ_TYPE_LEAD.equals(replay.getAggregateType()) || !leadId.equals(replay.getAggregateId())
                    || payload == null || !digest.equals(payload.get("requestDigest"))
                    || !subjectType.equals(payload.get("submitterType"))
                    || !(payload.get("submitterId") instanceof Number id) || id.longValue() != subjectId) {
                throw exception(LEAD_SUPPLEMENT_IDEMPOTENCY_CONFLICT);
            }
            return;
        }
        requireActionable(lead);
        Region region = region(req.getProvinceCode(), req.getCityCode());
        LeadCategorySnapshotService.Selection category = Objects.equals(lead.getLeadCategory(), req.getLeadCategory())
                ? null : categorySnapshotService.requireEnabled(req.getLeadCategory());
        List<LeadProductSnapshot> snapshots = products(req.getIntendedProducts());
        Map<String,Object> before = Map.of("provinceCode", Objects.toString(lead.getProvinceCode(), ""),
                "cityCode", Objects.toString(lead.getCityCode(), ""), "leadCategory", Objects.toString(lead.getLeadCategory(), ""),
                "remark", Objects.toString(lead.getRemark(), ""));
        lead.setProvinceCode(region.provinceCode()); lead.setProvinceName(region.provinceName());
        lead.setCityCode(region.cityCode()); lead.setCityName(region.cityName());
        if (category != null) {
            lead.setLeadCategory(category.value());
            lead.setLeadCategoryLabelSnapshot(category.labelSnapshot());
        }
        LocalDateTime now = LocalDateTime.now();
        LeadMapper.advanceActivity(lead, now);
        leadMapper.updateById(lead);
        productMapper.deleteByLeadId(leadId); insertProducts(leadId, req.getIntendedProducts(), snapshots);
        BusinessEventDO event = new BusinessEventDO(); event.setEventType("lead_submitter_supplemented");
        event.setAggregateType(BIZ_TYPE_LEAD); event.setAggregateId(leadId); event.setOperatorUserId(userId);
        String subjectName;
        if (partnerId != null) {
            var partner = partnerMapper.selectById(partnerId);
            subjectName = partner == null ? null : partner.getName();
        } else {
            var user = adminUserApi.getUser(userId);
            subjectName = user == null ? null : user.getNickname();
        }
        event.setRelatedObjectRefs(JsonUtils.toJsonString(new LeadSupplementSnapshot(before,
                LeadSupplementSnapshot.MODE, Objects.toString(StrUtil.trimToNull(req.getRemark()), ""),
                subjectType, subjectId, subjectName, digest)));
        event.setOccurredAt(now);
        event.setIdempotencyKey(req.getIdempotencyKey());
        try { eventMapper.insert(event); }
        catch (DuplicateKeyException ex) { throw exception(LEAD_SUPPLEMENT_IDEMPOTENCY_CONFLICT); }
    }

    static String supplementDigest(LeadSubmitterSupplementReqVO req) {
        List<List<Object>> products = req.getIntendedProducts() == null ? List.of() : req.getIntendedProducts().stream()
                .map(p -> List.<Object>of(Objects.toString(p.effectiveSpuRef(), ""), Objects.toString(p.getSkuRef(), ""),
                        Boolean.TRUE.equals(p.getSpuUnknown()), Boolean.TRUE.equals(p.getSkuUnknown()), Boolean.TRUE.equals(p.getPrimary())))
                .toList();
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(Arrays.asList(req.getProvinceCode(), req.getCityCode(),
                req.getLeadCategory(), products, StrUtil.trimToNull(req.getRemark()))));
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

    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = BIZ_TYPE_LEAD, bizId = "#leadId", action = "request-submitter-assist")
    public Long requestAssist(Long leadId, Long userId, LeadSubmitterAssistRequestReqVO req) {
        objectPermissionService.check(leadId, "request-submitter-assist");
        List<LeadAttachmentReqVO> attachments = req.getAttachments() == null ? List.of() : req.getAttachments();
        Map<Long, FileInfoRespDTO> files = attachmentService.validateReferences(attachments, userId);
        String problem = req.getProblem().trim();
        String expectedAssistance = req.getExpectedAssistance().trim();
        String remark = StrUtil.trimToNull(req.getRemark());
        List<Map<String, Object>> attachmentSnapshots = files.values().stream().map(file -> {
            Map<String, Object> snapshot = new LinkedHashMap<String, Object>();
            snapshot.put("infraFileId", file.getId());
            snapshot.put("name", file.getName());
            snapshot.put("type", file.getType());
            snapshot.put("size", file.getSize());
            return snapshot;
        }).toList();
        String fingerprint = DigestUtil.sha256Hex(JsonUtils.toJsonString(
                List.of(leadId, userId, problem, expectedAssistance, Objects.toString(remark, ""), attachmentSnapshots)));
        LeadSubmitterAssistRequestDO replay = assistRequestMapper.selectByIdempotencyKey(req.getIdempotencyKey());
        if (replay != null) {
            if (!Objects.equals(replay.getRequestFingerprint(), fingerprint)) {
                throw exception(LEAD_SUBMITTER_ASSIST_IDEMPOTENCY_CONFLICT);
            }
            return replay.getId();
        }

        LeadDO lead = leadMapper.selectByIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        AssistRecipient recipient = resolveAssistRecipient(lead);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        LeadSubmitterAssistRequestDO row = new LeadSubmitterAssistRequestDO();
        row.setLeadId(leadId); row.setLeadNoSnapshot(lead.getLeadNo()); row.setRequesterUserId(userId);
        row.setProblem(problem); row.setExpectedAssistance(expectedAssistance); row.setRemark(remark);
        row.setAttachmentSnapshotsJson(JsonUtils.toJsonString(attachmentSnapshots));
        row.setSubmitterTypeSnapshot(recipient.submitterType()); row.setSubmitterIdSnapshot(recipient.submitterId());
        row.setSubmitterNameSnapshot(recipient.submitterName()); row.setAssigneeUserIdSnapshot(recipient.assigneeUserId());
        row.setAssigneeNameSnapshot(recipient.assigneeName()); row.setRequestedAt(now);
        row.setRequestFingerprint(fingerprint); row.setIdempotencyKey(req.getIdempotencyKey());
        try {
            assistRequestMapper.insert(row);
        } catch (DuplicateKeyException duplicate) {
            replay = assistRequestMapper.selectByIdempotencyKey(req.getIdempotencyKey());
            if (replay == null || !Objects.equals(replay.getRequestFingerprint(), fingerprint)) {
                throw exception(LEAD_SUBMITTER_ASSIST_IDEMPOTENCY_CONFLICT);
            }
            return replay.getId();
        }

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("assist.requestId", row.getId()); context.put("assist.problem", problem);
        context.put("assist.expectedAssistance", expectedAssistance);
        context.put("assist.remark", Objects.toString(remark, ""));
        context.put("assist.attachmentNames", files.values().stream().map(FileInfoRespDTO::getName).toList());
        context.put("partnerOwnerUserId", recipient.assigneeUserId());
        BusinessEventDO event = new BusinessEventDO();
        event.setEventType("lead_submitter_assist_requested"); event.setAggregateType(BIZ_TYPE_LEAD);
        event.setAggregateId(leadId); event.setOperatorUserId(userId); event.setReason(problem);
        event.setEvidenceRefs(JsonUtils.toJsonString(attachmentSnapshots));
        event.setRelatedObjectRefs(JsonUtils.toJsonString(context)); event.setOccurredAt(now);
        event.setIdempotencyKey("assist-event:" + row.getId()); eventMapper.insert(event);

        if (recipient.assigneeUserId() != null) {
            String taskLeadNo = StrUtil.isBlank(lead.getLeadNo()) ? "客资记录不可用" : lead.getLeadNo();
            businessTaskCommandService.create(new BusinessTaskCreateCommand(TASK_TYPE_SUBMITTER_ASSIST,
                    BIZ_TYPE_LEAD, leadId, recipient.assigneeUserId(), "提交人协助：" + taskLeadNo,
                    problem, "OPEN_LEAD_SUBMITTER_ASSIST", null, null, JsonUtils.toJsonString(context),
                    "lead-submitter-assist:" + row.getId()));
        }
        notifyPublisher.publish(SUBMITTER_ASSIST_REQUESTED, leadId, "lead-submitter-assist-message:" + row.getId(),
                userId, now, context);
        if (PROVIDER_OWNER_PARTNER.equals(recipient.submitterType()) && recipient.assigneeUserId() != null) {
            notifyPublisher.publish(PARTNER_ASSIST_REMINDER, leadId, "lead-partner-assist-reminder:" + row.getId(),
                    userId, now, context);
        }
        return row.getId();
    }

    private AssistRecipient resolveAssistRecipient(LeadDO lead) {
        if (PROVIDER_OWNER_SYSTEM_USER.equals(lead.getProviderOwnerType()) && lead.getProviderOwnerId() != null) {
            return new AssistRecipient(PROVIDER_OWNER_SYSTEM_USER, lead.getProviderOwnerId(),
                    lead.getProviderOwnerNameSnapshot(), lead.getProviderOwnerId(), lead.getProviderOwnerNameSnapshot());
        }
        if (PROVIDER_OWNER_PARTNER.equals(lead.getProviderOwnerType()) && lead.getProviderOwnerId() != null) {
            PartnerOwnershipDO ownership = partnerOwnershipService.getByPartnerId(lead.getProviderOwnerId());
            Long assigneeId = ownership != null && ownership.getEmployeeUserId() != null
                    ? ownership.getEmployeeUserId() : lead.getPartnerOwnerUserIdSnapshot();
            String assigneeName = ownership != null && ownership.getEmployeeUserId() != null
                    ? ownership.getEmployeeNameSnapshot() : lead.getPartnerOwnerNameSnapshot();
            if (assigneeId == null) {
                throw exception(LEAD_SUBMITTER_ASSIST_RECIPIENT_MISSING);
            }
            return new AssistRecipient(PROVIDER_OWNER_PARTNER, lead.getProviderOwnerId(),
                    lead.getProviderOwnerNameSnapshot(), assigneeId, assigneeName);
        }
        throw exception(LEAD_SUBMITTER_ASSIST_RECIPIENT_MISSING);
    }

    private LeadDO requireSubmitterLeadForUpdate(Long leadId, Long userId, Long partnerId) {
        LeadDO lead = leadMapper.selectByIdForUpdate(leadId, TenantContextHolder.getRequiredTenantId());
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        if (partnerId != null) {
            if (!PROVIDER_OWNER_PARTNER.equals(lead.getProviderOwnerType())
                    || !Objects.equals(lead.getProviderOwnerId(), partnerId)) throw exception(LEAD_PERMISSION_DENIED);
        } else {
            if (!PROVIDER_OWNER_SYSTEM_USER.equals(lead.getProviderOwnerType())
                    || !Objects.equals(lead.getProviderOwnerId(), userId)) throw exception(LEAD_PERMISSION_DENIED);
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
    private record AssistRecipient(String submitterType, Long submitterId, String submitterName,
                                   Long assigneeUserId, String assigneeName) {}
}
