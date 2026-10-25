package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentArchiveExtraVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.registration.ServiceRelationDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import java.net.URI;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.*;

@Service
public class StudentArchiveExtraService {
    @Resource private ServiceRelationMapper relations;
    @Resource private StudentArchiveExtraRevisionMapper revisions;
    @Resource private StudentArchiveExtraConfigMapper configMapper;
    @Resource private StudentArchiveAttachmentMapper attachments;
    @Resource private StudentInfoImageService encryption;
    @Resource private PermissionApi permissions;
    @Resource private AdminUserApi users;
    @Resource private FileApi files;
    @Resource private StudentInfoFormMapper forms;
    @Resource private StudentInfoFormValueMapper answers;
    @Resource private StudentArchiveRevisionMapper collectionRevisions;

    public static List<Field> definitions() {
        var docs = List.of("jpg","jpeg","png","pdf","doc","docx");
        var pictures = List.of("jpg","jpeg","png");
        return List.of(
            new Field("channel","对接渠道","text",List.of()),
            new Field("registration_fee","报名费","number",List.of()),
            new Field("admission_ticket","准考证","composite",docs),
            new Field("theory","理论","composite",docs),
            new Field("skill","技能","composite",docs),
            new Field("thesis","论文/综合评审","composite",pictures),
            new Field("assessment_result","评定结果","text",List.of()),
            new Field("issuing_authority","发证单位","composite",docs),
            new Field("certificate_images","证书图片","composite",pictures),
            new Field("certificate_shipping_address","证书邮寄地址","textarea",List.of()),
            new Field("certificate_tracking_no","证书邮寄单号","text",List.of()));
    }
    private Field field(String key) {
        return definitions().stream().filter(f -> f.key().equals(key)).findFirst().orElseThrow(() -> exception(FIELD_INVALID,"未知档案字段"));
    }
    private boolean permitted(String code) { return permissions.hasAnyPermissions(getLoginUserId(),code); }
    private Map<String,Boolean> defaultVisibility() {
        var result = new LinkedHashMap<String,Boolean>();
        definitions().forEach(field -> result.put(field.key(), true));
        return result;
    }
    private VisibilityState visibilityState() {
        var row = configMapper.current();
        var visibility = defaultVisibility();
        if (row == null || row.getVisibilityJson() == null || row.getVisibilityJson().isBlank())
            return new VisibilityState(0, visibility);
        var stored = JsonUtils.parseObject(row.getVisibilityJson(), new tools.jackson.core.type.TypeReference<Map<String,Boolean>>() {});
        if (stored != null) stored.forEach((key, value) -> { if (visibility.containsKey(key)) visibility.put(key, Boolean.TRUE.equals(value)); });
        return new VisibilityState(row.getRevision(), visibility);
    }
    private record VisibilityState(int revision, Map<String,Boolean> values) { }
    private boolean examReader() { return permitted(EXAM_READ); }
    private ServiceRelationDO relation(Long id, boolean lock) {
        var row = lock ? relations.selectOne(new LambdaQueryWrapperX<ServiceRelationDO>().eq(ServiceRelationDO::getId,id).last("FOR UPDATE")) : relations.selectById(id);
        if (row == null) throw exception(LINK_INVALID);
        return row;
    }
    private Map<String,Value> values(StudentArchiveExtraRevisionDO row) {
        if (row == null) return new LinkedHashMap<>();
        var parsed = JsonUtils.parseObject(row.getValuesJson(), new tools.jackson.core.type.TypeReference<Map<String,Value>>() {});
        return new LinkedHashMap<>(parsed);
    }
    @ZsjosPermission(bizType="student-archive",bizId="#relationId",action="read")
    public Detail detail(Long relationId) {
        var relation = relation(relationId,false);
        var latest = revisions.latest(relationId);
        var state = visibilityState();
        boolean examReader = examReader();
        boolean canConfigure = permitted(EXAM_VISIBILITY_UPDATE);
        var visible = definitions().stream().filter(field -> examReader || state.values().getOrDefault(field.key(), true)).toList();
        var result = new Detail();
        result.setFields(canConfigure ? definitions() : visible);
        result.setCanMaintain(permitted(ARCHIVE_UPDATE) && !cn.iocoder.yudao.module.zsjos.service.registration.TeachingServiceLifecycle.closed(relation));
        result.setCanConfigureVisibility(canConfigure);
        result.setVisibilityRevision(state.revision());
        result.setEducationVisibility(canConfigure ? state.values() : visible.stream().collect(java.util.stream.Collectors.toMap(Field::key, field -> true, (a,b) -> a, LinkedHashMap::new)));
        boolean sensitive = permitted(ARCHIVE_SENSITIVE); result.setCanReadAttachments(sensitive);
        result.setRevision(latest == null ? 0 : latest.getRevision());
        var content = values(latest);
        // A collected address is a fallback until an internal override is explicitly saved.
        if (!content.containsKey("certificate_shipping_address") && relation.getRegistrationCaseId() != null) {
            var form = forms.byCase(relation.getRegistrationCaseId());
            if (form != null && "SUBMITTED".equals(form.getStatus())) {
                if (!Objects.equals(form.getPersonId(),relation.getPersonId()) || !Objects.equals(form.getOrderItemId(),relation.getOrderItemId())) throw exception(LINK_INVALID);
                var revision = collectionRevisions.latest(form.getId());
                var source = revision == null ? answers.byForm(form.getId()) : JsonUtils.parseArray(revision.getAnswersJson(),StudentInfoFormValueDO.class);
                source.stream().filter(a -> "certificate_shipping_address".equals(a.getFieldKey())).findFirst().ifPresent(a -> {
                    var value = new Value(); value.setText(a.getValueText()); content.put(a.getFieldKey(),value);
                });
            }
        }
        if (!examReader) content.keySet().removeIf(key -> !state.values().getOrDefault(key, true));
        if (!sensitive && content.containsKey("certificate_shipping_address")) {
            var masked = new Value(); masked.setText("已隐藏"); content.put("certificate_shipping_address",masked);
        }
        result.setValues(content);
        if (sensitive) result.setAttachments(content.values().stream().flatMap(v -> v.getAttachmentIds().stream()).distinct()
            .map(id -> project(requireAttachment(relationId,id,null))).toList());
        result.setHistory(revisions.history(relationId).stream().map(row -> {
            var h = new StudentArchiveVO.History(); h.setRevision(row.getRevision()); h.setReason(row.getReason());
            h.setModifiedAt(row.getCreateTime()); h.setOperatorUserId(row.getOperatorUserId());
            var user = users.getUser(row.getOperatorUserId()); h.setOperatorName(user == null ? "已离职或不可用用户" : user.getNickname()); return h;
        }).toList());
        return result;
    }

    @Transactional(rollbackFor=Exception.class)
    public Integer saveVisibility(VisibilitySave request) {
        if (!permitted(EXAM_VISIBILITY_UPDATE))
            throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED);
        var expected = defaultVisibility();
        if (!request.getVisibility().keySet().equals(expected.keySet()))
            throw exception(FIELD_INVALID, "考试字段可见性配置不完整");
        var row = configMapper.currentForUpdate();
        int current = row == null ? 0 : row.getRevision();
        if (!Objects.equals(request.getRevision(), current)) throw exception(VERSION_CONFLICT);
        if (row == null) {
            row = new StudentArchiveExtraConfigDO();
            row.setRevision(1);
            row.setVisibilityJson(JsonUtils.toJsonString(request.getVisibility()));
            configMapper.insert(row);
        } else {
            row.setRevision(current + 1);
            row.setVisibilityJson(JsonUtils.toJsonString(request.getVisibility()));
            configMapper.updateById(row);
        }
        return row.getRevision();
    }
    public static void validate(Field f, Value value) {
        if (value == null || value.getText() == null || value.getText().length()>2000 || value.getLinks()==null || value.getAttachmentIds()==null
            || value.getLinks().size()>9 || value.getAttachmentIds().size()>9) throw exception(FIELD_INVALID,f.label());
        if (!"composite".equals(f.type()) && (!value.getLinks().isEmpty() || !value.getAttachmentIds().isEmpty())) throw exception(FIELD_INVALID,f.label());
        if ("number".equals(f.type()) && !value.getText().isBlank()) {
            if (!value.getText().matches("[0-9]{1,10}(\\.[0-9]{1,2})?")) throw exception(FIELD_INVALID,"报名费须为非负数字，最多两位小数");
        }
        if (new HashSet<>(value.getAttachmentIds()).size()!=value.getAttachmentIds().size()) throw exception(FIELD_INVALID,"重复附件");
        for (String link : value.getLinks()) {
            try {
                var uri = URI.create(link);
                if (link.length()>2000 || !Set.of("https","http").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null) throw new IllegalArgumentException();
            } catch (RuntimeException error) { throw exception(FIELD_INVALID,"链接须为完整的 HTTP/HTTPS 地址"); }
        }
    }
    @ZsjosPermission(bizType="student-archive",bizId="#relationId",action="update")
    @Transactional(rollbackFor=Exception.class)
    public Integer save(Long relationId, Save request) {
        cn.iocoder.yudao.module.zsjos.service.registration.TeachingServiceLifecycle.requireOpen(relation(relationId,true));
        String fingerprint = DigestUtil.sha256Hex(JsonUtils.toJsonString(request));
        var prior = revisions.byKey(request.getIdempotencyKey());
        if (prior != null) {
            if (!Objects.equals(prior.getServiceRelationId(),relationId) || !Objects.equals(prior.getOperatorUserId(),getLoginUserId()) || !fingerprint.equals(prior.getRequestHash())) throw exception(VERSION_CONFLICT);
            return prior.getRevision();
        }
        var latest = revisions.latest(relationId); int current = latest == null ? 0 : latest.getRevision();
        if (!Objects.equals(request.getRevision(),current)) throw exception(VERSION_CONFLICT);
        var merged = values(latest);
        for (var change : request.getChanges().entrySet()) {
            var f = field(change.getKey()); var value = change.getValue(); validate(f,value);
            if (!examReader() && !visibilityState().values().getOrDefault(f.key(), true))
                throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED);
            if ("certificate_shipping_address".equals(f.key()) && !permitted(ARCHIVE_SENSITIVE)) throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED);
            var old = merged.get(f.key());
            var oldIds = old == null ? List.<Long>of() : old.getAttachmentIds();
            if (!permitted(ARCHIVE_SENSITIVE) && !oldIds.equals(value.getAttachmentIds())) throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED);
            for (Long id : value.getAttachmentIds()) requireAttachment(relationId,id,f.key());
            value.setText(value.getText().trim()); merged.put(f.key(),value);
        }
        var row = new StudentArchiveExtraRevisionDO(); row.setServiceRelationId(relationId); row.setRevision(current+1);
        row.setOperatorUserId(getLoginUserId()); row.setReason(Objects.requireNonNullElse(request.getReason(), "")); row.setIdempotencyKey(request.getIdempotencyKey());
        row.setRequestHash(fingerprint); row.setValuesJson(JsonUtils.toJsonString(merged)); revisions.insert(row); return row.getRevision();
    }
    private StudentArchiveAttachmentDO requireAttachment(Long relationId,Long id,String key) {
        var row = attachments.selectById(id);
        if (row == null || !Objects.equals(row.getServiceRelationId(),relationId) || key!=null && !key.equals(row.getFieldKey())) throw exception(FIELD_INVALID,"附件不属于当前报名或字段");
        return row;
    }
    private StudentInfoVO.Image project(StudentArchiveAttachmentDO row) {
        var result = new StudentInfoVO.Image(); result.setId(row.getId()); result.setFieldKey(row.getFieldKey());
        result.setName(field(row.getFieldKey()).label()+"-"+row.getId()+StudentInfoAttachmentValidator.extension(row.getContentType()));
        result.setSize(row.getFileSize()); result.setContentType(row.getContentType()); return result;
    }
    @ZsjosPermission(bizType="student-archive",bizId="#relationId",action="update")
    @Transactional(rollbackFor=Exception.class)
    public StudentInfoVO.Image upload(Long relationId,String key,MultipartFile upload) {
        cn.iocoder.yudao.module.zsjos.service.registration.TeachingServiceLifecycle.requireOpen(relation(relationId,true));
        if (!examReader() && !visibilityState().values().getOrDefault(key, true))
            throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED);
        if (!permitted(ARCHIVE_SENSITIVE)) throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED);
        var f = field(key);
        if (f.allowedExtensions().isEmpty() || upload.isEmpty() || upload.getSize()>10*1024*1024L) throw exception(FIELD_INVALID,"附件格式或大小");
        try {
            byte[] content = upload.getBytes();
            String type = StudentInfoAttachmentValidator.validate(content,upload.getOriginalFilename(),f.allowedExtensions());
            var stored = files.createFileInfo(encryption.crypt(content,true),UUID.randomUUID()+".bin","student-archive-private/"+relationId,"application/octet-stream");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status!=STATUS_COMMITTED) try { files.deleteFileIfExists(stored.getId()); } catch (Exception ignored) { }
                }
            });
            var row = new StudentArchiveAttachmentDO(); row.setServiceRelationId(relationId); row.setFieldKey(key);
            row.setInfraFileId(stored.getId()); row.setFileSize((long)content.length); row.setContentType(type); attachments.insert(row); return project(row);
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException e) { throw e; }
        catch (Exception e) { throw exception(FIELD_INVALID,"附件上传失败，请重试"); }
    }
    @ZsjosPermission(bizType="student-archive",bizId="#relationId",action="read")
    public byte[] download(Long relationId,Long id) {
        relation(relationId,false);
        if (!permitted(ARCHIVE_SENSITIVE)) throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED);
        try {
            var row = requireAttachment(relationId,id,null);
            if (!examReader() && !visibilityState().values().getOrDefault(row.getFieldKey(), true))
                throw exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.LEAD_PERMISSION_DENIED);
            var stored = files.getFileInfo(row.getInfraFileId());
            if (stored == null) throw exception(FIELD_INVALID,"附件不可用");
            return encryption.crypt(files.getFileContent(row.getInfraFileId()),false);
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException e) { throw e; }
        catch (Exception e) { throw exception(FIELD_INVALID,"附件读取失败，请重试"); }
    }
}
