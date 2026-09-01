package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerInvitationRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerOpenRequestCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerOpenRequestPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerOpenRequestRespVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerOpenRequestDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personnel.PartnerOpenRequestMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.PersonnelConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosPostCodeConstants.NEW_MEDIA_OPERATOR;

@Service
@Slf4j
public class PartnerOpenRequestServiceImpl implements PartnerOpenRequestService {

    @Resource private PartnerOpenRequestMapper requestMapper;
    @Resource private PartnerInvitationService invitationService;
    @Resource private PartnerAccountService partnerAccountService;
    @Resource private BpmProcessInstanceApi processInstanceApi;
    @Resource private BpmProcessTaskApi processTaskApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;
    @Resource private PermissionApi permissionApi;
    @Resource private RoleApi roleApi;
    @Resource private PartnerOpenRequestNotifyPublisher notifyPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PartnerOpenRequestCreateReqVO reqVO, Long applicantUserId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String idempotencyKey = StrUtil.trimToNull(reqVO.getIdempotencyKey());
        if (idempotencyKey != null) {
            PartnerOpenRequestDO existing = requestMapper.selectByApplicantAndIdempotencyKey(applicantUserId,
                    idempotencyKey, tenantId);
            if (existing != null) {
                return existing.getId();
            }
        }
        String mobile = StrUtil.trim(reqVO.getPartnerMobile());
        if (partnerAccountService.getByMobile(mobile) != null) {
            throw exception(PARTNER_MOBILE_DUPLICATE);
        }
        if (requestMapper.selectActiveByMobileForUpdate(mobile, tenantId) != null) {
            throw exception(PARTNER_OPEN_REQUEST_DUPLICATE);
        }
        AdminUserRespDTO assignedEmployee = requireNewMediaOperator(reqVO.getAssignedEmployeeUserId());
        AdminUserRespDTO applicant = requireEnabledUser(applicantUserId, PARTNER_OPEN_REQUEST_PERMISSION_DENIED);
        DeptRespDTO assignedDept = assignedEmployee.getDeptId() == null ? null : deptApi.getDept(assignedEmployee.getDeptId());
        DeptRespDTO applicantDept = applicant.getDeptId() == null ? null : deptApi.getDept(applicant.getDeptId());
        List<Long> reviewers = reviewerUserIds();
        if (reviewers.isEmpty()) {
            throw exception(PARTNER_OPEN_REQUEST_PROCESS_UNAVAILABLE);
        }
        LocalDateTime now = LocalDateTime.now();
        PartnerOpenRequestDO request = new PartnerOpenRequestDO()
                .setRequestNo(number())
                .setPartnerName(StrUtil.trim(reqVO.getPartnerName()))
                .setPartnerMobile(mobile)
                .setActiveMobileKey(mobile)
                .setAssignedEmployeeUserId(assignedEmployee.getId())
                .setAssignedEmployeeNameSnapshot(assignedEmployee.getNickname())
                .setAssignedEmployeeDeptIdSnapshot(assignedEmployee.getDeptId())
                .setAssignedEmployeeDeptNameSnapshot(assignedDept == null ? null : assignedDept.getName())
                .setApplicantUserId(applicant.getId())
                .setApplicantNameSnapshot(applicant.getNickname())
                .setApplicantDeptIdSnapshot(applicant.getDeptId())
                .setApplicantDeptNameSnapshot(applicantDept == null ? null : applicantDept.getName())
                .setStatus(PARTNER_OPEN_REQUEST_STATUS_PENDING)
                .setIdempotencyKey(idempotencyKey)
                .setSubmittedAt(now)
                .setVersion(0);
        try {
            requestMapper.insert(request);
        } catch (DuplicateKeyException duplicateKeyException) {
            throw exception(PARTNER_OPEN_REQUEST_DUPLICATE);
        }

        BpmProcessInstanceCreateReqDTO process = new BpmProcessInstanceCreateReqDTO();
        process.setProcessDefinitionKey(PARTNER_OPEN_REQUEST_PROCESS_DEFINITION_KEY);
        process.setBusinessKey(PARTNER_OPEN_REQUEST_BUSINESS_KEY_PREFIX + request.getId());
        process.setVariables(new LinkedHashMap<>(Map.of(
                "requestId", request.getId(),
                "requestNo", request.getRequestNo(),
                "partnerName", request.getPartnerName(),
                "partnerMobile", mask(request.getPartnerMobile()),
                "assignedEmployeeUserId", request.getAssignedEmployeeUserId(),
                "assignedEmployeeName", request.getAssignedEmployeeNameSnapshot(),
                "applicantUserId", request.getApplicantUserId(),
                "applicantName", request.getApplicantNameSnapshot()
        )));
        process.setStartUserSelectAssignees(Map.of(PARTNER_OPEN_REQUEST_TASK_DEFINITION_KEY, reviewers));
        try {
            request.setProcessInstanceId(processInstanceApi.createProcessInstance(applicantUserId, process));
        } catch (RuntimeException ex) {
            log.error("[create][partnerOpenRequestId({}) BPM start failed]", request.getId(), ex);
            throw exception(PARTNER_OPEN_REQUEST_PROCESS_UNAVAILABLE);
        }
        requestMapper.updateById(request);
        notifyPublisher.publish(PARTNER_OPEN_REQUEST_SCENE_SUBMITTED, request.getId(),
                "partner-open-request-submitted:" + request.getId(), applicantUserId, notifyPayload(request, reviewers));
        return request.getId();
    }

    @Override
    public PageResult<PartnerOpenRequestRespVO> getPage(PartnerOpenRequestPageReqVO reqVO, Long userId) {
        reqVO.setKeyword(StrUtil.trimToNull(reqVO.getKeyword()));
        Long applicantUserId = permissionApi.hasAnyPermissions(userId, PARTNER_OPEN_REQUEST_PERMISSION_REVIEW)
                ? null : userId;
        PageResult<PartnerOpenRequestDO> page = requestMapper.selectPage(reqVO, applicantUserId);
        Map<Long, AdminUserRespDTO> users = getUserMap(page.getList().stream()
                .flatMap(item -> Stream.of(item.getReviewedByUserId(), item.getCancelledByUserId()))
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        return new PageResult<>(page.getList().stream().map(item -> toResp(item, userId, users)).toList(),
                page.getTotal());
    }

    @Override
    public PartnerOpenRequestRespVO getDetail(Long id, Long userId) {
        PartnerOpenRequestDO request = requestMapper.selectById(id);
        if (request == null) {
            throw exception(PARTNER_OPEN_REQUEST_NOT_EXISTS);
        }
        if (!canRead(request, userId)) {
            throw exception(PARTNER_OPEN_REQUEST_PERMISSION_DENIED);
        }
        return toResp(request, userId, getUserMap(Stream.of(request.getReviewedByUserId(),
                request.getCancelledByUserId()).filter(Objects::nonNull).collect(Collectors.toSet())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, Long userId) {
        PartnerOpenRequestDO request = lock(id);
        if (!Objects.equals(request.getApplicantUserId(), userId)) {
            throw exception(PARTNER_OPEN_REQUEST_PERMISSION_DENIED);
        }
        if (!PARTNER_OPEN_REQUEST_STATUS_PENDING.equals(request.getStatus())) {
            throw exception(PARTNER_OPEN_REQUEST_STATE_INVALID);
        }
        request.setStatus(PARTNER_OPEN_REQUEST_STATUS_CANCELLED)
                .setActiveMobileKey(null)
                .setCancelledByUserId(userId)
                .setCancelledAt(LocalDateTime.now());
        if (requestMapper.updateById(request) != 1) {
            throw exception(PARTNER_OPEN_REQUEST_VERSION_CONFLICT);
        }
        processInstanceApi.cancelProcessInstanceByStartUser(userId, request.getProcessInstanceId(), "发起人撤回代开通兼职账号申请");
    }

    @Override
    public PageResult<LeadAssignmentUserRespVO> getAssigneeCandidatePage(Long deptId, String keyword,
                                                                         Integer pageNo, Integer pageSize) {
        RoleRespDTO role = requireNewMediaOperatorRole();
        Set<Long> roleUserIds = permissionApi.getUserRoleIdListByRoleIds(Set.of(role.getId()));
        Set<Long> deptUserIds = deptId == null ? null : usersInDeptTree(deptId);
        String normalizedKeyword = StrUtil.trimToNull(keyword);
        List<AdminUserRespDTO> users = adminUserApi.getUserList(roleUserIds).stream()
                .filter(user -> CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()))
                .filter(user -> deptUserIds == null || deptUserIds.contains(user.getId()))
                .filter(user -> normalizedKeyword == null
                        || StrUtil.containsIgnoreCase(StrUtil.nullToEmpty(user.getNickname()), normalizedKeyword)
                        || StrUtil.contains(StrUtil.nullToEmpty(user.getMobile()), normalizedKeyword))
                .sorted(java.util.Comparator.comparing(AdminUserRespDTO::getId))
                .toList();
        int currentPageNo = Math.max(1, pageNo == null ? 1 : pageNo);
        int currentPageSize = Math.min(100, Math.max(1, pageSize == null ? 20 : pageSize));
        int from = Math.min((currentPageNo - 1) * currentPageSize, users.size());
        int to = Math.min(from + currentPageSize, users.size());
        return new PageResult<>(users.subList(from, to).stream().map(user -> new LeadAssignmentUserRespVO()
                .setId(user.getId()).setNickname(user.getNickname()).setMaskedMobile(mask(user.getMobile()))
                .setDeptId(user.getDeptId()).setStatus(user.getStatus())).toList(), (long) users.size());
    }

    @Override
    @cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit(action = "partner-open-request.process-result",
            targetType = "partner-open-request")
    @Transactional(rollbackFor = Exception.class)
    public void handleProcessResult(String processInstanceId, Integer processStatus, String reason) {
        if (!BpmProcessInstanceStatusEnum.isProcessEndStatus(processStatus)) {
            return;
        }
        PartnerOpenRequestDO found = requestMapper.selectByProcessInstanceId(processInstanceId);
        if (found == null) {
            return;
        }
        PartnerOpenRequestDO request = lock(found.getId());
        if (!PARTNER_OPEN_REQUEST_STATUS_PENDING.equals(request.getStatus())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        var nodeStatuses = processTaskApi.getProcessNodeStatuses(processInstanceId,
                Set.of(PARTNER_OPEN_REQUEST_TASK_DEFINITION_KEY));
        Long reviewerUserId = CollUtil.isEmpty(nodeStatuses) ? null : nodeStatuses.getFirst().getReviewerUserId();
        if (BpmProcessInstanceStatusEnum.APPROVE.getStatus().equals(processStatus)) {
            request.setStatus(PARTNER_OPEN_REQUEST_STATUS_APPROVED)
                    .setReviewedByUserId(reviewerUserId)
                    .setReviewedAt(now)
                    .setReviewReason(StrUtil.trimToNull(reason));
            openByInvitation(request, reviewerUserId == null ? 0L : reviewerUserId, now);
        } else if (BpmProcessInstanceStatusEnum.REJECT.getStatus().equals(processStatus)) {
            request.setStatus(PARTNER_OPEN_REQUEST_STATUS_REJECTED)
                    .setReviewedByUserId(reviewerUserId)
                    .setReviewedAt(now)
                    .setReviewReason(StrUtil.trimToNull(reason))
                    .setActiveMobileKey(null);
            requestMapper.updateById(request);
            notifyPublisher.publish(PARTNER_OPEN_REQUEST_SCENE_REJECTED, request.getId(),
                    "partner-open-request-rejected:" + processInstanceId, 0L, notifyPayload(request, List.of()));
        } else {
            request.setStatus(PARTNER_OPEN_REQUEST_STATUS_CANCELLED)
                    .setReviewReason(StrUtil.trimToNull(reason))
                    .setActiveMobileKey(null)
                    .setCancelledAt(now);
            requestMapper.updateById(request);
        }
    }

    private void openByInvitation(PartnerOpenRequestDO request, Long operatorUserId, LocalDateTime now) {
        if (partnerAccountService.getByMobile(request.getPartnerMobile()) != null) {
            markOpenFailed(request, "手机号已存在兼职账号");
            return;
        }
        try {
            PartnerInvitationRespVO invitation = invitationService.create(new PartnerInvitationCreateCommand()
                    .setName(request.getPartnerName())
                    .setMobile(request.getPartnerMobile())
                    .setAssignedOperatorUserId(request.getAssignedEmployeeUserId()), operatorUserId);
            request.setStatus(PARTNER_OPEN_REQUEST_STATUS_OPENED)
                    .setActiveMobileKey(null)
                    .setInvitationId(invitation.getId())
                    .setInviteCodeSnapshot(invitation.getInviteCode())
                    .setInviteExpiresAt(invitation.getExpiresAt())
                    .setOpenedAt(now)
                    .setFailureReason(null);
            requestMapper.updateById(request);
            notifyPublisher.publish(PARTNER_OPEN_REQUEST_SCENE_OPENED, request.getId(),
                    "partner-open-request-opened:" + request.getId(), operatorUserId, notifyPayload(request, List.of()));
        } catch (RuntimeException ex) {
            log.error("[openByInvitation][partnerOpenRequestId({}) failed]", request.getId(), ex);
            markOpenFailed(request, StrUtil.maxLength(StrUtil.blankToDefault(ex.getMessage(), "邀请码生成失败"), 500));
        }
    }

    private void markOpenFailed(PartnerOpenRequestDO request, String failureReason) {
        request.setStatus(PARTNER_OPEN_REQUEST_STATUS_OPEN_FAILED)
                .setActiveMobileKey(null)
                .setFailureReason(failureReason);
        requestMapper.updateById(request);
        notifyPublisher.publish(PARTNER_OPEN_REQUEST_SCENE_OPEN_FAILED, request.getId(),
                "partner-open-request-open-failed:" + request.getId(), 0L, notifyPayload(request, List.of()));
    }

    private PartnerOpenRequestDO lock(Long id) {
        PartnerOpenRequestDO request = requestMapper.selectByIdForUpdate(id, TenantContextHolder.getRequiredTenantId());
        if (request == null) {
            throw exception(PARTNER_OPEN_REQUEST_NOT_EXISTS);
        }
        return request;
    }

    private boolean canRead(PartnerOpenRequestDO request, Long userId) {
        return Objects.equals(request.getApplicantUserId(), userId)
                || permissionApi.hasAnyPermissions(userId, PARTNER_OPEN_REQUEST_PERMISSION_REVIEW);
    }

    private AdminUserRespDTO requireEnabledUser(Long userId, cn.iocoder.yudao.framework.common.exception.ErrorCode errorCode) {
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        if (user == null || !CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())) {
            throw exception(errorCode);
        }
        return user;
    }

    private AdminUserRespDTO requireNewMediaOperator(Long userId) {
        RoleRespDTO role = requireNewMediaOperatorRole();
        Set<Long> userIds = permissionApi.getUserRoleIdListByRoleIds(Set.of(role.getId()));
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        if (user == null || !CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()) || !userIds.contains(userId)) {
            throw exception(PARTNER_INVITATION_OPERATOR_INVALID);
        }
        return user;
    }

    private RoleRespDTO requireNewMediaOperatorRole() {
        RoleRespDTO role = roleApi.getRoleByCode(NEW_MEDIA_OPERATOR);
        if (role == null || !CommonStatusEnum.ENABLE.getStatus().equals(role.getStatus())) {
            throw exception(PARTNER_INVITATION_OPERATOR_INVALID);
        }
        return role;
    }

    private Set<Long> usersInDeptTree(Long deptId) {
        Set<Long> deptIds = deptApi.getChildDeptList(deptId).stream().map(DeptRespDTO::getId).collect(Collectors.toSet());
        deptIds.add(deptId);
        return adminUserApi.getUserListByDeptIds(deptIds).stream().map(AdminUserRespDTO::getId).collect(Collectors.toSet());
    }

    private List<Long> reviewerUserIds() {
        return permissionApi.getEnabledUserIdsByPermission(PARTNER_OPEN_REQUEST_PERMISSION_REVIEW)
                .stream().sorted().toList();
    }

    private Map<Long, AdminUserRespDTO> getUserMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return adminUserApi.getUserMap(ids);
    }

    private PartnerOpenRequestRespVO toResp(PartnerOpenRequestDO request, Long viewerUserId,
                                            Map<Long, AdminUserRespDTO> users) {
        PartnerOpenRequestRespVO resp = new PartnerOpenRequestRespVO();
        resp.setId(request.getId()).setRequestNo(request.getRequestNo()).setPartnerName(request.getPartnerName())
                .setMaskedPartnerMobile(mask(request.getPartnerMobile()))
                .setAssignedEmployeeUserId(request.getAssignedEmployeeUserId())
                .setAssignedEmployeeName(request.getAssignedEmployeeNameSnapshot())
                .setAssignedEmployeeDeptId(request.getAssignedEmployeeDeptIdSnapshot())
                .setAssignedEmployeeDeptName(request.getAssignedEmployeeDeptNameSnapshot())
                .setApplicantUserId(request.getApplicantUserId()).setApplicantName(request.getApplicantNameSnapshot())
                .setApplicantDeptId(request.getApplicantDeptIdSnapshot()).setApplicantDeptName(request.getApplicantDeptNameSnapshot())
                .setStatus(request.getStatus()).setProcessInstanceId(request.getProcessInstanceId())
                .setInvitationId(request.getInvitationId()).setInviteExpiresAt(request.getInviteExpiresAt())
                .setReviewedByUserId(request.getReviewedByUserId())
                .setReviewedByName(users.get(request.getReviewedByUserId()) == null ? null
                        : users.get(request.getReviewedByUserId()).getNickname())
                .setReviewedAt(request.getReviewedAt()).setReviewReason(request.getReviewReason())
                .setOpenedAt(request.getOpenedAt()).setFailureReason(request.getFailureReason())
                .setSubmittedAt(request.getSubmittedAt()).setCancelledByUserId(request.getCancelledByUserId())
                .setCancelledAt(request.getCancelledAt()).setCreateTime(request.getCreateTime())
                .setVersion(request.getVersion()).setAvailableActions(availableActions(request, viewerUserId));
        boolean canSeeSensitive = Objects.equals(request.getApplicantUserId(), viewerUserId)
                || permissionApi.hasAnyPermissions(viewerUserId, PARTNER_OPEN_REQUEST_PERMISSION_REVIEW);
        resp.setPartnerMobile(canSeeSensitive ? request.getPartnerMobile() : null);
        resp.setInviteCode(canSeeSensitive && PARTNER_OPEN_REQUEST_STATUS_OPENED.equals(request.getStatus())
                ? request.getInviteCodeSnapshot() : null);
        return resp;
    }

    private List<String> availableActions(PartnerOpenRequestDO request, Long viewerUserId) {
        if (PARTNER_OPEN_REQUEST_STATUS_PENDING.equals(request.getStatus())
                && Objects.equals(request.getApplicantUserId(), viewerUserId)
                && permissionApi.hasAnyPermissions(viewerUserId, PARTNER_OPEN_REQUEST_PERMISSION_CANCEL)) {
            return List.of("cancel");
        }
        return List.of();
    }

    private Map<String, Object> notifyPayload(PartnerOpenRequestDO request, List<Long> reviewers) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("request.id", request.getId());
        payload.put("request.no", request.getRequestNo());
        payload.put("partner.name", request.getPartnerName());
        payload.put("partner.mobile.masked", mask(request.getPartnerMobile()));
        payload.put("assigned.employee", request.getAssignedEmployeeNameSnapshot());
        payload.put("applicantUserId", request.getApplicantUserId());
        payload.put("reviewerUserIds", reviewers);
        if (StrUtil.isNotBlank(request.getInviteCodeSnapshot())) {
            payload.put("invite.code", request.getInviteCodeSnapshot());
        }
        if (request.getInviteExpiresAt() != null) {
            payload.put("invite.expiresAt", request.getInviteExpiresAt());
        }
        if (StrUtil.isNotBlank(request.getReviewReason())) {
            payload.put("review.reason", request.getReviewReason());
        }
        if (StrUtil.isNotBlank(request.getFailureReason())) {
            payload.put("failure.reason", request.getFailureReason());
        }
        return payload;
    }

    private static String mask(String value) {
        if (StrUtil.isBlank(value) || value.length() < 7) {
            return "****";
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    private static String number() {
        return "POR" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }
}
