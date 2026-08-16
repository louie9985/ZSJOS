package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.*;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.appeal.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentUploadRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadAppealDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.OpportunityDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.event.BusinessEventDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.event.BusinessEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadAppealMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadIntendedProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.OpportunityMapper;
import cn.iocoder.yudao.module.zsjos.service.cashback.CashbackService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
@Slf4j
public class LeadAppealServiceImpl implements LeadAppealService {

    private static final String APPEAL_REVIEW_STAGE_VARIABLE = "reviewStage";

    @Resource private LeadAppealMapper appealMapper;
    @Resource private LeadMapper leadMapper;
    @Resource private BusinessEventMapper eventMapper;
    @Resource private LeadNotifyEventPublisher notifyEventPublisher;
    @Resource private LeadAttachmentService attachmentService;
    @Resource private FileApi fileApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;
    @Resource private RoleApi roleApi;
    @Resource private PermissionApi permissionApi;
    @Resource private BpmProcessInstanceApi processInstanceApi;
    @Resource private BpmProcessTaskApi processTaskApi;
    @Resource private OpportunityMapper opportunityMapper;
    @Resource private LeadIntendedProductMapper intendedProductMapper;
    @Resource private CashbackService cashbackService;

    @Override
    public List<LeadAppealRespVO> getLeadAppeals(Long leadId, Long userId) {
        LeadDO lead = requireLead(leadId);
        if (!Objects.equals(lead.getSourceUserId(), userId) && !Objects.equals(lead.getOwnerUserId(), userId)) {
            throw exception(LEAD_APPEAL_PERMISSION_DENIED);
        }
        return appealMapper.selectListByLeadId(leadId).stream().map(item -> convert(item, lead, null)).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(Long leadId, Long userId, LeadAppealSubmitReqVO reqVO) {
        LeadDO lead = requireLeadForUpdate(leadId);
        LeadAppealDO duplicate = appealMapper.selectBySubmissionIdempotencyKey(reqVO.getIdempotencyKey());
        if (duplicate != null) {
            if (Objects.equals(duplicate.getLeadId(), leadId) && Objects.equals(duplicate.getApplicantUserId(), userId)) {
                return duplicate.getId();
            }
            throw exception(LEAD_APPEAL_IDEMPOTENCY_CONFLICT);
        }
        if (!STATUS_INVALID.equals(lead.getStatus()) || !Objects.equals(lead.getSourceUserId(), userId)) {
            throw exception(LEAD_APPEAL_STATE_INVALID);
        }
        LeadAppealDO previous = appealMapper.selectLatestByLeadId(leadId);
        int roundNo = previous == null ? 1 : previous.getRoundNo() + 1;
        if (roundNo > 3 || previous != null && !APPEAL_STATUS_UPHELD.equals(previous.getStatus())) {
            throw exception(LEAD_APPEAL_STATE_INVALID);
        }
        ReviewerResolution resolution = resolveReviewers(roundNo, lead);
        List<Long> reviewers = resolution.reviewerUserIds();
        String evidence = buildEvidenceJson(reqVO.getAttachments(), userId);
        LocalDateTime now = LocalDateTime.now();
        LeadAppealDO appeal = new LeadAppealDO();
        appeal.setLeadId(leadId);
        appeal.setRoundNo(roundNo);
        appeal.setReviewStage(stage(roundNo));
        appeal.setStatus(reviewingStatus(roundNo));
        appeal.setOwnerUserIdSnapshot(resolution.ownerUserId());
        appeal.setOwnerDeptIdSnapshot(resolution.ownerDeptId());
        appeal.setReviewerDeptIdSnapshot(resolution.reviewerDeptId());
        appeal.setReviewerUserIdsSnapshot(JsonUtils.toJsonString(reviewers));
        appeal.setApplicantUserId(userId);
        appeal.setReason(reqVO.getReason().trim());
        appeal.setEvidenceRefs(evidence);
        appeal.setInvalidReasonSnapshot(lead.getInvalidReasonLabelSnapshot());
        appeal.setInvalidDescriptionSnapshot(lead.getInvalidDescription());
        appeal.setInvalidEvidenceRefsSnapshot(lead.getInvalidEvidenceRefs());
        appeal.setSubmittedAt(now);
        appeal.setSubmissionIdempotencyKey(reqVO.getIdempotencyKey());
        appealMapper.insert(appeal);
        try {
            BpmProcessInstanceCreateReqDTO processReq = new BpmProcessInstanceCreateReqDTO();
            processReq.setProcessDefinitionKey(APPEAL_PROCESS_DEFINITION_KEY);
            processReq.setBusinessKey(APPEAL_BUSINESS_KEY_PREFIX + appeal.getId());
            Map<String, Object> processVariables = new LinkedHashMap<>();
            processVariables.put("appealId", appeal.getId());
            processVariables.put("leadId", leadId);
            processVariables.put("roundNo", roundNo);
            processVariables.put("reviewStage", appeal.getReviewStage());
            processReq.setVariables(processVariables);
            processReq.setStartUserSelectAssignees(Map.of(APPEAL_TASK_DEFINITION_KEY, reviewers));
            appeal.setProcessInstanceId(processInstanceApi.createProcessInstance(userId, processReq));
        } catch (RuntimeException ex) {
            log.error("[submit][leadId({}) roundNo({}) processDefinitionKey({}) taskDefinitionKey({}) reviewerCount({}) BPM process start failed]",
                    leadId, roundNo, APPEAL_PROCESS_DEFINITION_KEY, APPEAL_TASK_DEFINITION_KEY, reviewers.size(), ex);
            throw exception(LEAD_APPEAL_PROCESS_UNAVAILABLE);
        }
        appealMapper.updateById(appeal);
        BusinessEventDO event = addEvent(EVENT_LEAD_APPEAL_SUBMITTED, lead, userId, appeal,
                STATUS_INVALID, appeal.getStatus(), appeal.getReason(), appeal.getEvidenceRefs(), now,
                reqVO.getIdempotencyKey());
        Map<String, Object> context = appealContext(lead, appeal);
        context.put("appeal.reviewerUserIds", reviewers);
        notifyEventPublisher.publish(cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.APPEAL_SUBMITTED,
                leadId, event.getIdempotencyKey(), userId, now, context);
        return appeal.getId();
    }

    @Override
    public PageResult<LeadAppealRespVO> getInboxPage(LeadAppealPageReqVO reqVO, Long userId) {
        Set<String> reviewPermissions = Set.of(PERMISSION_APPEAL_REVIEW_SALES_MANAGER,
                        PERMISSION_APPEAL_REVIEW_QUALITY, PERMISSION_APPEAL_REVIEW_CHAIRMAN).stream()
                .filter(permission -> permissionApi.hasAnyPermissions(userId, permission))
                .collect(Collectors.toSet());
        if (reviewPermissions.isEmpty()) return PageResult.empty();
        AdminUserRespDTO reviewer = adminUserApi.getUser(userId);
        if (reviewer == null || !CommonStatusEnum.ENABLE.getStatus().equals(reviewer.getStatus())) {
            return PageResult.empty();
        }
        BpmTaskPageReqDTO taskReq = new BpmTaskPageReqDTO();
        taskReq.setPageNo(reqVO.getPageNo()); taskReq.setPageSize(reqVO.getPageSize());
        taskReq.setProcessDefinitionKey(APPEAL_PROCESS_DEFINITION_KEY);
        taskReq.setTaskDefinitionKey(APPEAL_TASK_DEFINITION_KEY);
        taskReq.setProcessVariableName(APPEAL_REVIEW_STAGE_VARIABLE);
        taskReq.setProcessVariableValues(new ArrayList<>(reviewStages(reviewPermissions)));
        PageResult<BpmTaskRespDTO> taskPage = Boolean.TRUE.equals(reqVO.getHandled())
                ? processTaskApi.getDoneTaskPage(userId, taskReq) : processTaskApi.getTodoTaskPage(userId, taskReq);
        Set<Long> appealIds = taskPage.getList().stream().map(BpmTaskRespDTO::getBusinessKey)
                .map(this::parseAppealId).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, LeadAppealDO> appeals = appealIds.isEmpty() ? Map.of() : appealMapper.selectBatchIds(appealIds).stream()
                .collect(Collectors.toMap(LeadAppealDO::getId, appeal -> appeal, (left, right) -> left));
        Set<Long> leadIds = appeals.values().stream().map(LeadAppealDO::getLeadId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, LeadDO> leads = leadIds.isEmpty() ? Map.of() : leadMapper.selectBatchIds(leadIds).stream()
                .collect(Collectors.toMap(LeadDO::getId, lead -> lead, (left, right) -> left));
        List<BpmTaskRespDTO> selectedTasks = new ArrayList<>();
        List<LeadAppealDO> selectedAppeals = new ArrayList<>();
        List<LeadDO> selectedLeads = new ArrayList<>();
        for (BpmTaskRespDTO task : taskPage.getList()) {
            Long appealId = parseAppealId(task.getBusinessKey());
            LeadAppealDO appeal = appealId == null ? null : appeals.get(appealId);
            if (appeal == null || !canReviewStage(appeal, reviewPermissions) || !canReview(appeal, userId, reviewer)
                    || !Objects.equals(task.getBusinessKey(), APPEAL_BUSINESS_KEY_PREFIX + appealId)
                    || !Objects.equals(task.getProcessInstanceId(), appeal.getProcessInstanceId())) {
                log.error("[getInboxPage][userId({}) taskId({}) processInstanceId({}) appealId({}) task correlation invalid]",
                        userId, task.getId(), task.getProcessInstanceId(), appealId);
                throw exception(LEAD_APPEAL_PROCESS_UNAVAILABLE);
            }
            LeadDO lead = leads.get(appeal.getLeadId());
            if (lead == null) {
                log.error("[getInboxPage][userId({}) taskId({}) appealId({}) leadId({}) lead missing]",
                        userId, task.getId(), appealId, appeal.getLeadId());
                throw exception(LEAD_APPEAL_PROCESS_UNAVAILABLE);
            }
            selectedTasks.add(task); selectedAppeals.add(appeal); selectedLeads.add(lead);
        }
        Set<Long> displayUserIds = selectedAppeals.stream()
                .flatMap(appeal -> java.util.stream.Stream.of(appeal.getApplicantUserId(), appeal.getReviewerUserId()))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> userNames = displayUserIds.isEmpty() ? Map.of() : adminUserApi.getUserList(displayUserIds).stream()
                .filter(user -> user.getId() != null && user.getNickname() != null)
                .collect(Collectors.toMap(AdminUserRespDTO::getId, AdminUserRespDTO::getNickname,
                        (left, right) -> left));
        List<LeadAppealRespVO> result = new ArrayList<>();
        for (int i = 0; i < selectedAppeals.size(); i++) {
            result.add(convert(selectedAppeals.get(i), selectedLeads.get(i), selectedTasks.get(i).getId(), userNames));
        }
        return new PageResult<>(result, taskPage.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void overturn(Long appealId, Long userId, LeadAppealDecisionReqVO reqVO) {
        decide(appealId, userId, reqVO, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uphold(Long appealId, Long userId, LeadAppealDecisionReqVO reqVO) {
        decide(appealId, userId, reqVO, false);
    }

    private void decide(Long appealId, Long userId, LeadAppealDecisionReqVO reqVO, boolean overturn) {
        LeadAppealDO appeal = appealMapper.selectByIdForUpdate(appealId, TenantContextHolder.getRequiredTenantId());
        if (appeal == null) throw exception(LEAD_APPEAL_NOT_EXISTS);
        LeadAppealDO duplicate = appealMapper.selectByDecisionIdempotencyKey(reqVO.getIdempotencyKey());
        if (duplicate != null) {
            if (Objects.equals(duplicate.getId(), appealId) && Objects.equals(duplicate.getReviewerUserId(), userId)) return;
            throw exception(LEAD_APPEAL_IDEMPOTENCY_CONFLICT);
        }
        if (!isReviewing(appeal.getStatus())) throw exception(LEAD_APPEAL_ALREADY_HANDLED);
        String reviewPermission = requiredReviewPermission(appeal);
        if (!Objects.equals(appeal.getStatus(), reviewingStatus(appeal.getRoundNo()))) {
            throw exception(LEAD_APPEAL_PERMISSION_DENIED);
        }
        LeadDO lead = requireLeadForUpdate(appeal.getLeadId());
        if (!permissionApi.hasAnyPermissions(userId, reviewPermission)) {
            throw exception(LEAD_APPEAL_PERMISSION_DENIED);
        }
        if (!canReview(appeal, userId)) throw exception(LEAD_APPEAL_PERMISSION_DENIED);
        BpmTaskRespDTO task;
        try {
            task = processTaskApi.getTodoTask(userId, reqVO.getTaskId());
        } catch (RuntimeException ex) {
            throw exception(LEAD_APPEAL_ALREADY_HANDLED);
        }
        if (!Objects.equals(task.getProcessInstanceId(), appeal.getProcessInstanceId())
                || !Objects.equals(task.getBusinessKey(), APPEAL_BUSINESS_KEY_PREFIX + appealId)) {
            throw exception(LEAD_APPEAL_PERMISSION_DENIED);
        }
        String evidenceJson = buildEvidenceJson(reqVO.getAttachments(), userId);
        List<String> bpmAttachments = parseEvidence(evidenceJson).stream().map(EvidenceRef::getFileUrl)
                .filter(Objects::nonNull).toList();
        BpmTaskDecisionReqDTO decision = new BpmTaskDecisionReqDTO();
        decision.setTaskId(reqVO.getTaskId());
        decision.setReason(reqVO.getReason().trim());
        decision.setAttachments(bpmAttachments);
        if (overturn) processTaskApi.approveTask(userId, decision); else processTaskApi.rejectTask(userId, decision);

        LocalDateTime now = LocalDateTime.now();
        appeal.setStatus(overturn ? APPEAL_STATUS_OVERTURNED : APPEAL_STATUS_UPHELD);
        appeal.setReviewerUserId(userId);
        appeal.setDecisionReason(reqVO.getReason().trim());
        appeal.setDecisionEvidenceRefs(evidenceJson);
        appeal.setDecisionIdempotencyKey(reqVO.getIdempotencyKey());
        appeal.setDecidedAt(now);
        appealMapper.updateById(appeal);
        if (overturn) {
            OpportunityDO opportunity = opportunityMapper.selectByLeadId(lead.getId());
            boolean createOpportunity = opportunity == null;
            if (createOpportunity) {
                opportunity = new OpportunityDO();
                opportunity.setType(OPPORTUNITY_TYPE_INITIAL_CONVERSION);
                opportunity.setLeadId(lead.getId());
                opportunity.setExpectedProductSummary(
                        LeadBasicInfoService.productSummary(intendedProductMapper.selectListByLeadId(lead.getId())));
                opportunity.setVersion(0);
            }
            opportunity.setPersonId(lead.getPersonId());
            opportunity.setOwnerUserId(lead.getOwnerUserId());
            opportunity.setStatus(OPPORTUNITY_STATUS_OPEN);
            opportunity.setLostAt(null);
            opportunity.setLostReason(null);
            if (createOpportunity) opportunityMapper.insert(opportunity);
            else opportunityMapper.updateById(opportunity);
            lead.setStatus(STATUS_VALID);
            lead.setAssignmentStatus(ASSIGNMENT_OWNED);
            lead.setInvalidReason(null);
            lead.setInvalidReasonLabelSnapshot(null);
            lead.setInvalidDescription(null);
            lead.setInvalidEvidenceRefs(null);
            lead.setAppealDeadlineAt(null);
            lead.setQualifiedByUserId(userId);
            lead.setQualifiedAt(now);
            lead.setConvertedAt(now);
            lead.setValidDescription(reqVO.getReason().trim());
            leadMapper.updateById(lead);
            cashbackService.ensureValidCashback(lead.getId());
        }
        String eventType = overturn ? EVENT_LEAD_APPEAL_OVERTURNED : EVENT_LEAD_APPEAL_UPHELD;
        String scene = overturn ? cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.APPEAL_OVERTURNED
                : cn.iocoder.yudao.module.zsjos.enums.LeadNotifySceneConstants.APPEAL_UPHELD;
        BusinessEventDO event = addEvent(eventType, lead, userId, appeal, reviewingStatus(appeal.getRoundNo()),
                appeal.getStatus(), appeal.getDecisionReason(), appeal.getDecisionEvidenceRefs(), now,
                reqVO.getIdempotencyKey());
        notifyEventPublisher.publish(scene, lead.getId(), event.getIdempotencyKey(), userId, now,
                appealContext(lead, appeal));
    }

    @Override
    public LeadAttachmentUploadRespVO upload(MultipartFile file) throws IOException {
        return attachmentService.upload(file);
    }

    private ReviewerResolution resolveReviewers(int roundNo, LeadDO lead) {
        OwnerContext ownerContext = resolveOwnerContext(lead);
        if (roundNo == 1) {
            SupervisorResolution supervisor = findSupervisor(ownerContext);
            return new ReviewerResolution(ownerContext.ownerUserId(), ownerContext.deptId(),
                    supervisor.deptId(), List.of(supervisor.userId()));
        }
        if (roundNo == 2) {
            List<Long> users = roleUsers(ROLE_QUALITY_MANAGER, ROLE_QUALITY_SPECIALIST);
            if (users.isEmpty()) throw exception(LEAD_APPEAL_REVIEWER_NOT_CONFIGURED);
            return new ReviewerResolution(ownerContext.ownerUserId(), ownerContext.deptId(), null, users);
        }
        List<Long> users = roleUsers(ROLE_CHAIRMAN);
        if (users.size() != 1) throw exception(LEAD_APPEAL_CHAIRMAN_INVALID);
        return new ReviewerResolution(ownerContext.ownerUserId(), ownerContext.deptId(), null, users);
    }

    private OwnerContext resolveOwnerContext(LeadDO lead) {
        AdminUserRespDTO owner = lead.getOwnerUserId() == null ? null : adminUserApi.getUser(lead.getOwnerUserId());
        Long deptId = owner == null ? null : owner.getDeptId();
        return new OwnerContext(lead.getOwnerUserId(), deptId, deptId == null ? null : deptApi.getDept(deptId));
    }

    private SupervisorResolution findSupervisor(OwnerContext ownerContext) {
        if (ownerContext.dept() == null) throw exception(LEAD_APPEAL_REVIEWER_NOT_CONFIGURED);
        Set<Long> visitedDeptIds = new HashSet<>();
        DeptRespDTO current = ownerContext.dept();
        while (current != null && current.getId() != null && visitedDeptIds.add(current.getId())) {
            Long leaderId = current.getLeaderUserId();
            AdminUserRespDTO leader = leaderId == null ? null : adminUserApi.getUser(leaderId);
            if (leader != null && !Objects.equals(leaderId, ownerContext.ownerUserId())
                    && CommonStatusEnum.ENABLE.getStatus().equals(leader.getStatus())
                    && permissionApi.hasAnyPermissions(leaderId, PERMISSION_APPEAL_REVIEW_SALES_MANAGER)) {
                return new SupervisorResolution(current.getId(), leaderId);
            }
            Long parentId = current.getParentId();
            if (parentId == null || parentId == 0L) break;
            current = deptApi.getDept(parentId);
        }
        throw exception(LEAD_APPEAL_REVIEWER_NOT_CONFIGURED);
    }

    private List<Long> roleUsers(String... codes) {
        Set<Long> roleIds = new LinkedHashSet<>();
        for (String code : codes) {
            RoleRespDTO role = roleApi.getRoleByCode(code);
            if (role != null && CommonStatusEnum.ENABLE.getStatus().equals(role.getStatus())) roleIds.add(role.getId());
        }
        if (roleIds.isEmpty()) return List.of();
        return adminUserApi.getUserList(permissionApi.getUserRoleIdListByRoleIds(roleIds)).stream()
                .filter(user -> CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()))
                .map(AdminUserRespDTO::getId).distinct().sorted().toList();
    }

    private boolean canReview(LeadAppealDO appeal, Long userId) {
        return canReview(appeal, userId, adminUserApi.getUser(userId));
    }

    private boolean canReview(LeadAppealDO appeal, Long userId, AdminUserRespDTO user) {
        if (user == null || !CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())) return false;
        // Legacy rows predate reviewer snapshots. The BPM task API already scopes the task to its assignee.
        String snapshot = appeal.getReviewerUserIdsSnapshot();
        if (snapshot == null) return true;
        if (snapshot.isBlank()) return false;
        try {
            List<Long> ids = JsonUtils.parseArray(snapshot, Long.class);
            return ids != null && !ids.isEmpty() && ids.contains(userId);
        } catch (RuntimeException ex) {
            log.warn("[canReview][appealId({}) reviewer snapshot is invalid]", appeal.getId());
            return false;
        }
    }

    private boolean canReviewStage(LeadAppealDO appeal, Set<String> permissions) {
        String permission = reviewPermission(appeal);
        return permission != null && permissions.contains(permission);
    }

    private Set<String> reviewStages(Set<String> permissions) {
        Set<String> stages = new LinkedHashSet<>();
        if (permissions.contains(PERMISSION_APPEAL_REVIEW_SALES_MANAGER)) stages.add(APPEAL_STAGE_SALES_MANAGER);
        if (permissions.contains(PERMISSION_APPEAL_REVIEW_QUALITY)) stages.add(APPEAL_STAGE_QUALITY);
        if (permissions.contains(PERMISSION_APPEAL_REVIEW_CHAIRMAN)) stages.add(APPEAL_STAGE_CHAIRMAN);
        return stages;
    }

    private String buildEvidenceJson(List<LeadAttachmentReqVO> attachments, Long userId) {
        if (attachments == null || attachments.isEmpty()) return null;
        Map<Long, FileInfoRespDTO> files = attachmentService.validateReferences(attachments, userId);
        List<EvidenceRef> refs = new ArrayList<>();
        int sort = 0;
        for (LeadAttachmentReqVO item : attachments) {
            FileInfoRespDTO file = files.get(item.getInfraFileId());
            refs.add(new EvidenceRef(file.getId(), file.getUrl(), file.getName(), file.getType(), file.getSize(), sort++));
        }
        return JsonUtils.toJsonString(refs);
    }

    private LeadAppealRespVO convert(LeadAppealDO source, LeadDO lead, String taskId) {
        return convert(source, lead, taskId, null);
    }

    private LeadAppealRespVO convert(LeadAppealDO source, LeadDO lead, String taskId, Map<Long, String> userNames) {
        LeadAppealRespVO result = new LeadAppealRespVO();
        result.setId(source.getId()); result.setLeadId(source.getLeadId()); result.setLeadNo(lead.getLeadNo());
        result.setLeadName(lead.getSubmittedName());
        result.setRoundNo(source.getRoundNo()); result.setReviewStage(source.getReviewStage()); result.setStatus(source.getStatus());
        result.setApplicantUserId(source.getApplicantUserId());
        result.setApplicantUserName(userNames == null ? userName(source.getApplicantUserId())
                : userNames.get(source.getApplicantUserId()));
        result.setReason(source.getReason()); result.setEvidence(toEvidenceVO(source.getEvidenceRefs()));
        result.setInvalidReasonSnapshot(source.getInvalidReasonSnapshot());
        result.setInvalidDescriptionSnapshot(source.getInvalidDescriptionSnapshot());
        result.setInvalidEvidenceSnapshot(toEvidenceVO(source.getInvalidEvidenceRefsSnapshot()));
        result.setProcessInstanceId(source.getProcessInstanceId()); result.setTaskId(taskId);
        result.setReviewerUserId(source.getReviewerUserId());
        result.setReviewerUserName(userNames == null ? userName(source.getReviewerUserId())
                : userNames.get(source.getReviewerUserId()));
        result.setDecisionReason(source.getDecisionReason()); result.setDecisionEvidence(toEvidenceVO(source.getDecisionEvidenceRefs()));
        result.setSubmittedAt(source.getSubmittedAt()); result.setDecidedAt(source.getDecidedAt());
        result.setCanSubmitNextRound(APPEAL_STATUS_UPHELD.equals(source.getStatus()) && source.getRoundNo() < 3);
        return result;
    }

    private List<LeadAppealRespVO.EvidenceVO> toEvidenceVO(String json) {
        List<EvidenceRef> refs = parseEvidence(json);
        Map<Long, String> urls = refs.isEmpty() ? Map.of() : fileApi.presignGetUrls(
                refs.stream().map(EvidenceRef::getInfraFileId).filter(Objects::nonNull).toList(),
                ATTACHMENT_URL_EXPIRATION_SECONDS);
        return refs.stream().map(ref -> {
            LeadAppealRespVO.EvidenceVO vo = new LeadAppealRespVO.EvidenceVO();
            vo.setInfraFileId(ref.getInfraFileId()); vo.setFileUrl(urls.getOrDefault(ref.getInfraFileId(), ref.getFileUrl()));
            vo.setOriginalName(ref.getOriginalName()); vo.setContentType(ref.getContentType());
            vo.setFileSize(ref.getFileSize()); vo.setSort(ref.getSort()); return vo;
        }).toList();
    }

    private List<EvidenceRef> parseEvidence(String json) {
        return json == null ? List.of() : JsonUtils.parseArray(json, EvidenceRef.class);
    }

    private String userName(Long id) {
        AdminUserRespDTO user = id == null ? null : adminUserApi.getUser(id);
        return user == null ? null : user.getNickname();
    }

    private BusinessEventDO addEvent(String type, LeadDO lead, Long operatorUserId, LeadAppealDO appeal,
                                     String fromStatus, String toStatus, String reason, String evidence,
                                     LocalDateTime occurredAt, String idempotencyKey) {
        BusinessEventDO event = new BusinessEventDO();
        event.setEventType(type);
        event.setAggregateType(BIZ_TYPE_LEAD);
        event.setAggregateId(lead.getId());
        event.setOperatorUserId(operatorUserId);
        event.setFromStatus(fromStatus);
        event.setToStatus(toStatus);
        event.setReason(reason);
        event.setEvidenceRefs(evidence);
        event.setRelatedObjectRefs(JsonUtils.toJsonString(Map.of("appealId", appeal.getId(),
                "roundNo", appeal.getRoundNo(), "processInstanceId", appeal.getProcessInstanceId())));
        event.setOccurredAt(occurredAt);
        event.setIdempotencyKey(type + ":" + idempotencyKey);
        eventMapper.insert(event);
        return event;
    }

    private Map<String, Object> appealContext(LeadDO lead, LeadAppealDO appeal) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("submitterUserId", lead.getSourceUserId());
        context.put("ownerUserId", lead.getOwnerUserId());
        context.put("appeal.id", appeal.getId());
        context.put("appeal.roundNo", appeal.getRoundNo());
        context.put("appeal.stage", appeal.getReviewStage());
        context.put("appeal.reason", appeal.getReason());
        context.put("appeal.decisionReason", appeal.getDecisionReason());
        return context;
    }

    private Long parseAppealId(String businessKey) {
        if (businessKey == null || !businessKey.startsWith(APPEAL_BUSINESS_KEY_PREFIX)) return null;
        try { return Long.valueOf(businessKey.substring(APPEAL_BUSINESS_KEY_PREFIX.length())); }
        catch (NumberFormatException ex) { return null; }
    }

    private LeadDO requireLead(Long id) {
        LeadDO lead = leadMapper.selectById(id);
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        return lead;
    }

    private LeadDO requireLeadForUpdate(Long id) {
        LeadDO lead = leadMapper.selectByIdForUpdate(id, TenantContextHolder.getRequiredTenantId());
        if (lead == null) throw exception(LEAD_NOT_EXISTS);
        return lead;
    }

    private String stage(int round) { return round == 1 ? APPEAL_STAGE_SALES_MANAGER : round == 2 ? APPEAL_STAGE_QUALITY : APPEAL_STAGE_CHAIRMAN; }
    private String requiredReviewPermission(LeadAppealDO appeal) {
        String permission = reviewPermission(appeal);
        if (permission == null) throw exception(LEAD_APPEAL_PERMISSION_DENIED);
        return permission;
    }
    private String reviewPermission(LeadAppealDO appeal) {
        if (appeal.getRoundNo() == null || appeal.getRoundNo() < 1 || appeal.getRoundNo() > 3) {
            return null;
        }
        String expectedStage = stage(appeal.getRoundNo());
        if (!Objects.equals(appeal.getReviewStage(), expectedStage)) {
            return null;
        }
        return switch (expectedStage) {
            case APPEAL_STAGE_SALES_MANAGER -> PERMISSION_APPEAL_REVIEW_SALES_MANAGER;
            case APPEAL_STAGE_QUALITY -> PERMISSION_APPEAL_REVIEW_QUALITY;
            case APPEAL_STAGE_CHAIRMAN -> PERMISSION_APPEAL_REVIEW_CHAIRMAN;
            default -> null;
        };
    }
    private String reviewingStatus(int round) { return round == 1 ? APPEAL_STATUS_SALES_MANAGER_REVIEWING : round == 2 ? APPEAL_STATUS_QUALITY_REVIEWING : APPEAL_STATUS_CHAIRMAN_REVIEWING; }
    private boolean isReviewing(String status) { return Set.of(APPEAL_STATUS_SALES_MANAGER_REVIEWING, APPEAL_STATUS_QUALITY_REVIEWING, APPEAL_STATUS_CHAIRMAN_REVIEWING).contains(status); }

    private record OwnerContext(Long ownerUserId, Long deptId, DeptRespDTO dept) {}
    private record SupervisorResolution(Long deptId, Long userId) {}
    private record ReviewerResolution(Long ownerUserId, Long ownerDeptId, Long reviewerDeptId,
                                      List<Long> reviewerUserIds) {}

    @Data @NoArgsConstructor @AllArgsConstructor
    private static class EvidenceRef {
        private Long infraFileId;
        private String fileUrl;
        private String originalName;
        private String contentType;
        private Long fileSize;
        private Integer sort;
    }
}
