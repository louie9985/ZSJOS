package cn.iocoder.yudao.module.zsjos.service.workorder;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserCandidatePageReqDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workorder.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workorder.*;
import cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
public class WorkOrderServiceImpl implements WorkOrderService {
    private static final Set<String> FIELD_TYPES = Set.of(
            "text", "textarea", "number", "date", "datetime", "user", "department", "dictionary", "attachment");

    @Resource private WorkOrderSceneMapper sceneMapper;
    @Resource private WorkOrderSceneVersionMapper sceneVersionMapper;
    @Resource private WorkOrderMapper orderMapper;
    @Resource private WorkOrderHistoryMapper historyMapper;
    @Resource private WorkOrderNumberCounterMapper numberCounterMapper;
    @Resource private WorkOrderAttachmentMapper attachmentMapper;
    @Resource private PostApi postApi;
    @Resource private DeptApi deptApi;
    @Resource private DictDataApi dictDataApi;
    @Resource private AdminUserApi adminUserApi;
    @Resource private PermissionApi permissionApi;
    @Resource private RoleApi roleApi;
    @Resource private FileApi fileApi;
    @Resource private MediaWorkflowEventService workflowEventService;

    @Override
    public WorkOrderFileRespVO upload(byte[] content, String name, String contentType, Long userId) {
        FileInfoRespDTO file = fileApi.createFileInfo(content, name, "zsjos/work-order/" + userId, contentType);
        return BeanUtils.toBean(file, WorkOrderFileRespVO.class);
    }

    @Override
    public Long createScene(WorkOrderSceneCreateReqVO req, Long userId) {
        if (sceneMapper.selectByCode(req.getCode()) != null) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_CODE_DUPLICATE);
        }
        validateScene(req);
        WorkOrderSceneDO row = toSceneDO(req);
        row.setVersion(0);
        try {
            sceneMapper.insert(row);
        } catch (DuplicateKeyException duplicate) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_CODE_DUPLICATE);
        }
        return row.getId();
    }

    @Override
    public void updateScene(WorkOrderSceneUpdateReqVO req, Long userId) {
        WorkOrderSceneDO old = requireScene(req.getId());
        if (!Objects.equals(old.getCode(), req.getCode()) || !Objects.equals(old.getVersion(), req.getVersion())) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_INVALID);
        }
        validateScene(req);
        WorkOrderSceneDO row = toSceneDO(req);
        row.setId(req.getId());
        row.setVersion(req.getVersion());
        if (sceneMapper.updateById(row) != 1) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_INVALID);
        }
    }

    @Override
    public PageResult<WorkOrderSceneRespVO> scenePage(int pageNo, int pageSize) {
        PageResult<WorkOrderSceneDO> page = sceneMapper.selectPage(page(pageNo, pageSize), null);
        return new PageResult<>(page.getList().stream().map(this::toSceneVO).toList(), page.getTotal());
    }

    @Override
    public WorkOrderSceneRespVO getScene(String code) {
        return toSceneVO(requireScene(code));
    }

    @Override
    public WorkOrderScenePublishValidationRespVO validateScenePublish(Long id) {
        WorkOrderSceneDO scene = requireScene(id);
        validatePublishedScene(scene);
        return new WorkOrderScenePublishValidationRespVO(true, numberPreview(scene));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishScene(WorkOrderScenePublishReqVO req, Long userId) {
        WorkOrderSceneDO scene = requireScene(req.getId());
        if (!Objects.equals(scene.getVersion(), req.getVersion())) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_INVALID);
        }
        validatePublishedScene(scene);
        WorkOrderSceneVersionDO version = BeanUtils.toBean(scene, WorkOrderSceneVersionDO.class);
        version.setId(null);
        version.setSceneId(scene.getId());
        WorkOrderSceneVersionDO latest = sceneVersionMapper.selectLatestBySceneId(scene.getId());
        version.setVersionNo(latest == null ? 1 : latest.getVersionNo() + 1);
        version.setPublishedBy(userId);
        version.setPublishedAt(LocalDateTime.now());
        sceneVersionMapper.insert(version);
        WorkOrderSceneDO update = new WorkOrderSceneDO();
        update.setId(scene.getId());
        update.setVersion(scene.getVersion());
        update.setPublishedVersionId(version.getId());
        update.setPublishedVersionNo(version.getVersionNo());
        update.setLifecycleStatus("PUBLISHED");
        if (sceneMapper.updateById(update) != 1) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_INVALID);
        }
    }

    @Override
    public void disableScene(Long id, Integer version, Long userId) {
        WorkOrderSceneDO scene = requireScene(id);
        if (!Objects.equals(scene.getVersion(), version)) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_INVALID);
        }
        WorkOrderSceneDO update = new WorkOrderSceneDO();
        update.setId(id);
        update.setVersion(version);
        update.setStatus(CommonStatusEnum.DISABLE.getStatus());
        update.setLifecycleStatus("DISABLED");
        if (sceneMapper.updateById(update) != 1) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_INVALID);
        }
    }

    @Override
    public List<WorkOrderSceneRespVO> sceneVersions(Long id) {
        requireScene(id);
        return sceneVersionMapper.selectListBySceneId(id).stream().map(this::toSceneVO).toList();
    }

    @Override
    public PageResult<WorkOrderSceneRespVO> catalog(int pageNo, int pageSize, Long userId) {
        PageParam request = page(pageNo, pageSize);
        List<WorkOrderSceneRespVO> eligible = sceneMapper.selectPublishedList().stream()
                .map(scene -> sceneVersionMapper.selectById(scene.getPublishedVersionId()))
                .filter(Objects::nonNull)
                .filter(version -> isQualified(userId, version.getSourceQualificationMode(),
                        version.getSourceRoleScopesJson(), version.getSourceDeptScopesJson()))
                .map(this::toSceneVO).toList();
        int from = Math.min((request.getPageNo() - 1) * request.getPageSize(), eligible.size());
        int to = Math.min(from + request.getPageSize(), eligible.size());
        return new PageResult<>(eligible.subList(from, to), (long) eligible.size());
    }

    @Override
    public PageResult<WorkOrderCandidateRespVO> candidatePage(WorkOrderCandidatePageReqVO req, Long userId) {
        WorkOrderSceneVersionDO version = requirePublishedVersion(req.getSceneCode());
        requireQualification(userId, version.getSourceQualificationMode(), version.getSourceRoleScopesJson(),
                version.getSourceDeptScopesJson());
        AdminUserCandidatePageReqDTO query = new AdminUserCandidatePageReqDTO();
        query.setQualificationMode(version.getTargetQualificationMode());
        query.setRoleIds(new LinkedHashSet<>(scopeIds(version.getTargetRoleScopesJson())));
        query.setDeptIds(new LinkedHashSet<>(scopeIds(version.getTargetDeptScopesJson())));
        query.setKeyword(req.getKeyword()); query.setPageNo(req.getPageNo()); query.setPageSize(req.getPageSize());
        PageResult<AdminUserRespDTO> page = adminUserApi.getCandidateUserPage(query);
        return new PageResult<>(page.getList().stream().map(user -> {
            WorkOrderCandidateRespVO vo = new WorkOrderCandidateRespVO();
            vo.setId(user.getId()); vo.setName(user.getNickname()); vo.setDeptId(user.getDeptId()); return vo;
        }).toList(), page.getTotal());
    }

    @Override
    public PageResult<WorkOrderCandidateRespVO> candidateDepartmentPage(WorkOrderCandidatePageReqVO req, Long userId) {
        WorkOrderSceneVersionDO version = requirePublishedVersion(req.getSceneCode());
        requireQualification(userId, version.getSourceQualificationMode(), version.getSourceRoleScopesJson(),
                version.getSourceDeptScopesJson());
        List<WorkOrderCandidateRespVO> departments = candidateDepartments(version);
        int from = Math.min((req.getPageNo() - 1) * req.getPageSize(), departments.size());
        int to = Math.min(from + req.getPageSize(), departments.size());
        return new PageResult<>(departments.subList(from, to), (long) departments.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(WorkOrderCreateReqVO req, Long userId) {
        if (req.getRemark() == null || req.getRemark().isBlank()) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
        }
        List<Long> attachmentIds = normalizeAttachmentIds(req.getAttachmentIds());
        String fingerprint = fingerprint("create", userId, req.getSceneCode(), req.getTargetUserId(),
                req.getTargetDeptId(), req.getRemark().trim(), canonicalize(req.getValues()), attachmentIds);
        WorkOrderDO replay = orderMapper.selectByIdempotencyKey(req.getIdempotencyKey());
        if (replay != null) return requireCreateReplay(replay, userId, fingerprint);
        WorkOrderSceneDO scene = requireScene(req.getSceneCode());
        if (!Integer.valueOf(1).equals(scene.getStatus())) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_INVALID);
        }
        boolean versioned = scene.getPublishedVersionId() != null;
        WorkOrderSceneVersionDO published = versioned ? sceneVersionMapper.selectById(scene.getPublishedVersionId()) : null;
        if (versioned && published == null) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_INVALID);
        AdminUserRespDTO source = versioned
                ? requireQualification(userId, published.getSourceQualificationMode(), published.getSourceRoleScopesJson(), published.getSourceDeptScopesJson())
                : requireEligibleUser(userId, scene.getSourcePostCode());
        AdminUserRespDTO target = versioned ? validateTarget(req, published) : validateTarget(req, scene);
        List<WorkOrderFieldDefinition> definitions = parseDefinitions(versioned ? published.getFieldsJson() : scene.getFieldsJson());
        Map<String, Object> values = normalizeValues(definitions, req.getValues());
        List<Long> attachments = validateAttachments(mergeAttachmentIds(attachmentIds,
                dynamicAttachmentIds(definitions, values)), userId, 100);

        WorkOrderDO row = new WorkOrderDO();
        row.setBusinessType("GENERIC");
        row.setProcessorType(versioned ? published.getProcessorType() : "GENERIC");
        row.setOrderNo(versioned ? allocateOrderNo(published) : "WO" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4));
        row.setSceneCode(scene.getCode());
        row.setSceneVersionId(versioned ? published.getId() : null);
        row.setSceneNameSnapshot(versioned ? published.getName() : scene.getName());
        row.setAssignmentMode(versioned ? (req.getTargetUserId() != null ? "PERSON" : "DEPARTMENT") : scene.getAssignmentMode());
        row.setSourceUserId(userId);
        row.setTargetUserId(req.getTargetUserId());
        row.setTargetDeptId(req.getTargetDeptId());
        row.setSourceNameSnapshot(source.getNickname());
        row.setTargetNameSnapshot(target == null ? null : target.getNickname());
        row.setStatus(req.getTargetDeptId() != null || "PUBLIC_POOL".equals(scene.getAssignmentMode()) ? "AVAILABLE" : "PENDING_ACCEPT");
        if (versioned) {
            row.setRejectionStrategySnapshot(published.getRejectionStrategy());
            row.setCandidateQualificationMode(published.getTargetQualificationMode());
            row.setCandidateRoleScopesJson(published.getTargetRoleScopesJson());
            row.setCandidateDeptScopesJson(published.getTargetDeptScopesJson());
        }
        row.setFieldSnapshotJson(JsonUtils.toJsonString(definitions));
        row.setValueJson(JsonUtils.toJsonString(values));
        row.setAttachmentIdsJson(JsonUtils.toJsonString(attachments));
        row.setRemark(req.getRemark().trim());
        row.setCurrentRound(1);
        row.setCompletionAttachmentIdsJson("[]");
        row.setIdempotencyKey(req.getIdempotencyKey());
        row.setCommandUserId(userId);
        row.setRequestFingerprint(fingerprint);
        row.setVersion(0);
        try {
            orderMapper.insert(row);
        } catch (DuplicateKeyException duplicate) {
            WorkOrderDO concurrent = orderMapper.selectByIdempotencyKey(req.getIdempotencyKey());
            if (concurrent == null) throw duplicate;
            return requireCreateReplay(concurrent, userId, fingerprint);
        }
        history(row, null, row.getStatus(), userId, null, req.getIdempotencyKey(), "create", fingerprint);
        persistAttachmentSnapshots(row.getId(), 1, "REQUEST", attachments);
        if (row.getTargetUserId() != null) {
            notifyUsers(WorkOrderNotifySceneProvider.ASSIGNED, row, userId, "work-order-assigned:" + row.getId(), List.of(row.getTargetUserId()));
        } else {
            notifyUsers(WorkOrderNotifySceneProvider.POOL_AVAILABLE, row, userId, "work-order-pool:" + row.getId(), poolRecipients(row));
        }
        return row.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void take(Long id, WorkOrderActionReqVO req, Long userId) {
        WorkOrderDO row = requireForUpdate(id);
        String fingerprint = actionFingerprint("take", id, req, userId);
        if (isExactReplay(id, req.getIdempotencyKey(), "take", userId, fingerprint)) return;
        if (!Objects.equals(row.getTargetUserId(), userId)
                || orderMapper.take(id, userId, req.getVersion()) != 1) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_STATE_INVALID);
        }
        history(row, "PENDING_ACCEPT", "IN_PROGRESS", userId, null, req.getIdempotencyKey(), "take", fingerprint);
        notifyUsers(WorkOrderNotifySceneProvider.TAKEN, row, userId, "work-order-taken:" + row.getId() + ":" + req.getIdempotencyKey(), List.of(row.getSourceUserId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void claim(Long id, WorkOrderActionReqVO req, Long userId) {
        WorkOrderDO row = requireForUpdate(id);
        String fingerprint = actionFingerprint("claim", id, req, userId);
        if (isExactReplay(id, req.getIdempotencyKey(), "claim", userId, fingerprint)) return;
        AdminUserRespDTO target = row.getSceneVersionId() == null
                ? requireEligibleUser(userId, requireScene(row.getSceneCode()).getTargetPostCode())
                : requireQualification(userId, row.getCandidateQualificationMode(), row.getCandidateRoleScopesJson(), row.getCandidateDeptScopesJson());
        if (row.getTargetDeptId() != null && !Objects.equals(row.getTargetDeptId(), target.getDeptId())) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_PERMISSION_DENIED);
        }
        if (!("POOL".equals(row.getStatus()) || "AVAILABLE".equals(row.getStatus()))
                || orderMapper.claim(id, userId, target.getNickname(), req.getVersion()) != 1) {
            if (isExactReplay(id, req.getIdempotencyKey(), "claim", userId, fingerprint)) return;
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_CLAIM_ALREADY_TAKEN);
        }
        row.setTargetUserId(userId);
        row.setTargetNameSnapshot(target.getNickname());
        history(row, row.getStatus(), "IN_PROGRESS", userId, null, req.getIdempotencyKey(), "claim", fingerprint);
        notifyUsers(WorkOrderNotifySceneProvider.TAKEN, row, userId, "work-order-claimed:" + row.getId() + ":" + req.getIdempotencyKey(), List.of(row.getSourceUserId()));
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void complete(Long id, WorkOrderActionReqVO req, Long userId) {
        if (req.getResultRemark() == null || req.getResultRemark().isBlank()) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_STATE_INVALID);
        }
        WorkOrderDO row = requireForUpdate(id);
        String fingerprint = actionFingerprint("complete", id, req, userId);
        if (isExactReplay(id, req.getIdempotencyKey(), "complete", userId, fingerprint)) return;
        if (!Objects.equals(row.getTargetUserId(), userId)) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_PERMISSION_DENIED);
        List<Long> attachments = validateAttachments(req.getAttachmentIds(), userId);
        if (orderMapper.submitForReview(id, req.getVersion(), req.getResultRemark().trim(),
                JsonUtils.toJsonString(attachments)) != 1) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_STATE_INVALID);
        history(row, row.getStatus(), "PENDING_REVIEW", userId, req.getResultRemark().trim(),
                req.getIdempotencyKey(), "complete", fingerprint, attachments);
        persistAttachmentSnapshots(row.getId(), row.getCurrentRound() == null ? 1 : row.getCurrentRound(), "RESULT", attachments);
        notifyUsers(WorkOrderNotifySceneProvider.REVIEW_REQUESTED, row, userId, "work-order-review:" + row.getId() + ":" + req.getIdempotencyKey(), List.of(row.getSourceUserId()));
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void accept(Long id, WorkOrderActionReqVO req, Long userId) {
        transition(id, req, userId, List.of("PENDING_REVIEW", "COMPLETED_PENDING_ACCEPTANCE"), "COMPLETED", true, "accept");
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void returnForRework(Long id, WorkOrderActionReqVO req, Long userId) {
        if (req.getReason() == null || req.getReason().isBlank()) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_STATE_INVALID);
        }
        WorkOrderDO row = requireForUpdate(id);
        String fingerprint = actionFingerprint("return", id, req, userId);
        if (isExactReplay(id, req.getIdempotencyKey(), "return", userId, fingerprint)) return;
        if (!Objects.equals(row.getSourceUserId(), userId)) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_PERMISSION_DENIED);
        if (orderMapper.returnForRework(id, req.getVersion(), req.getReason().trim()) != 1) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_STATE_INVALID);
        history(row, "PENDING_REVIEW", "IN_PROGRESS", userId, req.getReason().trim(), req.getIdempotencyKey(), "return", fingerprint);
        notifyUsers(WorkOrderNotifySceneProvider.REWORKED, row, userId, "work-order-reworked:" + row.getId() + ":" + req.getIdempotencyKey(), List.of(row.getTargetUserId()));
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, WorkOrderActionReqVO req, Long userId) {
        requireReason(req);
        WorkOrderDO row = requireForUpdate(id);
        String fingerprint = actionFingerprint("reject", id, req, userId);
        if (isExactReplay(id, req.getIdempotencyKey(), "reject", userId, fingerprint)) return;
        if (!Objects.equals(row.getTargetUserId(), userId) || !"PENDING_ACCEPT".equals(row.getStatus())) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_PERMISSION_DENIED);
        }
        String targetStatus = "REJECTED_INVALID";
        int changed;
        if ("ROLE_POOL".equals(row.getRejectionStrategySnapshot())) {
            targetStatus = "AVAILABLE";
            changed = orderMapper.rejectToPool(id, req.getVersion(), "ROLE", row.getCandidateRoleScopesJson(), "[]");
        } else if ("DEPARTMENT_POOL".equals(row.getRejectionStrategySnapshot())) {
            targetStatus = "AVAILABLE";
            changed = orderMapper.rejectToPool(id, req.getVersion(), "DEPARTMENT", "[]", row.getCandidateDeptScopesJson());
        } else {
            changed = orderMapper.transition(id, req.getVersion(), "PENDING_ACCEPT", targetStatus, req.getReason().trim());
        }
        if (changed != 1) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_STATE_INVALID);
        history(row, "PENDING_ACCEPT", targetStatus, userId, req.getReason().trim(), req.getIdempotencyKey(), "reject", fingerprint);
        notifyUsers(WorkOrderNotifySceneProvider.REJECTED, row, userId, "work-order-rejected:" + row.getId() + ":" + req.getIdempotencyKey(), List.of(row.getSourceUserId()));
        if ("AVAILABLE".equals(targetStatus)) {
            WorkOrderDO rerouted = orderMapper.selectById(id);
            notifyUsers(WorkOrderNotifySceneProvider.POOL_AVAILABLE, rerouted, userId, "work-order-rerouted:" + row.getId() + ":" + req.getIdempotencyKey(), poolRecipients(rerouted));
        }
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long id, WorkOrderActionReqVO req, Long userId) {
        requireReason(req);
        transition(id, req, userId, List.of("PENDING_ACCEPT", "AVAILABLE", "POOL"), "WITHDRAWN", true, "withdraw");
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void terminate(Long id, WorkOrderActionReqVO req, Long userId) {
        requireReason(req);
        transition(id, req, userId, List.of("PENDING_REVIEW"), "TERMINATED_UNQUALIFIED", true, "terminate");
    }

    private void requireReason(WorkOrderActionReqVO req) {
        if (req.getReason() == null || req.getReason().isBlank()) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_STATE_INVALID);
    }

    private void transition(Long id, WorkOrderActionReqVO req, Long userId, List<String> fromStates, String to,
                            boolean sourceAction, String operation) {
        WorkOrderDO row = requireForUpdate(id);
        String fingerprint = actionFingerprint(operation, id, req, userId);
        if (isExactReplay(id, req.getIdempotencyKey(), operation, userId, fingerprint)) return;
        boolean authorized = sourceAction ? Objects.equals(row.getSourceUserId(), userId)
                : Objects.equals(row.getTargetUserId(), userId);
        if (!authorized) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_PERMISSION_DENIED);
        if (!fromStates.contains(row.getStatus()) || !Objects.equals(req.getVersion(), row.getVersion())
                || orderMapper.transition(id, req.getVersion(), row.getStatus(), to,
                sourceAction ? trimmed(req.getReason()) : null) != 1) {
            if (isExactReplay(id, req.getIdempotencyKey(), operation, userId, fingerprint)) return;
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_STATE_INVALID);
        }
        history(row, row.getStatus(), to, userId, trimmed(req.getReason()), req.getIdempotencyKey(), operation,
                fingerprint);
        String scene = switch (operation) {
            case "accept" -> WorkOrderNotifySceneProvider.COMPLETED;
            case "terminate" -> WorkOrderNotifySceneProvider.TERMINATED;
            case "withdraw" -> WorkOrderNotifySceneProvider.WITHDRAWN;
            default -> null;
        };
        Long recipient = "withdraw".equals(operation) ? row.getTargetUserId() : row.getTargetUserId();
        if (scene != null && recipient != null) notifyUsers(scene, row, userId,
                "work-order-" + operation + ":" + row.getId() + ":" + req.getIdempotencyKey(), List.of(recipient));
    }

    @Override
    public PageResult<WorkOrderRespVO> myPage(String status, String view, int pageNo, int pageSize, Long userId) {
        if (view != null && !Set.of("PENDING_ACCEPT", "PROCESSING", "PENDING_REVIEW", "CREATED", "CLOSED").contains(view)) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_STATE_INVALID);
        }
        return mapPage(orderMapper.selectMyPage(page(pageNo, pageSize), status, view, userId), userId);
    }

    @Override
    public PageResult<WorkOrderRespVO> pool(String sceneCode, int pageNo, int pageSize, Long userId) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<WorkOrderDO> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNo, pageSize);
        orderMapper.selectEligiblePool(page, sceneCode, userId);
        return mapPage(new PageResult<>(page.getRecords(), page.getTotal()), userId);
    }

    @Override
    public WorkOrderRespVO get(Long id, Long userId) {
        WorkOrderDO row = orderMapper.selectUnifiedById(id);
        if (row == null) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_NOT_EXISTS);
        boolean historyParticipant = historyMapper.selectByOrderId(id).stream().anyMatch(history -> Objects.equals(history.getOperatorUserId(), userId));
        boolean poolCandidate = "AVAILABLE".equals(row.getStatus()) && isQualified(userId, row.getCandidateQualificationMode(),
                row.getCandidateRoleScopesJson(), row.getCandidateDeptScopesJson());
        if (poolCandidate && row.getTargetDeptId() != null) {
            AdminUserRespDTO candidate = adminUserApi.getUser(userId);
            poolCandidate = candidate != null && Objects.equals(row.getTargetDeptId(), candidate.getDeptId());
        }
        if (!Objects.equals(row.getSourceUserId(), userId) && !Objects.equals(row.getTargetUserId(), userId)
                && !historyParticipant && !poolCandidate) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_PERMISSION_DENIED);
        }
        return toVO(row, userId);
    }

    @Override
    public PageResult<WorkOrderRespVO> auditPage(String status, int pageNo, int pageSize) {
        PageResult<WorkOrderDO> result = orderMapper.selectAuditPage(page(pageNo, pageSize), status);
        return new PageResult<>(result.getList().stream().map(row -> toVO(row, null)).toList(), result.getTotal());
    }

    @Override
    public WorkOrderRespVO auditGet(Long id) {
        WorkOrderDO row = orderMapper.selectAuditById(id);
        if (row == null) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_NOT_EXISTS);
        return toVO(row, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProductionEnvelope(String sceneCode, Long businessId, Long accountId, Long sourceUserId,
                                         Long targetUserId, Long targetDeptId, String remark, Map<String, Object> values,
                                         List<Long> attachmentIds, String idempotencyKey) {
        WorkOrderSceneDO scene = requireScene(sceneCode);
        WorkOrderSceneVersionDO version = scene.getPublishedVersionId() == null ? null
                : sceneVersionMapper.selectById(scene.getPublishedVersionId());
        if (version == null || !Integer.valueOf(1).equals(scene.getStatus())
                || !"PRODUCTION_TICKET".equals(version.getProcessorType()) || businessId == null
                || remark == null || remark.isBlank()) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_INVALID);
        }
        AdminUserRespDTO source = requireQualification(sourceUserId, version.getSourceQualificationMode(),
                version.getSourceRoleScopesJson(), version.getSourceDeptScopesJson());
        WorkOrderCreateReqVO assignment = new WorkOrderCreateReqVO();
        assignment.setTargetUserId(targetUserId); assignment.setTargetDeptId(targetDeptId);
        AdminUserRespDTO target = validateTarget(assignment, version);
        List<WorkOrderFieldDefinition> definitions = parseDefinitions(version.getFieldsJson());
        Map<String, Object> normalizedValues = normalizeValues(definitions, values);
        normalizedValues.put("accountId", accountId);
        List<Long> attachments = validateAttachments(mergeAttachmentIds(attachmentIds,
                dynamicAttachmentIds(definitions, normalizedValues)), sourceUserId, 100);
        WorkOrderDO existing = orderMapper.selectByBusiness("PRODUCTION_TICKET", businessId);
        if (existing != null) return existing.getId();

        WorkOrderDO row = new WorkOrderDO();
        row.setBusinessType("PRODUCTION_TICKET"); row.setBusinessId(businessId);
        row.setProcessorType("PRODUCTION_TICKET"); row.setOrderNo(allocateOrderNo(version));
        row.setSceneCode(version.getCode()); row.setSceneVersionId(version.getId());
        row.setSceneNameSnapshot(version.getName()); row.setAssignmentMode(targetUserId != null ? "PERSON" : "DEPARTMENT");
        row.setSourceUserId(sourceUserId); row.setTargetUserId(targetUserId); row.setTargetDeptId(targetDeptId);
        row.setSourceNameSnapshot(source.getNickname()); row.setTargetNameSnapshot(target == null ? null : target.getNickname());
        row.setStatus(targetUserId == null ? "AVAILABLE" : "PENDING_ACCEPT");
        row.setRejectionStrategySnapshot(version.getRejectionStrategy());
        row.setCandidateQualificationMode(version.getTargetQualificationMode());
        row.setCandidateRoleScopesJson(version.getTargetRoleScopesJson()); row.setCandidateDeptScopesJson(version.getTargetDeptScopesJson());
        row.setFieldSnapshotJson(JsonUtils.toJsonString(definitions)); row.setValueJson(JsonUtils.toJsonString(normalizedValues));
        row.setAttachmentIdsJson(JsonUtils.toJsonString(attachments)); row.setRemark(remark.trim()); row.setCurrentRound(1);
        row.setCompletionAttachmentIdsJson("[]"); row.setIdempotencyKey("production-envelope:" + idempotencyKey);
        row.setCommandUserId(sourceUserId); row.setRequestFingerprint(fingerprint(sceneCode, businessId, sourceUserId,
                targetUserId, targetDeptId, remark.trim(), canonicalize(normalizedValues), attachments)); row.setVersion(0);
        orderMapper.insert(row);
        history(row, null, row.getStatus(), sourceUserId, null, row.getIdempotencyKey(), "create", row.getRequestFingerprint());
        persistAttachmentSnapshots(row.getId(), 1, "REQUEST", attachments);
        notifyUsers(targetUserId == null ? WorkOrderNotifySceneProvider.POOL_AVAILABLE : WorkOrderNotifySceneProvider.ASSIGNED,
                row, sourceUserId, "production-envelope-created:" + businessId,
                targetUserId == null ? poolRecipients(row) : List.of(targetUserId));
        return row.getId();
    }

    @Override
    public void validateProductionPoolCandidate(Long businessId, Long userId) {
        WorkOrderDO row = requireProductionEnvelope(businessId);
        AdminUserRespDTO user = requireQualification(userId, row.getCandidateQualificationMode(),
                row.getCandidateRoleScopesJson(), row.getCandidateDeptScopesJson());
        if (row.getTargetDeptId() != null && !Objects.equals(row.getTargetDeptId(), user.getDeptId())) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_PERMISSION_DENIED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String rejectProductionAssignment(Long businessId, Long userId, String reason, String idempotencyKey) {
        WorkOrderDO row = requireProductionEnvelope(businessId);
        if (!Objects.equals(row.getTargetUserId(), userId) || !"PENDING_ACCEPT".equals(row.getStatus())) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_PERMISSION_DENIED);
        }
        String status = "INVALID".equals(row.getRejectionStrategySnapshot()) ? "REJECTED_INVALID" : "AVAILABLE";
        Long targetUser = "AVAILABLE".equals(status) ? null : row.getTargetUserId();
        if (orderMapper.updateBusinessProjection("PRODUCTION_TICKET", businessId, status, targetUser,
                targetUser == null ? null : row.getTargetNameSnapshot()) != 1) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_STATE_INVALID);
        }
        history(row, "PENDING_ACCEPT", status, userId, reason, idempotencyKey, "reject",
                fingerprint("production-reject", businessId, userId, reason));
        notifyUsers(WorkOrderNotifySceneProvider.REJECTED, row, userId, "production-envelope-rejected:" + businessId + ":" + idempotencyKey,
                List.of(row.getSourceUserId()));
        if ("AVAILABLE".equals(status)) {
            WorkOrderDO rerouted = requireProductionEnvelope(businessId);
            notifyUsers(WorkOrderNotifySceneProvider.POOL_AVAILABLE, rerouted, userId,
                    "production-envelope-rerouted:" + businessId + ":" + idempotencyKey, poolRecipients(rerouted));
        }
        return status;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncProductionStatus(Long businessId, String businessStatus, Long targetUserId, Long operatorUserId,
                                     String reason, String idempotencyKey) {
        WorkOrderDO row = requireProductionEnvelope(businessId);
        String projection = switch (businessStatus) {
            case "pending_accept" -> "PENDING_ACCEPT"; case "public_pool" -> "AVAILABLE";
            case "accepted", "in_production", "rejected" -> "IN_PROGRESS";
            case "submitted", "checking" -> "PENDING_REVIEW"; case "completed" -> "COMPLETED";
            case "assignment_rejected" -> "REJECTED_INVALID"; default -> row.getStatus();
        };
        AdminUserRespDTO target = targetUserId == null ? null : adminUserApi.getUser(targetUserId);
        if (orderMapper.updateBusinessProjection("PRODUCTION_TICKET", businessId, projection, targetUserId,
                target == null ? null : target.getNickname()) != 1) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_STATE_INVALID);
        }
        String operation = switch (businessStatus) {
            case "pending_accept" -> "production-pending-accept"; case "public_pool" -> "production-pool";
            case "accepted" -> "production-accept"; case "in_production" -> "production-start";
            case "submitted" -> "production-submit"; case "checking" -> "production-check";
            case "completed" -> "production-approve"; case "rejected" -> "production-return";
            case "assignment_rejected" -> "production-reject"; default -> "production-sync";
        };
        history(row, row.getStatus(), projection, operatorUserId, reason, idempotencyKey, operation,
                fingerprint(operation, businessId, businessStatus, targetUserId, operatorUserId, reason));
        if ("rejected".equals(businessStatus) && orderMapper.incrementProductionRound(businessId) != 1) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_STATE_INVALID);
        }
    }

    @Override
    public boolean isProductionTemplate(String sceneCode, Long userId) {
        WorkOrderSceneDO scene = requireScene(sceneCode);
        WorkOrderSceneVersionDO version = scene.getPublishedVersionId() == null ? null
                : sceneVersionMapper.selectById(scene.getPublishedVersionId());
        if (version == null || !Integer.valueOf(1).equals(scene.getStatus())
                || !"PRODUCTION_TICKET".equals(version.getProcessorType())) return false;
        requireQualification(userId, version.getSourceQualificationMode(), version.getSourceRoleScopesJson(),
                version.getSourceDeptScopesJson());
        return true;
    }

    @Override
    public Long getProductionEnvelopeId(Long businessId) {
        return requireProductionEnvelope(businessId).getId();
    }

    private WorkOrderDO requireProductionEnvelope(Long businessId) {
        WorkOrderDO row = orderMapper.selectByBusiness("PRODUCTION_TICKET", businessId);
        if (row == null) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_NOT_EXISTS);
        return row;
    }

    private WorkOrderSceneDO toSceneDO(WorkOrderSceneCreateReqVO req) {
        WorkOrderSceneDO row = BeanUtils.toBean(req, WorkOrderSceneDO.class);
        if (req.getCategoryValue() != null) {
            DictDataRespDTO category = dictDataApi.getDictDataList("zsjos_work_order_category").stream()
                    .filter(data -> Objects.equals(data.getValue(), req.getCategoryValue()))
                    .findFirst().orElseThrow(() -> exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_INVALID));
            row.setCategoryLabelSnapshot(category.getLabel());
        }
        row.setFieldsJson(JsonUtils.toJsonString(req.getFields()));
        row.setAllowedAssignmentTypesJson(JsonUtils.toJsonString(req.getAllowedAssignmentTypes()));
        row.setSourceRoleScopesJson(scopeJson(req.getSourceRoleIds(), true));
        row.setSourceDeptScopesJson(scopeJson(req.getSourceDeptIds(), false));
        row.setTargetRoleScopesJson(scopeJson(req.getTargetRoleIds(), true));
        row.setTargetDeptScopesJson(scopeJson(req.getTargetDeptIds(), false));
        row.setLifecycleStatus("DRAFT");
        return row;
    }

    private WorkOrderSceneRespVO toSceneVO(WorkOrderSceneDO row) {
        WorkOrderSceneRespVO result = BeanUtils.toBean(row, WorkOrderSceneRespVO.class);
        result.setFields(parseDefinitions(row.getFieldsJson()));
        result.setAllowedAssignmentTypes(parseStrings(row.getAllowedAssignmentTypesJson()));
        result.setSourceRoleIds(scopeIds(row.getSourceRoleScopesJson()));
        result.setSourceDeptIds(scopeIds(row.getSourceDeptScopesJson()));
        result.setTargetRoleIds(scopeIds(row.getTargetRoleScopesJson()));
        result.setTargetDeptIds(scopeIds(row.getTargetDeptScopesJson()));
        return result;
    }

    private void validateScene(WorkOrderSceneCreateReqVO req) {
        if (req.getProcessorType() != null) {
            if (!Set.of("GENERIC", "PRODUCTION_TICKET").contains(req.getProcessorType())
                    || !validQualification(req.getSourceQualificationMode(), req.getSourceRoleIds(), req.getSourceDeptIds())
                    || !validQualification(req.getTargetQualificationMode(), req.getTargetRoleIds(), req.getTargetDeptIds())
                    || req.getAllowedAssignmentTypes() == null || req.getAllowedAssignmentTypes().isEmpty()
                    || !Set.of("PERSON", "DEPARTMENT").containsAll(req.getAllowedAssignmentTypes())
                    || !Set.of("INVALID", "ROLE_POOL", "DEPARTMENT_POOL").contains(req.getRejectionStrategy())
                    || req.getNumberPrefix() == null || !req.getNumberPrefix().matches("[A-Z0-9]{2,12}")
                    || !Set.of("DAILY", "MONTHLY", "YEARLY", "NONE").contains(req.getNumberResetPeriod())) {
                throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_INVALID);
            }
            validateDefinitions(req.getFields());
            return;
        }
        var sourcePost = postApi.getPostByCode(req.getSourcePostCode());
        var targetPost = postApi.getPostByCode(req.getTargetPostCode());
        if (!List.of("DIRECT", "PUBLIC_POOL").contains(req.getAssignmentMode())
                || sourcePost == null || targetPost == null
                || !CommonStatusEnum.ENABLE.getStatus().equals(sourcePost.getStatus())
                || !CommonStatusEnum.ENABLE.getStatus().equals(targetPost.getStatus())) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_INVALID);
        }
        validateDefinitions(req.getFields());
    }

    private List<WorkOrderFieldDefinition> parseDefinitions(String json) {
        try {
            List<WorkOrderFieldDefinition> definitions = JsonUtils.parseArray(json, WorkOrderFieldDefinition.class);
            validateDefinitions(definitions);
            return definitions;
        } catch (RuntimeException error) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
        }
    }

    private void validateDefinitions(List<WorkOrderFieldDefinition> definitions) {
        if (definitions == null || definitions.size() > 100) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
        }
        Set<String> keys = new HashSet<>();
        for (WorkOrderFieldDefinition field : definitions) {
            if (field == null) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
            boolean dictionary = "dictionary".equals(field.type());
            if (field.key() == null || !field.key().matches("[a-z][a-z0-9_]{0,63}")
                    || field.label() == null || field.label().isBlank() || field.label().length() > 128
                    || !FIELD_TYPES.contains(field.type()) || !keys.add(field.key())
                    || dictionary != (field.dictionaryType() != null && !field.dictionaryType().isBlank())) {
                throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
            }
        }
    }

    private Map<String, Object> normalizeValues(List<WorkOrderFieldDefinition> definitions,
                                                 Map<String, Object> submitted) {
        try {
            Map<String, WorkOrderFieldDefinition> byKey = new LinkedHashMap<>();
            definitions.forEach(field -> byKey.put(field.key(), field));
            submitted = submitted == null ? Map.of() : submitted;
            if (!byKey.keySet().containsAll(submitted.keySet())) {
                throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
            }
            Set<Long> userIds = new LinkedHashSet<>();
            Set<Long> departmentIds = new LinkedHashSet<>();
            Map<String, Set<String>> dictionaryValues = new LinkedHashMap<>();
            for (WorkOrderFieldDefinition field : definitions) {
                Object value = submitted.get(field.key());
                if (missing(value)) continue;
                if ("user".equals(field.type())) userIds.add(requireLong(value));
                if ("department".equals(field.type())) departmentIds.add(requireLong(value));
                if ("dictionary".equals(field.type())) dictionaryValues
                        .computeIfAbsent(field.dictionaryType(), ignored -> new LinkedHashSet<>())
                        .add(requireString(value));
            }
            Map<Long, AdminUserRespDTO> users = userIds.isEmpty() ? Map.of() : adminUserApi.getUserMap(userIds);
            Map<Long, cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO> departments = departmentIds.isEmpty()
                    ? Map.of() : deptApi.getDeptMap(departmentIds);
            Map<String, Map<String, DictDataRespDTO>> dictionaries = new LinkedHashMap<>();
            for (Map.Entry<String, Set<String>> entry : dictionaryValues.entrySet()) {
                dictDataApi.validateDictDataList(entry.getKey(), entry.getValue());
                Map<String, DictDataRespDTO> values = new LinkedHashMap<>();
                dictDataApi.getDictDataList(entry.getKey()).forEach(data -> values.put(data.getValue(), data));
                dictionaries.put(entry.getKey(), values);
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (WorkOrderFieldDefinition field : definitions) {
                Object value = submitted.get(field.key());
                if (missing(value)) {
                    if (Boolean.TRUE.equals(field.required())) {
                        throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
                    }
                    continue;
                }
                normalized.put(field.key(), normalizeValue(field, value, users, departments, dictionaries));
            }
            return normalized;
        } catch (RuntimeException error) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
        }
    }

    private Object normalizeValue(WorkOrderFieldDefinition field, Object value,
                                  Map<Long, AdminUserRespDTO> users,
                                  Map<Long, cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO> departments,
                                  Map<String, Map<String, DictDataRespDTO>> dictionaries) {
        return switch (field.type()) {
            case "text", "textarea" -> requireString(value);
            case "number" -> new BigDecimal(String.valueOf(value)).stripTrailingZeros();
            case "date" -> LocalDate.parse(requireString(value)).toString();
            case "datetime" -> LocalDateTime.parse(requireString(value)).toString();
            case "user" -> userSnapshot(requireLong(value), users);
            case "department" -> departmentSnapshot(requireLong(value), departments);
            case "dictionary" -> dictionarySnapshot(field.dictionaryType(), requireString(value), dictionaries);
            case "attachment" -> normalizeDynamicAttachmentIds(value);
            default -> throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
        };
    }

    private Map<String, Object> userSnapshot(Long id, Map<Long, AdminUserRespDTO> users) {
        AdminUserRespDTO user = users.get(id);
        if (user == null || !CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
        }
        return snapshot("id", id, user.getNickname());
    }

    private Map<String, Object> departmentSnapshot(Long id,
            Map<Long, cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO> departments) {
        var dept = departments.get(id);
        if (dept == null || !CommonStatusEnum.ENABLE.getStatus().equals(dept.getStatus())) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
        }
        return snapshot("id", id, dept.getName());
    }

    private Map<String, Object> dictionarySnapshot(String type, String value,
            Map<String, Map<String, DictDataRespDTO>> dictionaries) {
        DictDataRespDTO data = Optional.ofNullable(dictionaries.get(type)).map(values -> values.get(value))
                .orElseThrow(() -> exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID));
        String label = requireSnapshotLabel(data.getLabel());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type);
        result.put("value", value);
        result.put("label", label);
        return result;
    }

    private List<Long> validateAttachments(List<Long> ids, Long userId) {
        return validateAttachments(ids, userId, 20);
    }

    private List<Long> validateAttachments(List<Long> ids, Long userId, int maxSize) {
        List<Long> normalized = normalizeAttachmentIds(ids, maxSize);
        try {
            for (Long id : normalized) {
                FileInfoRespDTO file = fileApi.getFileInfo(id);
                if (file == null || file.getPath() == null || !file.getPath().startsWith("zsjos/work-order/")
                        || !String.valueOf(userId).equals(file.getCreator())) {
                    throw exception(ZsjosErrorCodeConstants.WORK_ORDER_ATTACHMENT_INVALID);
                }
            }
            return normalized;
        } catch (RuntimeException error) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_ATTACHMENT_INVALID);
        }
    }

    private AdminUserRespDTO validateTarget(WorkOrderCreateReqVO req, WorkOrderSceneDO scene) {
        if ("DIRECT".equals(scene.getAssignmentMode())) {
            if (req.getTargetUserId() == null) {
                throw exception(ZsjosErrorCodeConstants.WORK_ORDER_PERMISSION_DENIED);
            }
            return requireEligibleUser(req.getTargetUserId(), scene.getTargetPostCode());
        }
        if (req.getTargetUserId() != null) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_STATE_INVALID);
        return null;
    }

    private Long requireCreateReplay(WorkOrderDO row, Long userId, String fingerprint) {
        if (!Objects.equals(row.getCommandUserId(), userId)
                || !Objects.equals(row.getRequestFingerprint(), fingerprint)) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_IDEMPOTENCY_CONFLICT);
        }
        return row.getId();
    }

    private boolean isExactReplay(Long orderId, String key, String operation, Long userId, String fingerprint) {
        WorkOrderHistoryDO replay = historyMapper.selectByOrderAndKey(orderId, key);
        if (replay == null) return false;
        if (!Objects.equals(replay.getOperation(), operation)
                || !Objects.equals(replay.getOperatorUserId(), userId)
                || !Objects.equals(replay.getRequestFingerprint(), fingerprint)) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_IDEMPOTENCY_CONFLICT);
        }
        return true;
    }

    private String actionFingerprint(String operation, Long id, WorkOrderActionReqVO req, Long userId) {
        return fingerprint(operation, id, userId, req.getVersion(), trimmed(req.getReason()),
                trimmed(req.getResultRemark()), normalizeAttachmentIds(req.getAttachmentIds()));
    }

    private String fingerprint(Object... values) {
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(Arrays.asList(values)));
    }

    private void history(WorkOrderDO row, String from, String to, Long userId, String reason, String key,
                         String operation, String fingerprint) {
        history(row, from, to, userId, reason, key, operation, fingerprint, List.of());
    }

    private void history(WorkOrderDO row, String from, String to, Long userId, String reason, String key,
                         String operation, String fingerprint, List<Long> attachmentIds) {
        WorkOrderHistoryDO history = new WorkOrderHistoryDO();
        history.setWorkOrderId(row.getId());
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setOperatorUserId(userId);
        history.setReason(reason);
        history.setIdempotencyKey(key);
        history.setOperation(operation);
        history.setRequestFingerprint(fingerprint);
        history.setRoundNo(row.getCurrentRound() == null ? 1 : row.getCurrentRound());
        if ("complete".equals(operation)) history.setResultRemark(reason);
        history.setAttachmentIdsJson(JsonUtils.toJsonString(attachmentIds == null ? List.of() : attachmentIds));
        history.setOperatedAt(LocalDateTime.now());
        historyMapper.insert(history);
    }

    private PageResult<WorkOrderRespVO> mapPage(PageResult<WorkOrderDO> page, Long userId) {
        return new PageResult<>(page.getList().stream().map(row -> toVO(row, userId)).toList(), page.getTotal());
    }

    private void persistAttachmentSnapshots(Long orderId, int roundNo, String phase, List<Long> fileIds) {
        int sort = 0;
        for (Long fileId : fileIds) {
            FileInfoRespDTO file = fileApi.getFileInfo(fileId);
            WorkOrderAttachmentDO snapshot = new WorkOrderAttachmentDO();
            snapshot.setWorkOrderId(orderId); snapshot.setRoundNo(roundNo); snapshot.setPhase(phase); snapshot.setFileId(fileId);
            snapshot.setFileNameSnapshot(file.getName()); snapshot.setMimeTypeSnapshot(file.getType()); snapshot.setFileSizeSnapshot(file.getSize()); snapshot.setSort(sort++);
            attachmentMapper.insert(snapshot);
        }
    }

    private void notifyUsers(String scene, WorkOrderDO row, Long operator, String key, Collection<Long> recipients) {
        if (row == null || recipients == null) return;
        List<Long> targetIds = recipients.stream().filter(Objects::nonNull).filter(id -> !Objects.equals(id, operator)).distinct().toList();
        if (targetIds.isEmpty()) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recipientUserIds", targetIds); payload.put("orderNo", row.getOrderNo());
        payload.put("sceneName", row.getSceneNameSnapshot());
        payload.put("deepLink", "/zsjos/work-orders/mine?workOrderId=" + row.getId());
        workflowEventService.notify(scene, "work-order", row.getId(), null, operator, key, payload);
    }

    private List<Long> poolRecipients(WorkOrderDO row) {
        if (row == null || row.getCandidateQualificationMode() == null) return List.of();
        List<Long> result = new ArrayList<>(); int pageNo = 1;
        while (true) {
            AdminUserCandidatePageReqDTO query = new AdminUserCandidatePageReqDTO();
            query.setQualificationMode(row.getCandidateQualificationMode());
            query.setRoleIds(new LinkedHashSet<>(scopeIds(row.getCandidateRoleScopesJson())));
            query.setDeptIds(new LinkedHashSet<>(scopeIds(row.getCandidateDeptScopesJson())));
            query.setPageNo(pageNo); query.setPageSize(100);
            PageResult<AdminUserRespDTO> candidates = adminUserApi.getCandidateUserPage(query);
            if (candidates == null || candidates.getList() == null || candidates.getList().isEmpty()) break;
            candidates.getList().stream().filter(user -> row.getTargetDeptId() == null || Objects.equals(row.getTargetDeptId(), user.getDeptId()))
                    .map(AdminUserRespDTO::getId).forEach(result::add);
            if ((long) pageNo * 100 >= candidates.getTotal()) break;
            pageNo++;
        }
        return result;
    }

    private WorkOrderRespVO toVO(WorkOrderDO row, Long userId) {
        WorkOrderRespVO result = BeanUtils.toBean(row, WorkOrderRespVO.class);
        result.setSceneName(row.getSceneNameSnapshot());
        result.setSourceName(row.getSourceNameSnapshot());
        result.setTargetName(row.getTargetNameSnapshot());
        result.setFields(parseDefinitions(row.getFieldSnapshotJson()));
        result.setValues(JsonUtils.parseObject(row.getValueJson(), Map.class));
        result.setAttachmentIds(JsonUtils.parseArray(row.getAttachmentIdsJson(), Long.class));
        result.setRequestAttachments(Optional.ofNullable(
                attachmentMapper.selectListByOrderIdAndPhase(row.getId(), "REQUEST")).orElse(List.of()).stream()
                .map(attachment -> {
                    WorkOrderFileRespVO file = new WorkOrderFileRespVO();
                    file.setId(attachment.getFileId()); file.setName(attachment.getFileNameSnapshot());
                    file.setType(attachment.getMimeTypeSnapshot()); file.setSize(attachment.getFileSizeSnapshot());
                    return file;
                }).toList());
        result.setCompletionAttachmentIds(row.getCompletionAttachmentIdsJson() == null ? List.of()
                : JsonUtils.parseArray(row.getCompletionAttachmentIdsJson(), Long.class));
        List<WorkOrderHistoryDO> histories = historyMapper.selectByOrderId(row.getId());
        Map<Long, AdminUserRespDTO> operators = histories.isEmpty() ? Map.of()
                : Optional.ofNullable(adminUserApi.getUserMap(histories.stream().map(WorkOrderHistoryDO::getOperatorUserId)
                .collect(java.util.stream.Collectors.toSet()))).orElse(Map.of());
        result.setTimeline(histories.stream().map(history -> {
            WorkOrderTimelineRespVO item = BeanUtils.toBean(history, WorkOrderTimelineRespVO.class);
            item.setOperatorName(Optional.ofNullable(operators.get(history.getOperatorUserId())).map(AdminUserRespDTO::getNickname).orElse(null));
            item.setAttachmentIds(history.getAttachmentIdsJson() == null ? List.of()
                    : JsonUtils.parseArray(history.getAttachmentIdsJson(), Long.class));
            return item;
        }).toList());
        result.setAvailableActions(availableActions(row, userId));
        return result;
    }

    private List<String> availableActions(WorkOrderDO row, Long userId) {
        List<String> actions = new ArrayList<>();
        if (userId == null || !"GENERIC".equals(row.getBusinessType())) return actions;
        if (Objects.equals(row.getTargetUserId(), userId) && "PENDING_ACCEPT".equals(row.getStatus())) {
            actions.add("take"); actions.add("reject");
        }
        if (Objects.equals(row.getTargetUserId(), userId) && "IN_PROGRESS".equals(row.getStatus())) actions.add("complete");
        if (Objects.equals(row.getSourceUserId(), userId) && Set.of("PENDING_ACCEPT", "AVAILABLE").contains(row.getStatus())) actions.add("withdraw");
        if (Objects.equals(row.getSourceUserId(), userId) && "PENDING_REVIEW".equals(row.getStatus())) {
            actions.add("accept"); actions.add("terminate"); actions.add("return");
        }
        if ("AVAILABLE".equals(row.getStatus()) && isQualified(userId, row.getCandidateQualificationMode(),
                row.getCandidateRoleScopesJson(), row.getCandidateDeptScopesJson())) actions.add("claim");
        return actions;
    }

    private WorkOrderDO require(Long id) {
        WorkOrderDO row = orderMapper.selectById(id);
        if (row == null) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_NOT_EXISTS);
        return row;
    }

    private WorkOrderDO requireForUpdate(Long id) {
        WorkOrderDO row = orderMapper.selectByIdForUpdate(id);
        if (row == null) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_NOT_EXISTS);
        return row;
    }

    private WorkOrderSceneDO requireScene(String code) {
        WorkOrderSceneDO row = sceneMapper.selectByCode(code);
        if (row == null) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_NOT_EXISTS);
        return row;
    }

    private WorkOrderSceneDO requireScene(Long id) {
        WorkOrderSceneDO row = sceneMapper.selectById(id);
        if (row == null) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_NOT_EXISTS);
        return row;
    }

    private WorkOrderSceneVersionDO requirePublishedVersion(String sceneCode) {
        WorkOrderSceneDO scene = requireScene(sceneCode);
        if (!Integer.valueOf(1).equals(scene.getStatus()) || scene.getPublishedVersionId() == null) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_INVALID);
        }
        WorkOrderSceneVersionDO version = sceneVersionMapper.selectById(scene.getPublishedVersionId());
        if (version == null) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_INVALID);
        return version;
    }

    private AdminUserRespDTO requireEligibleUser(Long userId, String postCode) {
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        var post = postApi.getPostByCode(postCode);
        if (user == null || post == null || !CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())
                || !CommonStatusEnum.ENABLE.getStatus().equals(post.getStatus())
                || user.getPostIds() == null || !user.getPostIds().contains(post.getId())) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_PERMISSION_DENIED);
        }
        return user;
    }

    private AdminUserRespDTO validateTarget(WorkOrderCreateReqVO req, WorkOrderSceneVersionDO version) {
        boolean person = req.getTargetUserId() != null;
        boolean department = req.getTargetDeptId() != null;
        if (person == department) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_STATE_INVALID);
        List<String> allowed = parseStrings(version.getAllowedAssignmentTypesJson());
        if (person && !allowed.contains("PERSON") || department && !allowed.contains("DEPARTMENT")) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_PERMISSION_DENIED);
        }
        if (person) return requireQualification(req.getTargetUserId(), version.getTargetQualificationMode(),
                version.getTargetRoleScopesJson(), version.getTargetDeptScopesJson());
        var dept = deptApi.getDept(req.getTargetDeptId());
        if (dept == null || !CommonStatusEnum.ENABLE.getStatus().equals(dept.getStatus())) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_PERMISSION_DENIED);
        }
        if (candidateDepartments(version).stream().noneMatch(item -> Objects.equals(item.getId(), req.getTargetDeptId()))) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_PERMISSION_DENIED);
        }
        return null;
    }

    private List<WorkOrderCandidateRespVO> candidateDepartments(WorkOrderSceneVersionDO version) {
        LinkedHashSet<Long> departmentIds = new LinkedHashSet<>();
        int pageNo = 1;
        while (true) {
            AdminUserCandidatePageReqDTO query = new AdminUserCandidatePageReqDTO();
            query.setQualificationMode(version.getTargetQualificationMode());
            query.setRoleIds(new LinkedHashSet<>(scopeIds(version.getTargetRoleScopesJson())));
            query.setDeptIds(new LinkedHashSet<>(scopeIds(version.getTargetDeptScopesJson())));
            query.setPageNo(pageNo); query.setPageSize(100);
            PageResult<AdminUserRespDTO> candidates = adminUserApi.getCandidateUserPage(query);
            if (candidates == null || candidates.getList() == null || candidates.getList().isEmpty()) break;
            candidates.getList().stream().map(AdminUserRespDTO::getDeptId).filter(Objects::nonNull)
                    .forEach(departmentIds::add);
            if ((long) pageNo * 100 >= candidates.getTotal()) break;
            pageNo++;
        }
        if (departmentIds.isEmpty()) return List.of();
        Map<Long, cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO> departments =
                Optional.ofNullable(deptApi.getDeptMap(departmentIds)).orElse(Map.of());
        return departmentIds.stream().map(departments::get).filter(Objects::nonNull)
                .filter(dept -> CommonStatusEnum.ENABLE.getStatus().equals(dept.getStatus()))
                .map(dept -> {
                    WorkOrderCandidateRespVO item = new WorkOrderCandidateRespVO();
                    item.setId(dept.getId()); item.setDeptId(dept.getId()); item.setName(dept.getName());
                    return item;
                }).sorted(Comparator.comparing(WorkOrderCandidateRespVO::getName)
                        .thenComparing(WorkOrderCandidateRespVO::getId)).toList();
    }

    private void validatePublishedScene(WorkOrderSceneDO scene) {
        if (scene.getProcessorType() == null || scene.getCategoryValue() == null || scene.getAllowedAssignmentTypesJson() == null) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_INVALID);
        }
        roleApi.validRoleList(scopeIds(scene.getSourceRoleScopesJson()));
        roleApi.validRoleList(scopeIds(scene.getTargetRoleScopesJson()));
        deptApi.validateDeptList(scopeIds(scene.getSourceDeptScopesJson()));
        deptApi.validateDeptList(scopeIds(scene.getTargetDeptScopesJson()));
        parseDefinitions(scene.getFieldsJson());
    }

    private WorkOrderSceneRespVO toSceneVO(WorkOrderSceneVersionDO row) {
        WorkOrderSceneRespVO result = BeanUtils.toBean(row, WorkOrderSceneRespVO.class);
        result.setId(row.getSceneId()); result.setPublishedVersionId(row.getId()); result.setPublishedVersionNo(row.getVersionNo());
        result.setCategoryLabel(row.getCategoryLabelSnapshot()); result.setLifecycleStatus("PUBLISHED");
        result.setFields(parseDefinitions(row.getFieldsJson())); result.setAllowedAssignmentTypes(parseStrings(row.getAllowedAssignmentTypesJson()));
        result.setSourceRoleIds(scopeIds(row.getSourceRoleScopesJson())); result.setSourceDeptIds(scopeIds(row.getSourceDeptScopesJson()));
        result.setTargetRoleIds(scopeIds(row.getTargetRoleScopesJson())); result.setTargetDeptIds(scopeIds(row.getTargetDeptScopesJson()));
        result.setPublishedAt(row.getPublishedAt());
        return result;
    }

    private String numberPreview(WorkOrderSceneDO scene) {
        LocalDate now = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        String date = switch (scene.getNumberResetPeriod()) {
            case "DAILY" -> now.format(DateTimeFormatter.BASIC_ISO_DATE);
            case "MONTHLY" -> now.format(DateTimeFormatter.ofPattern("yyyyMM"));
            case "YEARLY" -> now.format(DateTimeFormatter.ofPattern("yyyy"));
            default -> "";
        };
        return scene.getNumberPrefix() + date + "1".repeat(scene.getNumberSequenceWidth());
    }

    private AdminUserRespDTO requireQualification(Long userId, String mode, String rolesJson, String deptsJson) {
        AdminUserRespDTO user = adminUserApi.getUser(userId);
        if (user == null || !CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus())
                || !matchesQualification(user, permissionApi.getEnabledRoleIdsByUserId(userId), mode, scopeIds(rolesJson), scopeIds(deptsJson))) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_PERMISSION_DENIED);
        }
        return user;
    }

    private boolean isQualified(Long userId, String mode, String rolesJson, String deptsJson) {
        try { requireQualification(userId, mode, rolesJson, deptsJson); return true; } catch (RuntimeException ignored) { return false; }
    }

    private static boolean matchesQualification(AdminUserRespDTO user, Set<Long> userRoles, String mode,
                                                List<Long> roleIds, List<Long> deptIds) {
        boolean role = userRoles != null && roleIds.stream().anyMatch(userRoles::contains);
        boolean dept = user.getDeptId() != null && deptIds.contains(user.getDeptId());
        return switch (mode == null ? "" : mode) { case "ROLE" -> role; case "DEPARTMENT" -> dept; case "ROLE_AND_DEPARTMENT" -> role && dept; default -> false; };
    }

    private String scopeJson(List<Long> ids, boolean role) {
        if (ids == null || ids.isEmpty()) return "[]";
        return JsonUtils.toJsonString((role ? roleApi.getRoleList(ids) : deptApi.getDeptList(ids)).stream()
                .map(item -> role ? new WorkOrderScopeSnapshot(((cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO) item).getId(), ((cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO) item).getName())
                        : new WorkOrderScopeSnapshot(((cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO) item).getId(), ((cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO) item).getName())).toList());
    }

    private static boolean validQualification(String mode, List<Long> roles, List<Long> depts) {
        return switch (mode == null ? "" : mode) { case "ROLE" -> roles != null && !roles.isEmpty(); case "DEPARTMENT" -> depts != null && !depts.isEmpty(); case "ROLE_AND_DEPARTMENT" -> roles != null && !roles.isEmpty() && depts != null && !depts.isEmpty(); default -> false; };
    }

    private static List<Long> scopeIds(String json) { if (json == null || json.isBlank()) return List.of(); try { return JsonUtils.parseArray(json, WorkOrderScopeSnapshot.class).stream().map(WorkOrderScopeSnapshot::id).toList(); } catch (RuntimeException ignored) { return List.of(); } }
    private static List<String> parseStrings(String json) { return json == null ? List.of() : JsonUtils.parseArray(json, String.class); }

    private String allocateOrderNo(WorkOrderSceneVersionDO version) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        String resetKey = switch (version.getNumberResetPeriod()) {
            case "DAILY" -> today.format(DateTimeFormatter.BASIC_ISO_DATE);
            case "MONTHLY" -> today.format(DateTimeFormatter.ofPattern("yyyyMM"));
            case "YEARLY" -> today.format(DateTimeFormatter.ofPattern("yyyy"));
            case "NONE" -> "ALL";
            default -> throw exception(ZsjosErrorCodeConstants.WORK_ORDER_SCENE_INVALID);
        };
        Long tenantId = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId();
        numberCounterMapper.increment(tenantId, version.getNumberPrefix(), resetKey);
        long value = numberCounterMapper.selectAllocatedValue();
        long limit = (long) Math.pow(10, version.getNumberSequenceWidth());
        if (value >= limit) throw exception(ZsjosErrorCodeConstants.WORK_ORDER_NUMBER_OVERFLOW);
        String datePart = "ALL".equals(resetKey) ? "" : resetKey;
        return version.getNumberPrefix() + datePart + String.format(Locale.ROOT, "%0" + version.getNumberSequenceWidth() + "d", value);
    }

    private PageParam page(int pageNo, int pageSize) {
        PageParam page = new PageParam();
        page.setPageNo(pageNo);
        page.setPageSize(pageSize);
        return page;
    }

    private static boolean missing(Object value) {
        return value == null || value instanceof String text && text.isBlank()
                || value instanceof Collection<?> collection && collection.isEmpty();
    }

    private static String requireString(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
        }
        return text.trim();
    }

    private static Long requireLong(Object value) {
        try {
            BigDecimal decimal = value instanceof Number number ? new BigDecimal(number.toString())
                    : new BigDecimal(requireString(value));
            if (decimal.signum() < 0) throw new ArithmeticException();
            return decimal.longValueExact();
        } catch (NumberFormatException | ArithmeticException error) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
        }
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    private static Map<String, Object> snapshot(String idKey, Long id, String label) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(idKey, id);
        result.put("label", requireSnapshotLabel(label));
        return result;
    }

    private static String requireSnapshotLabel(String label) {
        if (label == null || label.isBlank()) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
        }
        return label;
    }

    private static List<Long> normalizeAttachmentIds(List<Long> ids) {
        return normalizeAttachmentIds(ids, 20);
    }

    private static List<Long> normalizeAttachmentIds(List<Long> ids, int maxSize) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Long> normalized = ids.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (normalized.size() != ids.size() || normalized.size() > maxSize || normalized.stream().anyMatch(id -> id <= 0)) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_ATTACHMENT_INVALID);
        }
        return normalized;
    }

    private static List<Long> normalizeDynamicAttachmentIds(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
        }
        try {
            List<Long> ids = collection.stream().map(WorkOrderServiceImpl::requireLong).toList();
            return normalizeAttachmentIds(ids);
        } catch (RuntimeException error) {
            throw exception(ZsjosErrorCodeConstants.WORK_ORDER_FIELD_INVALID);
        }
    }

    private static List<Long> dynamicAttachmentIds(List<WorkOrderFieldDefinition> definitions,
                                                   Map<String, Object> values) {
        return definitions.stream().filter(field -> "attachment".equals(field.type()))
                .map(field -> values.get(field.key())).filter(Objects::nonNull)
                .flatMap(value -> ((Collection<?>) value).stream()).map(WorkOrderServiceImpl::requireLong)
                .distinct().sorted().toList();
    }

    private static List<Long> mergeAttachmentIds(List<Long> primary, List<Long> dynamic) {
        return java.util.stream.Stream.concat(
                primary == null ? java.util.stream.Stream.empty() : primary.stream(),
                dynamic == null ? java.util.stream.Stream.empty() : dynamic.stream())
                .distinct().sorted().toList();
    }

    private static Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), canonicalize(item)));
            return sorted;
        }
        if (value instanceof Collection<?> collection) return collection.stream().map(WorkOrderServiceImpl::canonicalize).toList();
        return value;
    }
}
