package cn.iocoder.yudao.module.zsjos.service.positioning;

import static cn.iocoder.yudao.module.zsjos.enums.MediaNotificationScenes.*;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardSubmissionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardSubmissionMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.media.MediaWorkflowEventService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;

@Service
public class PositioningEvidenceService {
    public record Evidence(Long id, String name, String type, Long size, Long uploadedBy, LocalDateTime uploadedAt) {}
    @Resource private cn.iocoder.yudao.module.zsjos.service.media.MediaCollaborationNotifyPublisher collaborationNotify;
    @Resource private cn.iocoder.yudao.module.zsjos.service.delivery.DeliveryPositioningSyncService deliverySync;
    @Resource private FileApi fileApi;
    @Resource private PositioningCardMapper cardMapper;
    @Resource private PositioningCardSubmissionMapper submissionMapper;
    @Resource private MediaWorkflowEventService workflowEventService;

    public static List<Evidence> evidence(PositioningCardSubmissionDO row) {
        return row.getEvidenceJson() == null ? List.of() : JsonUtils.parseArray(row.getEvidenceJson(), Evidence.class);
    }

    public static boolean eligible(PositioningCardSubmissionDO row) {
        return List.of("confirmed", "superseded", "student_agreed").contains(row.getStatus())
                && (!Boolean.TRUE.equals(row.getEvidenceRequired()) || !evidence(row).isEmpty());
    }

    private String directory(Long cardId, Long submissionId, Long userId) {
        // Infra rejects a trailing slash because it creates an empty path segment.
        return "zsjos/positioning-evidence/" + TenantContextHolder.getRequiredTenantId() + "/" + cardId + "/" + submissionId + "/" + userId;
    }

    private PositioningCardSubmissionDO requireSubmission(Long cardId, Long submissionId) {
        var row = submissionMapper.selectById(submissionId);
        if (row == null || !Objects.equals(row.getCardId(), cardId)) throw exception(POSITIONING_REFERENCE_INVALID);
        return row;
    }

    private void requireWritable(Long cardId, PositioningCardSubmissionDO row, Long userId) {
        var card = cardMapper.selectById(cardId);
        if (card == null || !Objects.equals(card.getOperatorUserId(), userId)) throw exception(POSITIONING_CARD_PERMISSION_DENIED);
        if (eligible(row)) return;
        var latest = submissionMapper.selectLatestByCard(cardId);
        if (!"student_evidence_pending".equals(row.getStatus()) || !"student_evidence_pending".equals(card.getStatus())
                || latest == null || !Objects.equals(latest.getId(), row.getId())) throw exception(POSITIONING_CARD_STATE_INVALID);
    }

    @ZsjosPermission(bizType="positioning-card", bizId="#cardId", action="evidence")
    public PositioningCardService.CardFile upload(Long cardId, Long submissionId, byte[] bytes, String name, String declaredMime, Long userId) {
        requireWritable(cardId, requireSubmission(cardId, submissionId), userId);
        // 确认凭证与定位卡附件同源：聊天记录截图、语音、视频等，走同一白名单。
        String detected = PositioningAttachmentTypes.detectAllowed(name, bytes);
        if (bytes.length == 0 || bytes.length > 20 * 1024 * 1024 || detected == null) throw exception(DIRECTOR_FORM_VALUE_INVALID);
        var file = fileApi.createFileInfo(bytes, PositioningAttachmentTypes.storageName(name), directory(cardId, submissionId, userId), detected);
        return new PositioningCardService.CardFile(file.getId(), file.getName(), file.getType(), file.getSize(), null);
    }

    @Transactional(rollbackFor=Exception.class)
    @ZsjosPermission(bizType="positioning-card", bizId="#cardId", action="evidence")
    public void submit(Long cardId, Long submissionId, Integer version, List<Long> ids, Long userId) {
        var card = cardMapper.selectByIdForUpdate(cardId, TenantContextHolder.getRequiredTenantId());
        if (card == null) throw exception(POSITIONING_CARD_NOT_EXISTS);
        var row = submissionMapper.selectByIdForUpdate(submissionId, TenantContextHolder.getRequiredTenantId());
        if (row == null || !Objects.equals(row.getCardId(), cardId)) throw exception(POSITIONING_REFERENCE_INVALID);
        requireWritable(cardId, row, userId);
        if (ids == null || ids.isEmpty() || ids.size() > 20 || new HashSet<>(ids).size() != ids.size()) throw exception(DIRECTOR_FORM_VALUE_INVALID);
        List<Evidence> saved = new ArrayList<>(evidence(row));
        Set<Long> existing = new HashSet<>(); saved.forEach(file -> existing.add(file.id()));
        // A lost response can be retried without duplicating the receipt or advancing the version twice.
        if (existing.containsAll(ids)) return;
        if (!Objects.equals(row.getVersion(), version)) throw exception(POSITIONING_CARD_VERSION_CONFLICT);
        LocalDateTime now = LocalDateTime.now();
        for (Long id : ids) {
            if (existing.contains(id)) continue;
            var file = fileApi.getFileInfo(id);
            if (file == null || file.getPath() == null || !file.getPath().startsWith(directory(cardId, submissionId, userId))) throw exception(POSITIONING_REFERENCE_INVALID);
            saved.add(new Evidence(id, file.getName(), file.getType(), file.getSize(), userId, now));
        }
        boolean pending = "student_evidence_pending".equals(row.getStatus());
        if (submissionMapper.update(null, new LambdaUpdateWrapper<PositioningCardSubmissionDO>()
                .eq(PositioningCardSubmissionDO::getId, submissionId).eq(PositioningCardSubmissionDO::getVersion, version)
                .set(PositioningCardSubmissionDO::getEvidenceJson, JsonUtils.toJsonString(saved))
                .set(PositioningCardSubmissionDO::getStatus, pending ? "confirmed" : row.getStatus())
                .set(PositioningCardSubmissionDO::getVersion, version + 1)) != 1) throw exception(POSITIONING_CARD_VERSION_CONFLICT);
        if (pending) {
            if (cardMapper.transition(cardId, card.getVersion(), "student_evidence_pending", "confirmed") != 1) throw exception(POSITIONING_CARD_VERSION_CONFLICT);
            workflowEventService.transition("positioning-card", cardId, userId, "student_evidence_pending", "confirmed", null, "positioning-evidence:" + submissionId);
            deliverySync.syncService(row.getStudentPersonId(), row.getServiceRelationId());
            collaborationNotify.card(MEDIA_POSITIONING_EFFECTIVE, card, userId, "positioning-effective:" + submissionId);
        }
    }

    @ZsjosPermission(bizType="positioning-card", bizId="#cardId", action="read")
    public PositioningCardService.CardFile attachment(Long cardId, Long submissionId, Long fileId) {
        var file = evidence(requireSubmission(cardId, submissionId)).stream().filter(item -> Objects.equals(item.id(), fileId)).findFirst()
                .orElseThrow(() -> exception(POSITIONING_REFERENCE_INVALID));
        if (fileApi.getFileInfo(fileId) == null) throw exception(POSITIONING_REFERENCE_INVALID);
        return new PositioningCardService.CardFile(file.id(), file.name(), file.type(), file.size(), fileApi.presignGetUrl(fileId, 300));
    }
}
