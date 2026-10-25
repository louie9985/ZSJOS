package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.hutool.core.util.DesensitizedUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentArchiveVO.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.*;

@Service
public class StudentArchiveService {
    @Resource private ServiceRelationMapper relations;
    @Resource private StudentInfoFormMapper forms;
    @Resource private StudentInfoFormConfigMapper configs;
    @Resource private StudentInfoFormValueMapper answers;
    @Resource private StudentArchiveRevisionMapper revisions;
    @Resource private StudentInfoImageMapper imageMapper;
    @Resource private StudentInfoFormService collection;
    @Resource private StudentInfoFields fields;
    @Resource private StudentInfoImageService images;
    @Resource private PermissionApi permissions;
    @Resource private cn.iocoder.yudao.module.system.api.user.AdminUserApi users;

    private StudentInfoFormDO form(Long relationId) {
        var relation = relations.selectById(relationId);
        if (relation == null) throw exception(LINK_INVALID);
        if (relation.getRegistrationCaseId() == null) return null;
        var form = forms.byCase(relation.getRegistrationCaseId());
        if (form != null && (!Objects.equals(form.getPersonId(), relation.getPersonId())
                || !Objects.equals(form.getOrderItemId(), relation.getOrderItemId()))) throw exception(LINK_INVALID);
        return form;
    }
    private StudentInfoFormDO submitted(Long relationId) {
        var form = form(relationId);
        if (form == null || !"SUBMITTED".equals(form.getStatus())) throw exception(FIELD_INVALID, "学员尚未提交档案");
        return form;
    }
    private List<StudentInfoFormValueDO> content(StudentInfoFormDO form, StudentArchiveRevisionDO revision) {
        return revision == null ? answers.byForm(form.getId())
                : JsonUtils.parseArray(revision.getAnswersJson(), StudentInfoFormValueDO.class);
    }
    private List<Long> imageIds(StudentInfoFormDO form, StudentArchiveRevisionDO revision) {
        return revision == null ? images.list(form.getId()).stream().map(StudentInfoVO.Image::getId).toList()
                : JsonUtils.parseArray(revision.getImageIdsJson(), Long.class);
    }
    private boolean permitted(String permission) { return permissions.hasAnyPermissions(getLoginUserId(), permission); }

    @ZsjosPermission(bizType="student-archive", bizId="#relationId", action="read")
    public Detail detail(Long relationId, boolean sensitive) {
        if (sensitive && !permitted(ARCHIVE_SENSITIVE)) throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED);
        var relation = relations.selectById(relationId);
        var form = form(relationId);
        var result = new Detail();
        result.setPersonId(relation.getPersonId()); result.setServiceRelationId(relationId);
        result.setRegistrationCaseId(relation.getRegistrationCaseId()); result.setOrderItemId(relation.getOrderItemId());
        result.setCanMaintain(permitted(ARCHIVE_UPDATE) && !cn.iocoder.yudao.module.zsjos.service.registration.TeachingServiceLifecycle.closed(relation) && form != null && "SUBMITTED".equals(form.getStatus()));
        var information = new StudentInfoVO.Detail(); result.setInformation(information); result.setRevision(0);
        information.setStatus(form == null ? "NONE" : form.getStatus());
        information.setCanReadSensitive(permitted(ARCHIVE_SENSITIVE)); information.setCanExport(permitted(ARCHIVE_EXPORT));
        if (form == null) return result;
        var config = configs.selectById(form.getConfigVersionId());
        information.setConfigVersion(config == null ? null : config.getVersionNo());
        result.setProductName(form.getProductName()); information.setId(form.getId());
        information.setCreatedAt(form.getCreateTime()); information.setSubmittedAt(form.getSubmittedAt());
        information.setFields(collection.definitions(form));
        if (!"SUBMITTED".equals(form.getStatus())) return result;
        var latest = revisions.latest(form.getId()); result.setRevision(latest == null ? 0 : latest.getRevision());
        Map<String, String> display = new LinkedHashMap<>();
        for (var value : content(form, latest)) {
            String text = value.getValueText() != null ? value.getValueText()
                    : value.getValueLabelSnapshot() != null ? value.getValueLabelSnapshot() : value.getAreaLabelSnapshot();
            if (text == null) text = "";
            if (Boolean.TRUE.equals(value.getSensitive()) && !sensitive && !text.isEmpty())
                text = "mobile".equals(value.getFieldKey()) ? DesensitizedUtil.mobilePhone(text) : (value.getFieldKey().endsWith("shipping_address") ? "已隐藏" : DesensitizedUtil.idCardNum(text,3,4));
            display.put(value.getFieldKey(), text);
        }
        information.setValues(display);
        information.setImages(images.archiveList(form.getId(), imageIds(form, latest)));
        result.setHistory(revisions.history(form.getId()).stream().map(row -> {
            var history = new History(); history.setRevision(row.getRevision()); history.setOperatorUserId(row.getOperatorUserId());
            history.setModifiedAt(row.getCreateTime()); history.setReason(row.getReason()); return history;
        }).peek(history -> {
            var operator = users.getUser(history.getOperatorUserId());
            history.setOperatorName(operator == null ? "已离职或不可用用户" : operator.getNickname());
        }).toList());
        return result;
    }

    @ZsjosPermission(bizType="student-archive", bizId="#relationId", action="update")
    public Edit edit(Long relationId) {
        var form = submitted(relationId); var latest = revisions.latest(form.getId());
        var result = new Edit(); result.setRevision(latest == null ? 0 : latest.getRevision());
        Map<String,Object> raw = new LinkedHashMap<>();
        for (var answer : content(form, latest)) {
            var definition = collection.definitions(form).stream().filter(f -> f.getKey().equals(answer.getFieldKey())).findFirst().orElseThrow();
            if (Boolean.TRUE.equals(answer.getSensitive()) && !permitted(ARCHIVE_SENSITIVE)) continue;
            raw.put(answer.getFieldKey(), "dict".equals(definition.getType()) ? answer.getValueCode()
                    : "area".equals(definition.getType()) ? Arrays.stream(answer.getAreaCodePath().split("/")).map(Integer::valueOf).toList() : answer.getValueText());
        }
        var selected = images.archiveList(form.getId(), imageIds(form, latest)); result.setImages(selected);
        for (var field : collection.definitions(form)) if ("images".equals(field.getType()))
            raw.put(field.getKey(), selected.stream().filter(i -> field.getKey().equals(i.getFieldKey())).map(StudentInfoVO.Image::getId).toList());
        result.setValues(raw);
        Map<String,List<StudentInfoVO.Option>> options = new LinkedHashMap<>();
        for (var field : collection.definitions(form)) if ("dict".equals(field.getType())) options.put(field.getKey(), fields.options(field));
        result.setOptions(options); return result;
    }

    @ZsjosPermission(bizType="student-archive", bizId="#relationId", action="update")
    @Transactional(rollbackFor=Exception.class)
    public Integer save(Long relationId, Save request) {
        cn.iocoder.yudao.module.zsjos.service.registration.TeachingServiceLifecycle.requireOpen(relations.selectByIdForUpdate(relationId,cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId()));
        var form = forms.lock(submitted(relationId).getId());
        String fingerprint = cn.hutool.crypto.digest.DigestUtil.sha256Hex(JsonUtils.toJsonString(request));
        var previous = revisions.byKey(request.getIdempotencyKey());
        if (previous != null) {
            if (!Objects.equals(previous.getFormId(), form.getId()) || !Objects.equals(previous.getOperatorUserId(), getLoginUserId())
                    || !fingerprint.equals(previous.getRequestHash())) throw exception(VERSION_CONFLICT);
            return previous.getRevision();
        }
        var latest = revisions.latest(form.getId()); int current = latest == null ? 0 : latest.getRevision();
        // Unique form revision plus form lock prevents lost updates. Original answers are never updated.
        if (!Objects.equals(request.getRevision(), current)) throw exception(VERSION_CONFLICT);
        var definitions = collection.definitions(form);
        Map<String,StudentInfoFormValueDO> merged = new LinkedHashMap<>();
        content(form, latest).forEach(v -> merged.put(v.getFieldKey(), v));
        var changes = new LinkedHashMap<>(request.getChanges());
        if (changes.isEmpty()) throw exception(FIELD_INVALID, "请至少修改一项档案内容");
        for (String key : changes.keySet()) {
            var field = definitions.stream().filter(f -> key.equals(f.getKey()) && f.getEnabled()).findFirst().orElseThrow(() -> exception(FIELD_INVALID, key));
            if ((Boolean.TRUE.equals(field.getSensitive()) || "images".equals(field.getType())) && !permitted(ARCHIVE_SENSITIVE))
                throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED);
            if (Set.of("age","gender").contains(key) && !changes.containsKey("id_card")) throw exception(FIELD_INVALID, field.getLabel());
        }
        if (changes.containsKey("id_card")) for (String derived : List.of("age","gender"))
            if (definitions.stream().anyMatch(f -> f.getEnabled() && derived.equals(f.getKey()))) changes.put(derived, null);
        var changedFields = definitions.stream().filter(f -> changes.containsKey(f.getKey()) && !"images".equals(f.getType())).toList();
        Map<String,Object> textChanges = new LinkedHashMap<>();
        changedFields.forEach(f -> textChanges.put(f.getKey(), changes.get(f.getKey())));
        var validated = fields.validateValues(changedFields, textChanges);
        changedFields.forEach(f -> merged.remove(f.getKey()));
        validated.forEach(v -> { v.setFormId(form.getId()); v.setTenantId(form.getTenantId()); merged.put(v.getFieldKey(),v); });
        var selected = new LinkedHashSet<>(imageIds(form, latest));
        for (var field : definitions) if ("images".equals(field.getType()) && changes.containsKey(field.getKey())) {
            Object raw = changes.get(field.getKey());
            if (!(raw instanceof List<?> ids)) throw exception(FIELD_INVALID, field.getLabel());
            selected.removeIf(id -> { var image = imageMapper.selectById(id);
                if (image == null || !Objects.equals(image.getFormId(),form.getId())) throw exception(LINK_INVALID);
                return image.getFieldKey().equals(field.getKey()); });
            for (Object id : ids) {
                if (!(id instanceof Number number) || !id.toString().matches("[0-9]+")) throw exception(FIELD_INVALID, field.getLabel());
                var image = imageMapper.selectById(number.longValue());
                if (image == null || !Objects.equals(image.getFormId(), form.getId()) || !field.getKey().equals(image.getFieldKey())
                        || !selected.add(image.getId())) throw exception(FIELD_INVALID, field.getLabel());
            }
        }
        images.validateArchive(form, definitions, new ArrayList<>(selected));
        var row = new StudentArchiveRevisionDO(); row.setFormId(form.getId()); row.setPersonId(form.getPersonId()); row.setRevision(current+1);
        row.setOperatorUserId(getLoginUserId()); row.setReason(Objects.requireNonNullElse(request.getReason(), ""));
        row.setIdempotencyKey(request.getIdempotencyKey()); row.setRequestHash(fingerprint);
        row.setAnswersJson(JsonUtils.toJsonString(new ArrayList<>(merged.values()))); row.setImageIdsJson(JsonUtils.toJsonString(selected));
        row.setTenantId(form.getTenantId()); revisions.insert(row); return row.getRevision();
    }
    @ZsjosPermission(bizType="student-archive", bizId="#relationId", action="update")
    @Transactional(rollbackFor=Exception.class)
    public StudentInfoVO.Image upload(Long relationId, String fieldKey, String side, MultipartFile file) {
        cn.iocoder.yudao.module.zsjos.service.registration.TeachingServiceLifecycle.requireOpen(relations.selectByIdForUpdate(relationId,cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId()));
        if (!permitted(ARCHIVE_SENSITIVE)) throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED);
        return images.archiveUpload(forms.lock(submitted(relationId).getId()), fieldKey, side, file);
    }
    @ZsjosPermission(bizType="student-archive", bizId="#relationId", action="read")
    public byte[] image(Long relationId, Long imageId) {
        if (!permitted(ARCHIVE_SENSITIVE)) throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED);
        var form = submitted(relationId); var image = imageMapper.selectById(imageId);
        if (image == null || !Objects.equals(image.getFormId(),form.getId())) throw exception(LINK_INVALID);
        return images.archiveRead(form.getId(), imageId);
    }
    @ZsjosPermission(bizType="student-archive", bizId="#relationId", action="read")
    public Detail export(Long relationId) {
        if (!permitted(ARCHIVE_EXPORT)) throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED);
        return detail(relationId,permitted(ARCHIVE_SENSITIVE));
    }
}
