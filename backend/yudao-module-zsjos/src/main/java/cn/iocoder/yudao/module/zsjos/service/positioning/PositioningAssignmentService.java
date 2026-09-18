package cn.iocoder.yudao.module.zsjos.service.positioning;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.positioning.vo.PositioningApplyReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.account.MediaAccountObjectPermissionProvider;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.List;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class PositioningAssignmentService {
    @Resource private PositioningServiceCardMapper masterMapper;
    @Resource private PositioningCardMapper cardMapper;
    @Resource private PositioningCardSubmissionMapper submissionMapper;
    @Resource private PositioningApplicationMapper applicationMapper;
    @Resource private PositioningApplicationLogMapper logMapper;
    @Resource private ServiceRelationMapper relationMapper;
    @Resource private MediaAccountMapper accountMapper;
    @Resource private MediaAccountObjectPermissionProvider accountPermission;
    @Resource private PermissionApi permissionApi;
    @Resource private PositioningCardObjectPermissionProvider cardPermission;

    public Long masterId(Long relationId) {
        if (relationId == null) return null;
        var selected = masterMapper.find(relationId);
        if (selected != null) return selected.getCardId();
        var cards = cardMapper.selectByService(relationId);
        return cards.size() == 1 ? cards.getFirst().getId() : null;
    }
    /** Called within a relation-locked transaction; historical siblings are never merged. */
    public void register(Long relationId, Long studentId, Long cardId) {
        if (masterMapper.find(relationId) == null) masterMapper.insert(new PositioningServiceCardDO()
                .setServiceRelationId(relationId).setStudentPersonId(studentId).setCardId(cardId).setVersion(0));
    }
    public void requireMaster(PositioningCardDO card) {
        if (card.getServiceRelationId() == null || !Objects.equals(masterId(card.getServiceRelationId()), card.getId()))
            throw exception(POSITIONING_MASTER_REQUIRED);
    }
    @ZsjosPermission(bizType="student-service", bizId="#relationId", action="read")
    @Transactional(rollbackFor=Exception.class)
    public void selectMaster(Long relationId, Long cardId, Long userId) {
        var relation = relationMapper.selectByIdForUpdate(relationId, TenantContextHolder.getRequiredTenantId());
        var card = cardMapper.selectById(cardId);
        if (relation == null || !Objects.equals(relation.getContentDirectorUserId(), userId))
            throw exception(POSITIONING_CARD_PERMISSION_DENIED);
        if (card == null || !Objects.equals(card.getServiceRelationId(), relationId)
                || !Objects.equals(card.getStudentPersonId(), relation.getPersonId())
                || !Objects.equals(card.getDirectorUserId(), userId)) throw exception(POSITIONING_REFERENCE_INVALID);
        Long master = masterId(relationId);
        if (master != null && !master.equals(cardId)) throw exception(POSITIONING_MASTER_ALREADY_SELECTED);
        register(relationId, relation.getPersonId(), cardId);
    }
    public boolean canApply(Long accountId, Long userId) {
        return permissionApi.hasAnyPermissions(userId, "zsjos:positioning-card:apply")
                && accountPermission.hasPermission(accountId, "positioning-apply", userId);
    }
    public PositioningApplicationDO application(Long accountId) { return applicationMapper.find(accountId); }
    public List<PositioningCardSubmissionDO> candidates(Long accountId) {
        var account = accountMapper.selectById(accountId);
        if (account == null || account.getCreateServiceRelationId() == null) return List.of();
        return submissionMapper.selectByService(account.getCreateServiceRelationId()).stream()
                .filter(row -> Objects.equals(account.getStudentPersonId(), row.getStudentPersonId()))
                .filter(PositioningAssignmentService::confirmed).toList();
    }
    static boolean confirmed(PositioningCardSubmissionDO row) {
        return PositioningEvidenceService.eligible(row);
    }
    @ZsjosPermission(bizType="media-account", bizId="#req.accountId", action="positioning-apply")
    @Transactional(rollbackFor=Exception.class)
    public void apply(PositioningApplyReqVO req, Long userId) {
        if (!canApply(req.accountId(), userId)) throw exception(POSITIONING_CARD_PERMISSION_DENIED);
        var account = accountMapper.selectByIdForUpdate(req.accountId(), TenantContextHolder.getRequiredTenantId());
        if (account == null) throw exception(POSITIONING_REFERENCE_INVALID);
        var replay = logMapper.find(req.idempotencyKey());
        if (replay != null) {
            if (!Objects.equals(replay.getAccountId(), req.accountId()) || !Objects.equals(replay.getSubmissionId(), req.submissionId())
                    || !Objects.equals(replay.getAppliedBy(), userId) || !Objects.equals(replay.getExpectedVersion(), req.version()))
                throw exception(POSITIONING_CARD_VERSION_CONFLICT);
            return;
        }
        var target = submissionMapper.selectById(req.submissionId());
        if (target == null || !confirmed(target) || account.getCreateServiceRelationId() == null
                || !Objects.equals(account.getStudentPersonId(), target.getStudentPersonId())
                || !Objects.equals(account.getCreateServiceRelationId(), target.getServiceRelationId())) throw exception(POSITIONING_REFERENCE_INVALID);
        cardPermission.check(target.getCardId(), "read", userId);
        var current = applicationMapper.find(req.accountId());
        int version = current == null ? 0 : current.getVersion();
        if (!Objects.equals(version, req.version())) throw exception(POSITIONING_CARD_VERSION_CONFLICT);
        Long previous = current == null ? null : current.getSubmissionId();
        if (current == null) {
            current = new PositioningApplicationDO().setAccountId(req.accountId()).setSubmissionId(req.submissionId()).setAppliedBy(userId).setVersion(1);
            applicationMapper.insert(current);
        } else {
            current.setSubmissionId(req.submissionId()).setAppliedBy(userId).setVersion(version + 1);
            applicationMapper.updateById(current);
        }
        logMapper.insert(new PositioningApplicationLogDO().setAccountId(req.accountId()).setPreviousSubmissionId(previous)
                .setSubmissionId(req.submissionId()).setAppliedBy(userId).setExpectedVersion(req.version()).setIdempotencyKey(req.idempotencyKey()));
    }
}
