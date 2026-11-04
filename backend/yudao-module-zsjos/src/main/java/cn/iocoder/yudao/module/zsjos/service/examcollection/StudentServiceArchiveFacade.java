package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.hutool.core.util.DesensitizedUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentArchiveVO.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormValueDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.service.registration.TeachingServiceLifecycle;
import cn.iocoder.yudao.module.zsjos.service.examcollection.StudentServiceArchiveStore.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.*;

/** Adapter behind the existing archive's object/feature checks; does not grant new permissions. */
@Service
public class StudentServiceArchiveFacade {
    @Resource private StudentServiceArchiveStore store;
    @Resource private StudentServiceArchiveRevisionMapper revisions;
    @Resource private StudentServiceArchiveAttachmentMapper uploaded;
    @Resource private ExamCollectionAttachmentMapper examFiles;
    @Resource private StudentInfoImageMapper registrationFiles;
    @Resource private StudentInfoFormMapper forms;
    @Resource private StudentInfoFormConfigMapper configs;
    @Resource private StudentArchiveRevisionMapper legacyRevisions;
    @Resource private ExamCollectionSubmissionMapper submissions;
    @Resource private ExamCollectionTaskMapper tasks;
    @Resource private ServiceRelationMapper relations;
    @Resource private StudentInfoFields fields;
    @Resource private StudentInfoImageService cipher;
    @Resource private PermissionApi permissions;
    @Resource private AdminUserApi users;
    @Resource private FileApi files;

    public boolean hasRevision(Long id) { return revisions.latest(id) != null; }
    private boolean permitted(String code) { return permissions.hasAnyPermissions(getLoginUserId(), code); }
    private ServiceRelationDO relation(Long id, boolean lock) {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        var row = lock ? relations.selectByIdForUpdate(id, tenant) : relations.selectById(id);
        if (row == null || !Objects.equals(row.getTenantId(), tenant)) throw exception(LINK_INVALID);
        return row;
    }
    public Detail detail(Long id, boolean sensitive) {
        return project(relation(id, false), store.current(id), sensitive, true);
    }
    public Detail history(Long id, int revision, boolean sensitive) {
        var relation = relation(id, false);
        Snapshot snapshot;
        if (revision == 0) snapshot = store.baseline(id);
        else {
            var row = revisions.selectOne(new LambdaQueryWrapperX<StudentServiceArchiveRevisionDO>()
                    .eq(StudentServiceArchiveRevisionDO::getServiceRelationId, id).eq(StudentServiceArchiveRevisionDO::getRevision, revision));
            if (row == null || !Objects.equals(row.getPersonId(), relation.getPersonId())) throw exception(LINK_INVALID);
            snapshot = new Snapshot(id, relation.getPersonId(), row.getRevision(), JsonUtils.parseArray(row.getFieldsSnapshot(), StudentInfoVO.Field.class),
                    JsonUtils.parseArray(row.getAnswersJson(), StudentInfoFormValueDO.class), JsonUtils.parseArray(row.getAttachmentsJson(), Attachment.class));
        }
        return project(relation, snapshot, sensitive, false);
    }
    public Detail submission(Long id, Long submissionId, boolean sensitive) {
        var relation = relation(id, false); var submission = submissions.selectById(submissionId);
        if (submission == null || submission.getSubmittedAt() == null || !Objects.equals(submission.getTenantId(), relation.getTenantId())
                || !Objects.equals(submission.getServiceRelationId(), id)) throw exception(LINK_INVALID);
        var task = tasks.selectById(submission.getTaskId()); if (task == null) throw exception(LINK_INVALID);
        var selected = new ArrayList<Attachment>();
        for (Long fileId : JsonUtils.parseArray(submission.getAttachmentIdsJson(), Long.class)) {
            var row = examFiles.selectById(fileId);
            if (row == null || !Objects.equals(row.getSubmissionId(), submissionId) || !Objects.equals(row.getTenantId(), relation.getTenantId())) throw exception(LINK_INVALID);
            selected.add(new Attachment("exam", fileId, row.getFieldKey(), row.getSide(), row.getOriginalName(), row.getContentType(), row.getFileSize()));
        }
        var result = project(relation, new Snapshot(id, relation.getPersonId(), 0, JsonUtils.parseArray(task.getFieldsSnapshot(), StudentInfoVO.Field.class),
                JsonUtils.parseArray(submission.getAnswersJson(), StudentInfoFormValueDO.class), selected), sensitive, false);
        result.getInformation().setSubmittedAt(submission.getSubmittedAt()); return result;
    }
    private Detail project(ServiceRelationDO relation, Snapshot snapshot, boolean sensitive, boolean current) {
        if (sensitive && !permitted(ARCHIVE_SENSITIVE)) throw exception(ExamCollectionConstants.DENIED);
        var result = new Detail(); result.setPersonId(relation.getPersonId()); result.setServiceRelationId(relation.getId());
        result.setRegistrationCaseId(relation.getRegistrationCaseId()); result.setOrderItemId(relation.getOrderItemId());
        result.setRevision(snapshot.revision()); result.setCanMaintain(current && permitted(ARCHIVE_UPDATE) && !TeachingServiceLifecycle.closed(relation));
        var info = new StudentInfoVO.Detail(); info.setStatus("SUBMITTED"); info.setFields(snapshot.fields());
        info.setCanReadSensitive(permitted(ARCHIVE_SENSITIVE)); info.setCanExport(permitted(ARCHIVE_EXPORT));
        var display = ExamCollectionQueryService.display(snapshot.answers());
        if (!sensitive) for (var value : snapshot.answers()) if (Boolean.TRUE.equals(value.getSensitive())) {
            String text = display.getOrDefault(value.getFieldKey(), "");
            if (!text.isEmpty()) display.put(value.getFieldKey(), "mobile".equals(value.getFieldKey()) ? DesensitizedUtil.mobilePhone(text)
                    : "id_card".equals(value.getFieldKey()) ? DesensitizedUtil.idCardNum(text, 3, 4) : "已隐藏");
        }
        info.setValues(display); info.setImages(snapshot.attachments().stream().map(this::image).toList()); result.setInformation(info);
        var rows = revisions.selectList(new LambdaQueryWrapperX<StudentServiceArchiveRevisionDO>()
                .eq(StudentServiceArchiveRevisionDO::getServiceRelationId, relation.getId()).orderByDesc(StudentServiceArchiveRevisionDO::getRevision));
        var legacyForm = relation.getRegistrationCaseId() == null ? null : forms.byCase(relation.getRegistrationCaseId());
        if (legacyForm != null && Objects.equals(legacyForm.getPersonId(), relation.getPersonId()) && Objects.equals(legacyForm.getOrderItemId(), relation.getOrderItemId())) {
            result.setProductName(legacyForm.getProductName()); info.setCreatedAt(legacyForm.getCreateTime()); info.setSubmittedAt(legacyForm.getSubmittedAt());
            var config = configs.selectById(legacyForm.getConfigVersionId()); info.setConfigVersion(config == null ? null : config.getVersionNo());
        }
        var oldRows = legacyForm != null && Objects.equals(legacyForm.getPersonId(), relation.getPersonId()) && Objects.equals(legacyForm.getOrderItemId(), relation.getOrderItemId())
                ? legacyRevisions.history(legacyForm.getId()) : List.<cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentArchiveRevisionDO>of();
        var operatorIds = java.util.stream.Stream.concat(rows.stream().map(StudentServiceArchiveRevisionDO::getOperatorUserId), oldRows.stream().map(r -> r.getOperatorUserId()))
                .filter(Objects::nonNull).distinct().toList();
        var operators = operatorIds.isEmpty() ? Map.<Long, cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO>of() : users.getUserMap(operatorIds);
        var history = new ArrayList<History>();
        for (var row : rows) {
            var item = new History(); item.setSource("service"); item.setRevision(row.getRevision()); item.setOperatorUserId(row.getOperatorUserId());
            item.setOperatorName(row.getOperatorUserId() == null ? "学员提交" : operators.containsKey(row.getOperatorUserId()) ? operators.get(row.getOperatorUserId()).getNickname() : "已离职或不可用用户");
            item.setModifiedAt(row.getCreateTime()); item.setReason(row.getReason()); item.setTaskId(row.getTaskId()); item.setSubmissionId(row.getSubmissionId()); history.add(item);
        }
        if (!store.baseline(relation.getId()).fields().isEmpty()) {
            var baseline = new History(); baseline.setSource("service"); baseline.setRevision(0); baseline.setOperatorName("原报名档案"); baseline.setReason("考试收集前的报名档案"); history.add(baseline);
        }
        result.setHistory(history);
        for (var old : oldRows) {
            var item = new History(); item.setSource("registration"); item.setRevision(old.getRevision()); item.setOperatorUserId(old.getOperatorUserId());
            item.setOperatorName(old.getOperatorUserId() != null && operators.containsKey(old.getOperatorUserId()) ? operators.get(old.getOperatorUserId()).getNickname() : "历史维护");
            item.setModifiedAt(old.getCreateTime()); item.setReason(old.getReason()); history.add(item);
        }
        var originals = submissions.selectList(new LambdaQueryWrapperX<ExamCollectionSubmissionDO>()
                .eq(ExamCollectionSubmissionDO::getServiceRelationId, relation.getId()).isNotNull(ExamCollectionSubmissionDO::getSubmittedAt)
                .orderByDesc(ExamCollectionSubmissionDO::getSubmittedAt).orderByDesc(ExamCollectionSubmissionDO::getId));
        var taskIds = originals.stream().map(ExamCollectionSubmissionDO::getTaskId).distinct().toList();
        var taskNames = taskIds.isEmpty() ? Map.<Long, String>of() : tasks.selectByIds(taskIds).stream()
                .collect(java.util.stream.Collectors.toMap(ExamCollectionTaskDO::getId, ExamCollectionTaskDO::getTitle));
        result.setSubmissions(originals.stream().map(s -> {
            var item = new History(); item.setSource("submission"); item.setTaskId(s.getTaskId()); item.setTaskTitle(taskNames.get(s.getTaskId()));
            item.setSubmissionId(s.getId()); item.setOperatorName("学员填写"); item.setModifiedAt(s.getSubmittedAt()); return item;
        }).toList());
        rows.stream().filter(r -> r.getRevision() <= snapshot.revision() && "exam_submission".equals(r.getSource()))
                .findFirst().ifPresent(r -> {
                    var original = r.getSubmissionId() == null ? null : submissions.selectById(r.getSubmissionId());
                    if (original != null && Objects.equals(original.getTaskId(), r.getTaskId())) info.setSubmittedAt(original.getSubmittedAt());
                });
        return result;
    }
    public Edit edit(Long id) {
        var snapshot = store.current(id); var result = new Edit(); result.setRevision(snapshot.revision());
        var raw = new LinkedHashMap<String, Object>(); var options = new LinkedHashMap<String, List<StudentInfoVO.Option>>();
        for (var field : snapshot.fields()) {
            if ((Boolean.TRUE.equals(field.getSensitive()) || "images".equals(field.getType())) && !permitted(ARCHIVE_SENSITIVE)) continue;
            var value = snapshot.answers().stream().filter(a -> field.getKey().equals(a.getFieldKey())).findFirst().orElse(null);
            if ("images".equals(field.getType())) raw.put(field.getKey(), snapshot.attachments().stream().filter(a -> field.getKey().equals(a.fieldKey())).map(this::reference).toList());
            else if (value != null) raw.put(field.getKey(), "dict".equals(field.getType()) ? value.getValueCode()
                    : "area".equals(field.getType()) ? Arrays.stream(value.getAreaCodePath().split("/")).map(Integer::valueOf).toList() : value.getValueText());
            if ("dict".equals(field.getType())) options.put(field.getKey(), fields.options(field));
        }
        result.setValues(raw); result.setOptions(options); result.setImages(snapshot.attachments().stream().map(this::image).toList()); return result;
    }
    @Transactional(rollbackFor=Exception.class)
    public Integer save(Long id, Save request) {
        var relation = relation(id, true); TeachingServiceLifecycle.requireOpen(relation);
        String key = DigestUtil.sha256Hex("service-archive-edit:" + request.getIdempotencyKey());
        String hash = DigestUtil.sha256Hex(JsonUtils.toJsonString(request));
        var previous = revisions.byRequest(id, key);
        if (previous != null) {
            if (!hash.equals(previous.getRequestHash()) || !Objects.equals(previous.getOperatorUserId(), getLoginUserId())) throw exception(VERSION_CONFLICT);
            return previous.getRevision();
        }
        var current = store.current(id);
        if (current.revision() != request.getRevision()) throw exception(VERSION_CONFLICT);
        var changes = new LinkedHashMap<>(request.getChanges()); if (changes.isEmpty()) throw exception(FIELD_INVALID, "请至少修改一项档案内容");
        for (var keyName : changes.keySet()) {
            var definition = current.fields().stream().filter(f -> keyName.equals(f.getKey()) && Boolean.TRUE.equals(f.getEnabled())).findFirst()
                    .orElseThrow(() -> exception(FIELD_INVALID, keyName));
            if ((Boolean.TRUE.equals(definition.getSensitive()) || "images".equals(definition.getType())) && !permitted(ARCHIVE_SENSITIVE)) throw exception(ExamCollectionConstants.DENIED);
            if (Set.of("age", "gender").contains(keyName) && !changes.containsKey("id_card")) throw exception(FIELD_INVALID, definition.getLabel());
        }
        if (changes.containsKey("id_card")) for (String derived : List.of("age", "gender"))
            if (current.fields().stream().anyMatch(f -> derived.equals(f.getKey()) && Boolean.TRUE.equals(f.getEnabled()))) changes.put(derived, null);
        var textFields = current.fields().stream().filter(f -> changes.containsKey(f.getKey()) && !"images".equals(f.getType())).toList();
        var textChanges = new LinkedHashMap<String, Object>(); textFields.forEach(f -> textChanges.put(f.getKey(), changes.get(f.getKey())));
        var values = new LinkedHashMap<String, StudentInfoFormValueDO>(); current.answers().forEach(a -> values.put(a.getFieldKey(), a));
        textFields.forEach(f -> values.remove(f.getKey())); fields.validateValues(textFields, textChanges).forEach(a -> values.put(a.getFieldKey(), a));
        var selected = new ArrayList<>(current.attachments());
        for (var field : current.fields()) if ("images".equals(field.getType()) && changes.containsKey(field.getKey())) {
            if (!(changes.get(field.getKey()) instanceof List<?> refs)) throw exception(FIELD_INVALID, field.getLabel());
            var replacements = refs.stream().map(ref -> resolve(id, ref)).toList();
            if (replacements.stream().anyMatch(a -> !field.getKey().equals(a.fieldKey())) || replacements.stream().map(this::reference).distinct().count() != replacements.size())
                throw exception(FIELD_INVALID, field.getLabel());
            boolean identity = "identity_images".equals(field.getKey());
            if (replacements.size() > (identity ? 2 : field.getMaxImages()) || Boolean.TRUE.equals(field.getRequired()) && replacements.size() < (identity ? 2 : 1)) throw exception(FIELD_INVALID, field.getLabel());
            if (identity && (replacements.stream().anyMatch(a -> !("front".equals(a.side()) || "back".equals(a.side())))
                    || replacements.stream().map(Attachment::side).distinct().count() != replacements.size())) throw exception(FIELD_INVALID, field.getLabel());
            selected.removeIf(a -> field.getKey().equals(a.fieldKey())); selected.addAll(replacements);
        }
        var row = new StudentServiceArchiveRevisionDO(); row.setTenantId(relation.getTenantId()); row.setServiceRelationId(id); row.setPersonId(relation.getPersonId());
        row.setRevision(current.revision() + 1); row.setSource("staff_edit"); row.setOperatorUserId(getLoginUserId()); row.setReason(Objects.requireNonNullElse(request.getReason(), ""));
        row.setIdempotencyKey(key); row.setRequestHash(hash); row.setFieldsSnapshot(JsonUtils.toJsonString(current.fields()));
        row.setWrittenKeysJson(JsonUtils.toJsonString(changes.keySet()));
        row.setAnswersJson(JsonUtils.toJsonString(values.values())); row.setAttachmentsJson(JsonUtils.toJsonString(selected)); revisions.insert(row); return row.getRevision();
    }
    @Transactional(rollbackFor=Exception.class)
    public StudentInfoVO.Image upload(Long id, String fieldKey, String side, MultipartFile upload) {
        var relation = relation(id, true); TeachingServiceLifecycle.requireOpen(relation);
        if (!permitted(ARCHIVE_SENSITIVE)) throw exception(ExamCollectionConstants.DENIED);
        var field = store.current(id).fields().stream().filter(f -> fieldKey.equals(f.getKey()) && "images".equals(f.getType())).findFirst().orElseThrow(() -> exception(FIELD_INVALID, "材料字段"));
        boolean identity = "identity_images".equals(fieldKey);
        if (identity ? !("front".equals(side) || "back".equals(side)) : side != null && !side.isEmpty()) throw exception(FIELD_INVALID, field.getLabel());
        if (upload.isEmpty()) throw exception(ATTACHMENT_EMPTY);
        if (upload.getSize() > field.getMaxSizeMb() * 1024L * 1024) throw exception(ATTACHMENT_TOO_LARGE, field.getMaxSizeMb());
        try {
            byte[] content = upload.getBytes(); String type = StudentInfoAttachmentValidator.validate(content, upload.getOriginalFilename(), field.getAllowedExtensions());
            var stored = files.createFileInfo(cipher.encryptPrivateMaterial(content), UUID.randomUUID() + ".bin", "student-service-archive-private/" + id, "application/octet-stream");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) { if (status != STATUS_COMMITTED) try { files.deleteFileIfExists(stored.getId()); } catch (Exception ignored) { } }
            });
            var row = new StudentServiceArchiveAttachmentDO(); row.setTenantId(relation.getTenantId()); row.setServiceRelationId(id);
            row.setFieldKey(fieldKey); row.setSide(identity ? side : ""); row.setOriginalName(ExamCollectionAttachmentService.safeName(upload.getOriginalFilename()));
            row.setInfraFileId(stored.getId()); row.setFileSize((long) content.length); row.setContentType(type); uploaded.insert(row);
            return image(new Attachment("service", row.getId(), fieldKey, row.getSide(), row.getOriginalName(), type, row.getFileSize()));
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException error) { throw error; }
        catch (Exception error) { throw exception(FIELD_INVALID, "材料上传失败，请重试"); }
    }
    public byte[] read(Long relationId, Long id, String source) {
        resolve(relationId, source + ":" + id);
        Long fileId = switch (source) {
            case "registration" -> registrationFiles.selectById(id).getInfraFileId();
            case "exam" -> examFiles.selectById(id).getInfraFileId();
            case "service" -> uploaded.selectById(id).getInfraFileId();
            default -> throw exception(LINK_INVALID);
        };
        try { return cipher.decryptPrivateMaterial(files.getFileContent(fileId)); }
        catch (Exception error) { throw exception(FIELD_INVALID, "材料读取失败，请重试"); }
    }
    private Attachment resolve(Long relationId, Object raw) {
        String value = raw instanceof Number ? "registration:" + raw : String.valueOf(raw);
        if (!value.matches("(registration|exam|service):[0-9]+")) throw exception(FIELD_INVALID, "材料引用");
        var parts = value.split(":"); Long id;
        try { id = Long.valueOf(parts[1]); } catch (NumberFormatException error) { throw exception(LINK_INVALID); }
        var relation = relation(relationId, false);
        if ("service".equals(parts[0])) {
            var row = uploaded.selectById(id);
            if (row == null || !Objects.equals(row.getTenantId(), relation.getTenantId()) || !Objects.equals(row.getServiceRelationId(), relationId)) throw exception(LINK_INVALID);
            return new Attachment("service", id, row.getFieldKey(), row.getSide(), row.getOriginalName(), row.getContentType(), row.getFileSize());
        }
        if ("registration".equals(parts[0])) {
            var row = registrationFiles.selectById(id);
            var form = row == null ? null : forms.selectById(row.getFormId());
            if (row == null || form == null || !Objects.equals(row.getTenantId(), relation.getTenantId()) || !Objects.equals(row.getFormId(), form.getId())
                    || !Objects.equals(form.getTenantId(), relation.getTenantId()) || !Objects.equals(form.getPersonId(), relation.getPersonId())
                    || !(relation.getRegistrationCaseId() != null && Objects.equals(form.getRegistrationCaseId(), relation.getRegistrationCaseId())
                    || relation.getOrderItemId() != null && Objects.equals(form.getOrderItemId(), relation.getOrderItemId()))) throw exception(LINK_INVALID);
            return new Attachment("registration", id, row.getFieldKey(), row.getSide(), row.getOriginalName(), row.getContentType(), row.getFileSize());
        }
        var row = examFiles.selectById(id);
        boolean submittedToService = row != null && submissions.selectCount(new LambdaQueryWrapperX<ExamCollectionSubmissionDO>()
                .eq(ExamCollectionSubmissionDO::getId, row.getSubmissionId()).eq(ExamCollectionSubmissionDO::getTaskId, row.getTaskId())
                .eq(ExamCollectionSubmissionDO::getServiceRelationId, relationId)
                .isNotNull(ExamCollectionSubmissionDO::getSubmittedAt)
                .apply("JSON_CONTAINS(attachment_ids_json,{0})", String.valueOf(id))) > 0;
        if (row == null || !Objects.equals(row.getTenantId(), relation.getTenantId()) || !submittedToService && revisions.selectCount(new LambdaQueryWrapperX<StudentServiceArchiveRevisionDO>()
                .eq(StudentServiceArchiveRevisionDO::getServiceRelationId, relationId)
                .apply("JSON_CONTAINS(attachments_json,{0})", JsonUtils.toJsonString(Map.of("source", "exam", "id", id)))) == 0) throw exception(LINK_INVALID);
        return new Attachment("exam", id, row.getFieldKey(), row.getSide(), row.getOriginalName(), row.getContentType(), row.getFileSize());
    }
    private String reference(Attachment attachment) { return attachment.source() + ":" + attachment.id(); }
    private StudentInfoVO.Image image(Attachment attachment) {
        var result = new StudentInfoVO.Image(); result.setId(attachment.id()); result.setSource(attachment.source()); result.setFieldKey(attachment.fieldKey());
        result.setSide(attachment.side()); result.setName(attachment.name() == null || attachment.name().isBlank() ? attachment.fieldKey() + "-" + attachment.id() + StudentInfoAttachmentValidator.extension(attachment.contentType()) : attachment.name());
        result.setContentType(attachment.contentType()); result.setSize(attachment.size()); return result;
    }
}
