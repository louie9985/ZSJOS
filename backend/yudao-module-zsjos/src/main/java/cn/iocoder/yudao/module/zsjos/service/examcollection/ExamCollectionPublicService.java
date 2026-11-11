package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.service.TenantFrameworkService;
import cn.iocoder.yudao.module.zsjos.controller.pub.examcollection.vo.ExamCollectionPublicVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Field;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.*;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoFields;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionConstants.*;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.FIELD_INVALID;

@Service
public class ExamCollectionPublicService {
    @Resource private ExamCollectionTaskMapper tasks;
    @Resource private ExamCollectionSubmissionMapper submissions;
    @Resource private ExamCollectionAttachmentMapper attachments;
    @Resource private ExamCollectionTokens tokens;
    @Resource private TenantFrameworkService tenants;
    @Resource private StudentInfoFields fields;
    @Resource private ExamCollectionMatchingService matching;

    public ExamCollectionPublicVO.Runtime detail(String token) {
        return withTask(token, false, task -> {
            var result = new ExamCollectionPublicVO.Runtime(); result.setTitle(task.getTitle());
            result.setContent(ExamCollectionContent.clean(task.getContent()));
            result.setDescription(task.getDescription()); result.setStatus(task.getStatus());
            if (OPEN.equals(task.getStatus())) result.setFields(definitions(task));
            return result;
        });
    }
    @Transactional(rollbackFor=Exception.class)
    public ExamCollectionPublicVO.Session start(String token) {
        return withTask(token, true, task -> {
            requireOpen(task);
            var row = new ExamCollectionSubmissionDO(); row.setTenantId(task.getTenantId()); row.setTaskId(task.getId());
            String sessionToken = tokens.create(); row.setSessionTokenHash(tokens.hash(sessionToken));
            row.setStatus(DRAFT); row.setExpiresAt(now().plusDays(7)); row.setRevision(0); submissions.insert(row);
            var result = new ExamCollectionPublicVO.Session(); result.setSessionToken(sessionToken); result.setExpiresAt(row.getExpiresAt());
            return result;
        });
    }
    @Transactional(rollbackFor=Exception.class)
    public void submit(String token, String sessionToken, ExamCollectionPublicVO.Submit request) {
        withSession(token, sessionToken, false, (task, row) -> {
            var input = ExamCollectionFields.liveValues(request.getValues());
            String fingerprint = DigestUtil.sha256Hex(JsonUtils.toJsonString(new TreeMap<>(input)));
            String legacyFingerprint = DigestUtil.sha256Hex(JsonUtils.toJsonString(new TreeMap<>(request.getValues())));
            if (!DRAFT.equals(row.getStatus())) {
                if (Objects.equals(row.getIdempotencyKey(), request.getIdempotencyKey())
                        && (Objects.equals(row.getRequestHash(), fingerprint) || Objects.equals(row.getRequestHash(), legacyFingerprint))) return null;
                throw exception(CONFLICT);
            }
            requireOpen(task);
            var previous = submissions.byRequest(task.getId(), request.getIdempotencyKey());
            if (previous != null) throw exception(CONFLICT);
            var definitions = definitions(task);
            var values = fields.validateCollectionValues(definitions, input);
            var fileIds = validateAttachments(task, row, definitions, input);
            String identity = values.stream().filter(v -> "id_card".equals(v.getFieldKey())).findFirst()
                    .orElseThrow(() -> exception(FIELD_INVALID, "身份证号码")).getValueText();
            long sequence = Math.addExact(task.getSequenceNo(), 1);
            task.setSequenceNo(sequence); tasks.updateById(task);
            row.setSequenceNo(sequence); row.setIdempotencyKey(request.getIdempotencyKey()); row.setRequestHash(fingerprint);
            row.setAnswersJson(JsonUtils.toJsonString(values)); row.setAttachmentIdsJson(JsonUtils.toJsonString(fileIds));
            row.setIdentityHash(tokens.identityHash(identity)); row.setSubmittedAt(now()); row.setStatus(SUBMITTED);
            row.setRevision(row.getRevision() + 1); submissions.updateById(row);
            matching.match(task, row); return null;
        });
    }
    List<Long> validateAttachments(ExamCollectionTaskDO task, ExamCollectionSubmissionDO row, List<Field> definitions,
                                   Map<String, Object> values) {
        var result = new ArrayList<Long>();
        for (var field : definitions) if ("images".equals(field.getType())) {
            Object raw = values.get(field.getKey());
            if (!(raw instanceof List<?> ids)) throw exception(FIELD_INVALID, field.getLabel());
            boolean identity = "identity_images".equals(field.getKey());
            if (ids.size() < (identity ? 2 : 1) || ids.size() > (identity ? 2 : field.getMaxImages()))
                throw exception(FIELD_INVALID, field.getLabel());
            Set<String> sides = new HashSet<>();
            for (Object id : ids) {
                if (!(id instanceof Number number) || !id.toString().matches("[0-9]+")) throw exception(FIELD_INVALID, field.getLabel());
                var file = attachments.selectById(number.longValue());
                if (file == null || !Objects.equals(file.getTenantId(), task.getTenantId())
                        || !Objects.equals(file.getTaskId(), task.getId()) || !Objects.equals(file.getSubmissionId(), row.getId())
                        || !field.getKey().equals(file.getFieldKey()) || result.contains(file.getId())) throw exception(SESSION_INVALID);
                if (identity && (!("front".equals(file.getSide()) || "back".equals(file.getSide())) || !sides.add(file.getSide())))
                    throw exception(FIELD_INVALID, field.getLabel());
                result.add(file.getId());
            }
        }
        return result;
    }
    public List<Field> definitions(ExamCollectionTaskDO task) { return ExamCollectionFields.liveFields(JsonUtils.parseArray(task.getFieldsSnapshot(), Field.class)); }
    static LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Shanghai")); }
    static void requireOpen(ExamCollectionTaskDO task) { if (!OPEN.equals(task.getStatus())) throw exception(CLOSED); }

    /** Internal callback used by the attachment service within its own transaction. */
    public <T> T withSession(String token, String sessionToken, boolean requireDraft,
                            BiFunction<ExamCollectionTaskDO, ExamCollectionSubmissionDO, T> action) {
        return withTask(token, true, task -> {
            var session = submissions.bySession(tokens.hash(sessionToken));
            if (session == null || !Objects.equals(session.getTaskId(), task.getId())
                    || !Objects.equals(session.getTenantId(), task.getTenantId())) throw exception(SESSION_INVALID);
            var row = submissions.lock(session.getId());
            if (row == null || !Objects.equals(row.getSessionTokenHash(), tokens.hash(sessionToken))
                    || row.getExpiresAt().isBefore(now())) throw exception(SESSION_INVALID);
            if (requireDraft) { requireOpen(task); if (!DRAFT.equals(row.getStatus())) throw exception(CONFLICT); }
            return action.apply(task, row);
        });
    }
    private <T> T withTask(String token, boolean lock, Function<ExamCollectionTaskDO, T> action) {
        String hash = tokens.hash(token);
        Boolean oldIgnore = TenantContextHolder.isIgnore();
        ExamCollectionTaskDO found;
        try {
            TenantContextHolder.setIgnore(true); found = tasks.byToken(hash);
            if (found == null) throw exception(NOT_FOUND);
            tenants.validTenant(found.getTenantId());
        } finally { TenantContextHolder.setIgnore(oldIgnore); }
        return inTenant(found.getTenantId(), () -> {
            var task = lock ? tasks.lock(found.getId()) : tasks.selectById(found.getId());
            if (task == null || !Objects.equals(task.getTokenHash(), hash)
                    || !Objects.equals(task.getTenantId(), found.getTenantId())) throw exception(NOT_FOUND);
            return action.apply(task);
        });
    }
    private <T> T inTenant(Long tenant, Supplier<T> action) {
        Long previous = TenantContextHolder.getTenantId(); Boolean ignored = TenantContextHolder.isIgnore();
        try { TenantContextHolder.setTenantId(tenant); TenantContextHolder.setIgnore(false); return action.get(); }
        finally { TenantContextHolder.setTenantId(previous); TenantContextHolder.setIgnore(ignored); }
    }
}
