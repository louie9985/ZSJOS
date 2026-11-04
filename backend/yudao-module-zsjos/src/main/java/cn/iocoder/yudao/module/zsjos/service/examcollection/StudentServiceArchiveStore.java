package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Field;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormValueDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoFormService;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoFields;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.*;

/** Internal persistence adapter. Entry services own object authorization before invoking it. */
@Service
public class StudentServiceArchiveStore {
    @Resource private ServiceRelationMapper relations;
    @Resource private StudentServiceArchiveRevisionMapper revisions;
    @Resource private StudentInfoFormMapper forms;
    @Resource private StudentInfoFormValueMapper answers;
    @Resource private StudentArchiveRevisionMapper legacyRevisions;
    @Resource private StudentInfoImageMapper legacyImages;
    @Resource private StudentInfoFormService collection;
    @Resource private ExamCollectionAttachmentMapper attachments;
    @Resource private StudentInfoFields fieldValidation;

    public record Attachment(String source, Long id, String fieldKey, String side, String name,
                             String contentType, Long size) {}
    public record Snapshot(Long relationId, Long personId, int revision, List<Field> fields,
                           List<StudentInfoFormValueDO> answers, List<Attachment> attachments) {}

    public Snapshot current(Long relationId) {
        return current(requireRelation(relations.selectById(relationId)));
    }

    private ServiceRelationDO requireRelation(ServiceRelationDO relation) {
        if (relation == null || !Objects.equals(relation.getTenantId(), TenantContextHolder.getRequiredTenantId()))
            throw exception(LINK_INVALID);
        return relation;
    }

    private Snapshot current(ServiceRelationDO relation) {
        var latest = revisions.latest(relation.getId());
        if (latest != null) {
            if (!Objects.equals(latest.getPersonId(), relation.getPersonId())) throw exception(VERSION_CONFLICT);
            return new Snapshot(relation.getId(), relation.getPersonId(), latest.getRevision(),
                    JsonUtils.parseArray(latest.getFieldsSnapshot(), Field.class),
                    JsonUtils.parseArray(latest.getAnswersJson(), StudentInfoFormValueDO.class),
                    JsonUtils.parseArray(latest.getAttachmentsJson(), Attachment.class));
        }
        return legacy(relation);
    }
    public Snapshot baseline(Long relationId) { return legacy(requireRelation(relations.selectById(relationId))); }
    private Snapshot legacy(ServiceRelationDO relation) {
        var empty = new Snapshot(relation.getId(), relation.getPersonId(), 0, List.of(), List.of(), List.of());
        if (relation.getRegistrationCaseId() == null) return empty;
        var form = forms.byCase(relation.getRegistrationCaseId());
        if (form == null || !"SUBMITTED".equals(form.getStatus())) return empty;
        if (!Objects.equals(form.getPersonId(), relation.getPersonId())
                || !Objects.equals(form.getOrderItemId(), relation.getOrderItemId())) throw exception(LINK_INVALID);
        var revision = legacyRevisions.latest(form.getId());
        var values = revision == null ? answers.byForm(form.getId())
                : JsonUtils.parseArray(revision.getAnswersJson(), StudentInfoFormValueDO.class);
        var imageRows = legacyImages.allByForm(form.getId());
        var ids = revision == null ? imageRows.stream().filter(i -> !Boolean.TRUE.equals(i.getArchiveOnly())).map(i -> i.getId()).toList()
                : JsonUtils.parseArray(revision.getImageIdsJson(), Long.class);
        var files = imageRows.stream().filter(i -> ids.contains(i.getId()))
                .map(i -> new Attachment("registration", i.getId(), i.getFieldKey(), i.getSide(),
                        i.getOriginalName(), i.getContentType(), i.getFileSize())).toList();
        return new Snapshot(relation.getId(), relation.getPersonId(), 0, collection.definitions(form), values, files);
    }

    /** Called inside the task-locked submit/resolve transaction; never edits original answers. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int apply(ExamCollectionTaskDO task, ExamCollectionSubmissionDO submission, Long relationId,
                     Long operatorUserId, String reason) {
        var tenant = TenantContextHolder.getRequiredTenantId();
        if (!Objects.equals(task.getTenantId(), tenant) || !Objects.equals(submission.getTenantId(), tenant)
                || !Objects.equals(task.getId(), submission.getTaskId()) || submission.getId() == null
                || submission.getSubmittedAt() == null || submission.getRequestHash() == null)
            throw exception(LINK_INVALID);
        var relation = requireRelation(relations.selectByIdForUpdate(relationId, tenant));
        cn.iocoder.yudao.module.zsjos.service.registration.TeachingServiceLifecycle.requireOpen(relation);
        String key = "exam-submit:" + submission.getId();
        var previous = revisions.byRequest(relationId, key);
        if (previous != null) {
            if (!Objects.equals(previous.getRequestHash(), submission.getRequestHash())) throw exception(VERSION_CONFLICT);
            return previous.getRevision();
        }
        var current = current(relation);
        var selected = JsonUtils.parseArray(task.getFieldsSnapshot(), Field.class);
        var incoming = JsonUtils.parseArray(submission.getAnswersJson(), StudentInfoFormValueDO.class);
        Set<String> keys = new LinkedHashSet<>(); selected.forEach(f -> keys.add(f.getKey()));
        if (incoming.stream().anyMatch(v -> !keys.contains(v.getFieldKey()))) throw exception(FIELD_INVALID, "非本次收集字段");
        if (incoming.stream().map(StudentInfoFormValueDO::getFieldKey).distinct().count() != incoming.size())
            throw exception(FIELD_INVALID, "重复档案字段");
        for (var field : selected) if (!"images".equals(field.getType()) && Boolean.TRUE.equals(field.getRequired())
                && incoming.stream().noneMatch(v -> field.getKey().equals(v.getFieldKey())))
            throw exception(FIELD_INVALID, field.getLabel());
        var fieldMap = new LinkedHashMap<String, Field>();
        current.fields().forEach(f -> fieldMap.put(f.getKey(), f));
        selected.forEach(f -> fieldMap.put(f.getKey(), f));
        var valueMap = new LinkedHashMap<String, StudentInfoFormValueDO>();
        current.answers().stream().filter(v -> !keys.contains(v.getFieldKey())).forEach(v -> valueMap.put(v.getFieldKey(), v));
        incoming.forEach(v -> valueMap.put(v.getFieldKey(), v));
        refreshIdentityDerived(current, fieldMap, valueMap);
        var fileIds = JsonUtils.parseArray(submission.getAttachmentIdsJson(), Long.class);
        if (fileIds == null || new HashSet<>(fileIds).size() != fileIds.size()) throw exception(FIELD_INVALID, "重复或缺失的材料引用");
        var files = new ArrayList<>(current.attachments().stream().filter(a -> !keys.contains(a.fieldKey())).toList());
        for (Long id : fileIds) {
            var file = attachments.selectById(id);
            if (file == null || !Objects.equals(file.getTenantId(), tenant)
                    || !Objects.equals(file.getTaskId(), task.getId()) || !Objects.equals(file.getSubmissionId(), submission.getId())
                    || selected.stream().noneMatch(f -> file.getFieldKey().equals(f.getKey()) && "images".equals(f.getType())))
                throw exception(LINK_INVALID);
            files.add(new Attachment("exam", file.getId(), file.getFieldKey(), file.getSide(),
                    file.getOriginalName(), file.getContentType(), file.getFileSize()));
        }
        for (var field : selected) if ("images".equals(field.getType())) {
            var selectedFiles = files.stream().filter(f -> field.getKey().equals(f.fieldKey())).toList();
            boolean identity = "identity_images".equals(field.getKey());
            int minimum = Boolean.TRUE.equals(field.getRequired()) ? (identity ? 2 : 1) : 0;
            int maximum = identity ? 2 : field.getMaxImages();
            if (selectedFiles.size() < minimum || selectedFiles.size() > maximum)
                throw exception(FIELD_INVALID, field.getLabel());
            if (identity && (selectedFiles.stream().anyMatch(f -> !("front".equals(f.side()) || "back".equals(f.side())))
                    || selectedFiles.stream().map(Attachment::side).distinct().count() != selectedFiles.size()))
                throw exception(FIELD_INVALID, field.getLabel());
        }
        var row = new StudentServiceArchiveRevisionDO();
        row.setTenantId(tenant); row.setServiceRelationId(relationId); row.setPersonId(relation.getPersonId());
        row.setRevision(current.revision() + 1); row.setTaskId(task.getId()); row.setSubmissionId(submission.getId());
        row.setOperatorUserId(operatorUserId); row.setSource("exam_submission"); row.setReason(Objects.requireNonNullElse(reason, ""));
        row.setIdempotencyKey(key); row.setRequestHash(submission.getRequestHash());
        row.setFieldsSnapshot(JsonUtils.toJsonString(fieldMap.values())); row.setAnswersJson(JsonUtils.toJsonString(valueMap.values()));
        row.setAttachmentsJson(JsonUtils.toJsonString(files)); revisions.insert(row);
        return row.getRevision();
    }

    private void refreshIdentityDerived(Snapshot current, Map<String, Field> definitions,
                                        Map<String, StudentInfoFormValueDO> values) {
        var identity = values.get("id_card");
        if (identity == null || identity.getValueText() == null) return;
        String card = identity.getValueText().trim().toUpperCase(Locale.ROOT);
        String oldCard = current.answers().stream().filter(v -> "id_card".equals(v.getFieldKey()))
                .map(StudentInfoFormValueDO::getValueText).filter(Objects::nonNull).findFirst().orElse("");
        if (card.equalsIgnoreCase(oldCard.trim())) return;
        var derived = new ArrayList<Field>();
        derived.add(definitions.get("id_card"));
        for (String key : List.of("age", "gender")) {
            var definition = definitions.get(key);
            if (definition == null || !Boolean.TRUE.equals(definition.getEnabled())) continue;
            // An unchanged dictionary choice retains its historical label even when the ID changes.
            var previous = values.get(key);
            if ("gender".equals(key) && card.length() == 18 && Character.isDigit(card.charAt(16))
                    && previous != null && Objects.equals(previous.getValueCode(), (card.charAt(16) - '0') % 2 == 1 ? "1" : "2")) continue;
            derived.add(definition);
        }
        if (derived.size() == 1) return;
        fieldValidation.validateValues(derived, Map.of("id_card", card)).stream()
                .filter(value -> !"id_card".equals(value.getFieldKey())).forEach(value -> values.put(value.getFieldKey(), value));
    }
}
