package cn.iocoder.yudao.module.zsjos.service.forcedform;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.dept.dto.PostRespDTO;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.forcedform.vo.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.forcedform.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.forcedform.*;
import cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertMap;
import static cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean;

@Service
public class ForcedFormServiceImpl implements ForcedFormService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_WITHDRAWN = "WITHDRAWN";
    private static final String RECIPIENT_PENDING = "PENDING";
    private static final String RECIPIENT_COMPLETED = "COMPLETED";
    private static final String BATCH_SENT = "SENT";
    private static final String FILE_TEMPORARY = "TEMPORARY";
    private static final String FILE_BOUND = "BOUND";
    private static final String FILE_EXPIRED = "EXPIRED";

    @Resource private ForcedFormMapper formMapper;
    @Resource private ForcedFormVersionMapper versionMapper;
    @Resource private ForcedFormBatchMapper batchMapper;
    @Resource private ForcedFormRecipientMapper recipientMapper;
    @Resource private ForcedFormSubmissionMapper submissionMapper;
    @Resource private ForcedFormSubmissionFileMapper submissionFileMapper;
    @Resource private ForcedFormFieldValidator fieldValidator;
    @Resource private AdminUserApi adminUserApi;
    @Resource private DeptApi deptApi;
    @Resource private PostApi postApi;
    @Resource private DictDataApi dictDataApi;
    @Resource private FileApi fileApi;

    @Override
    public PageResult<ForcedFormRespVO> page(ForcedFormPageReqVO req) {
        var page = formMapper.selectPage(req, Wrappers.<ForcedFormDO>lambdaQuery()
                .like(StrUtil.isNotBlank(req.getName()), ForcedFormDO::getName, req.getName())
                .eq(StrUtil.isNotBlank(req.getStatus()), ForcedFormDO::getStatus, req.getStatus())
                .orderByDesc(ForcedFormDO::getId));
        List<ForcedFormRespVO> list = page.getList().stream().map(this::toFormVO).toList();
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ForcedFormSaveReqVO req, Long actor) {
        List<ForcedFormFieldDefinition> fields = fieldValidator.parseAndValidate(req.getFieldsJson());
        ForcedFormDO form = toBean(req, ForcedFormDO.class);
        form.setId(null);
        form.setStatus(STATUS_DRAFT);
        form.setVersion(0);
        form.setCurrentVersionId(null);
        form.setFieldsJson(JsonUtils.toJsonString(fields));
        formMapper.insert(form);
        return form.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "forced-form", bizId = "#req.id", action = "update")
    public void update(ForcedFormSaveReqVO req) {
        ForcedFormDO form = requireForm(req.getId());
        if (!STATUS_DRAFT.equals(form.getStatus())) {
            throw exception(ZsjosErrorCodeConstants.FORCED_FORM_VERSION_INVALID);
        }
        List<ForcedFormFieldDefinition> fields = fieldValidator.parseAndValidate(req.getFieldsJson());
        form.setName(req.getName());
        form.setDescription(req.getDescription());
        form.setFieldsJson(JsonUtils.toJsonString(fields));
        formMapper.updateById(form);
    }

    @Override
    @ZsjosPermission(bizType = "forced-form", bizId = "#id", action = "read")
    public ForcedFormRespVO get(Long id) {
        return toFormVO(requireForm(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "forced-form", bizId = "#id", action = "delete")
    public void delete(Long id, Long actor) {
        ForcedFormDO form = requireForm(id);
        if (!STATUS_DRAFT.equals(form.getStatus())) {
            throw exception(ZsjosErrorCodeConstants.FORCED_FORM_VERSION_INVALID);
        }
        formMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "forced-form", bizId = "#id", action = "copy")
    public ForcedFormRespVO copy(Long id, Long actor) {
        ForcedFormDO form = requireForm(id);
        ForcedFormDO copy = new ForcedFormDO();
        copy.setName(form.getName() + " - 副本");
        copy.setDescription(form.getDescription());
        copy.setFieldsJson(form.getFieldsJson());
        copy.setStatus(STATUS_DRAFT);
        copy.setVersion(0);
        formMapper.insert(copy);
        return toFormVO(copy);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "forced-form", bizId = "#id", action = "publish")
    public void publish(Long id, Long actor) {
        ForcedFormDO form = requireForm(id);
        if (STATUS_PUBLISHED.equals(form.getStatus())) {
            throw exception(ZsjosErrorCodeConstants.FORCED_FORM_VERSION_INVALID);
        }
        List<ForcedFormFieldDefinition> fields = fieldValidator.parseAndValidate(form.getFieldsJson());
        ForcedFormVersionDO version = new ForcedFormVersionDO();
        version.setFormId(form.getId());
        version.setVersionNo(form.getVersion() + 1);
        version.setFieldsJson(JsonUtils.toJsonString(fields));
        version.setSchemaHash(schemaHash(fields));
        version.setStatus(STATUS_PUBLISHED);
        version.setPublishedAt(LocalDateTime.now());
        versionMapper.insert(version);
        form.setFieldsJson(JsonUtils.toJsonString(fields));
        form.setVersion(version.getVersionNo());
        form.setCurrentVersionId(version.getId());
        form.setStatus(STATUS_PUBLISHED);
        formMapper.updateById(form);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "forced-form", bizId = "#id", action = "withdraw")
    public void withdraw(Long id, Long actor) {
        ForcedFormDO form = requireForm(id);
        if (!STATUS_PUBLISHED.equals(form.getStatus())) {
            throw exception(ZsjosErrorCodeConstants.FORCED_FORM_VERSION_INVALID);
        }
        form.setStatus(STATUS_WITHDRAWN);
        formMapper.updateById(form);
    }

    @Override
    @ZsjosPermission(bizType = "forced-form", bizId = "#id", action = "send")
    public ForcedFormRecipientPreviewRespVO recipientPreview(Long id, ForcedFormSendReqVO req) {
        ForcedFormDO form = requirePublishedForm(id);
        List<RecipientCandidate> recipients = resolveRecipients(req);
        Map<Long, ForcedFormRecipientDO> existing = existingRecipients(form.getId(), recipients);
        Map<Long, ForcedFormSubmissionDO> completed = existingSubmissions(form.getId(), recipients);
        return toPreviewVO(recipients, existing, completed);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "forced-form", bizId = "#id", action = "send")
    public ForcedFormSendRespVO send(Long id, ForcedFormSendReqVO req, Long actor) {
        ForcedFormDO form = requirePublishedForm(id);
        List<RecipientCandidate> recipients = resolveRecipients(req);
        Map<Long, ForcedFormRecipientDO> existingRecipients = existingRecipients(form.getId(), recipients);
        Map<Long, ForcedFormSubmissionDO> completed = existingSubmissions(form.getId(), recipients);

        ForcedFormBatchDO batch = new ForcedFormBatchDO();
        batch.setFormId(form.getId());
        batch.setVersionId(form.getCurrentVersionId());
        batch.setScopeType(req.getScopeType());
        batch.setScopeConfigJson(JsonUtils.toJsonString(Map.of(
                "userIds", normalizeIds(req.getUserIds()),
                "deptIds", normalizeIds(req.getDeptIds()),
                "postIds", normalizeIds(req.getPostIds()))));
        batch.setStatus(BATCH_SENT);
        batch.setSentAt(LocalDateTime.now());
        batchMapper.insert(batch);

        List<ForcedFormRecipientDO> rows = new ArrayList<>();
        int skippedCompleted = 0;
        for (RecipientCandidate candidate : recipients) {
            if (completed.containsKey(candidate.userId)) {
                skippedCompleted++;
                continue;
            }
            if (existingRecipients.containsKey(candidate.userId)) {
                continue;
            }
            ForcedFormRecipientDO row = new ForcedFormRecipientDO();
            row.setBatchId(batch.getId());
            row.setFormId(form.getId());
            row.setUserId(candidate.userId);
            row.setNicknameSnapshot(candidate.nickname);
            row.setDeptSnapshot(candidate.deptName);
            row.setPostSnapshot(candidate.postNames);
            row.setSource(req.getScopeType());
            row.setStatus(RECIPIENT_PENDING);
            rows.add(row);
        }
        if (!rows.isEmpty()) {
            recipientMapper.insertBatch(rows);
        }

        ForcedFormSendRespVO resp = new ForcedFormSendRespVO();
        resp.setBatchId(batch.getId());
        resp.setRecipientCount(rows.size());
        resp.setSkippedCompletedCount(skippedCompleted);
        resp.setFilteredCount(Math.max(0, recipients.size() - rows.size() - skippedCompleted));
        return resp;
    }

    @Override
    public List<ForcedFormPendingRespVO> pending(Long userId) {
        List<ForcedFormRecipientDO> rows = recipientMapper.selectList(Wrappers.<ForcedFormRecipientDO>lambdaQuery()
                .eq(ForcedFormRecipientDO::getUserId, userId)
                .eq(ForcedFormRecipientDO::getStatus, RECIPIENT_PENDING)
                .orderByAsc(ForcedFormRecipientDO::getId));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, ForcedFormBatchDO> batches = batchMapper.selectList(ForcedFormBatchDO::getId, rows.stream()
                .map(ForcedFormRecipientDO::getBatchId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(ForcedFormBatchDO::getId, item -> item));
        Map<Long, ForcedFormDO> forms = formMapper.selectList(ForcedFormDO::getId, rows.stream()
                .map(ForcedFormRecipientDO::getFormId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(ForcedFormDO::getId, item -> item));
        return rows.stream()
                .filter(row -> {
                    ForcedFormDO form = forms.get(row.getFormId());
                    return form != null && STATUS_PUBLISHED.equals(form.getStatus()) && form.getCurrentVersionId() != null;
                })
                .sorted(Comparator.comparing((ForcedFormRecipientDO row) -> {
                    ForcedFormBatchDO batch = batches.get(row.getBatchId());
                    return batch == null || batch.getSentAt() == null ? LocalDateTime.MIN : batch.getSentAt();
                }).thenComparing(ForcedFormRecipientDO::getId))
                .map(row -> {
                    ForcedFormDO form = forms.get(row.getFormId());
                    ForcedFormBatchDO batch = batches.get(row.getBatchId());
                    ForcedFormPendingRespVO vo = new ForcedFormPendingRespVO();
                    vo.setFormId(form.getId());
                    vo.setVersionId(form.getCurrentVersionId());
                    vo.setBatchId(row.getBatchId());
                    vo.setRecipientId(row.getId());
                    vo.setName(form.getName());
                    vo.setDescription(form.getDescription());
                    vo.setVersion(form.getVersion());
                    vo.setSentAt(batch == null ? null : batch.getSentAt());
                    return vo;
                }).toList();
    }

    @Override
    @ZsjosPermission(bizType = "forced-form", bizId = "#id", action = "runtime")
    public ForcedFormRuntimeRespVO runtime(Long id, Long userId) {
        ForcedFormRecipientDO recipient = requirePendingRecipient(id, userId);
        ForcedFormDO form = requirePublishedForm(id);
        ForcedFormVersionDO version = requireCurrentVersion(form);
        List<ForcedFormFieldDefinition> fields = fieldValidator.parseAndValidate(version.getFieldsJson());
        ForcedFormRuntimeRespVO resp = new ForcedFormRuntimeRespVO();
        resp.setFormId(form.getId());
        resp.setVersionId(version.getId());
        resp.setVersion(version.getVersionNo());
        resp.setName(form.getName());
        resp.setDescription(form.getDescription());
        resp.setRecipientId(recipient.getId());
        resp.setBatchId(recipient.getBatchId());
        resp.setFields(fields.stream().map(this::toRuntimeField).toList());
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "forced-form", bizId = "#id", action = "attachment-upload")
    public ForcedFormAttachmentUploadRespVO uploadAttachment(Long id, Long userId, String fieldKey, MultipartFile file) {
        requirePendingRecipient(id, userId);
        ForcedFormDO form = requirePublishedForm(id);
        ForcedFormVersionDO version = requireCurrentVersion(form);
        ForcedFormFieldDefinition field = requireField(version, fieldKey);
        if (!"attachment".equals(field.getType())) {
            throw exception(ZsjosErrorCodeConstants.FORCED_FORM_ATTACHMENT_INVALID);
        }
        validateAttachment(file, field);
        if (field.getMaxCount() != null) {
            Long currentCount = submissionFileMapper.selectCount(Wrappers.<ForcedFormSubmissionFileDO>lambdaQuery()
                    .eq(ForcedFormSubmissionFileDO::getFormId, form.getId())
                    .eq(ForcedFormSubmissionFileDO::getVersionId, version.getId())
                    .eq(ForcedFormSubmissionFileDO::getUserId, userId)
                    .eq(ForcedFormSubmissionFileDO::getFieldKey, field.getKey())
                    .eq(ForcedFormSubmissionFileDO::getStatus, FILE_TEMPORARY));
            if (currentCount != null && currentCount >= field.getMaxCount()) {
                throw exception(ZsjosErrorCodeConstants.FORCED_FORM_ATTACHMENT_INVALID);
            }
        }

        FileInfoRespDTO saved = fileApi.createFileInfo(readBytes(file), normalizeFileName(file.getOriginalFilename()),
                buildDirectory(form.getId(), userId), file.getContentType());
        ForcedFormSubmissionFileDO row = new ForcedFormSubmissionFileDO();
        row.setFormId(form.getId());
        row.setVersionId(version.getId());
        row.setUserId(userId);
        row.setFieldKey(field.getKey());
        row.setInfraFileId(saved.getId());
        row.setUploadToken(newUploadToken());
        row.setFileName(saved.getName());
        row.setFileSize(saved.getSize());
        row.setContentType(saved.getType());
        row.setStatus(FILE_TEMPORARY);
        submissionFileMapper.insert(row);

        ForcedFormAttachmentUploadRespVO resp = new ForcedFormAttachmentUploadRespVO();
        resp.setFormId(form.getId());
        resp.setVersionId(version.getId());
        resp.setFieldKey(field.getKey());
        resp.setInfraFileId(saved.getId());
        resp.setUploadToken(row.getUploadToken());
        resp.setFileName(saved.getName());
        resp.setFileSize(saved.getSize());
        resp.setContentType(saved.getType());
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ZsjosPermission(bizType = "forced-form", bizId = "#id", action = "submit")
    public void submit(Long id, ForcedFormSubmitReqVO req, Long userId) {
        ForcedFormRecipientDO recipient = requirePendingRecipient(id, userId);
        ForcedFormDO form = requirePublishedForm(id);
        ForcedFormVersionDO version = requireCurrentVersion(form);
        List<ForcedFormFieldDefinition> fields = fieldValidator.parseAndValidate(version.getFieldsJson());
        Map<String, Object> answers = JsonUtils.parseObject(req.getAnswersJson(), Map.class);
        Map<String, Object> normalized = fieldValidator.normalizeAnswers(fields, answers);
        validateSubmissionNotExists(form.getId(), userId);

        ForcedFormSubmissionDO submission = new ForcedFormSubmissionDO();
        submission.setFormId(form.getId());
        submission.setVersionId(version.getId());
        submission.setUserId(userId);
        submission.setFieldsSnapshotJson(version.getFieldsJson());
        submission.setAnswersJson(JsonUtils.toJsonString(normalized));
        submission.setDictSnapshotJson(JsonUtils.toJsonString(fieldValidator.buildDictSnapshot(fields, normalized)));
        submission.setPlatform(req.getPlatform());
        submissionMapper.insert(submission);

        bindAttachments(form, version, userId, submission.getId(), fields, normalized);

        recipient.setStatus(RECIPIENT_COMPLETED);
        recipient.setCompletedAt(LocalDateTime.now());
        recipient.setSubmissionId(submission.getId());
        recipientMapper.updateById(recipient);
    }

    @Override
    public ForcedFormStatusRespVO status(Long userId) {
        List<ForcedFormPendingRespVO> pending = pending(userId);
        ForcedFormStatusRespVO resp = new ForcedFormStatusRespVO();
        resp.setPendingCount(pending.size());
        if (!pending.isEmpty()) {
            resp.setFirstPendingFormId(pending.get(0).getFormId());
            resp.setFirstPendingFormName(pending.get(0).getName());
        }
        return resp;
    }

    @Override
    public PageResult<ForcedFormSubmissionListRespVO> submissionPage(ForcedFormSubmissionPageReqVO req) {
        var page = submissionMapper.selectPage(req, Wrappers.<ForcedFormSubmissionDO>lambdaQuery()
                .eq(req.getFormId() != null, ForcedFormSubmissionDO::getFormId, req.getFormId())
                .eq(req.getUserId() != null, ForcedFormSubmissionDO::getUserId, req.getUserId())
                .eq(StrUtil.isNotBlank(req.getPlatform()), ForcedFormSubmissionDO::getPlatform, req.getPlatform())
                .orderByDesc(ForcedFormSubmissionDO::getId));
        return new PageResult<>(toSubmissionList(page.getList()), page.getTotal());
    }

    @Override
    @ZsjosPermission(bizType = "forced-form-submission", bizId = "#id", action = "read")
    public ForcedFormSubmissionRespVO submission(Long id) {
        ForcedFormSubmissionDO submission = requireSubmission(id);
        return toSubmissionVO(submission);
    }

    @Override
    public void exportSubmissions(ForcedFormSubmissionPageReqVO req, HttpServletResponse response) {
        PageResult<ForcedFormSubmissionListRespVO> page = submissionPage(copyPageSizeNone(req));
        List<List<String>> head = List.of(
                List.of("提交ID"),
                List.of("表单ID"),
                List.of("表单名称"),
                List.of("版本ID"),
                List.of("版本号"),
                List.of("用户ID"),
                List.of("用户昵称"),
                List.of("平台"),
                List.of("提交时间"),
                List.of("字段快照"),
                List.of("答案JSON"),
                List.of("字典快照"));
        List<List<Object>> rows = new ArrayList<>();
        for (ForcedFormSubmissionListRespVO item : page.getList()) {
            ForcedFormSubmissionDO submission = submissionMapper.selectById(item.getId());
            rows.add(List.of(
                    item.getId(), item.getFormId(), item.getFormName(), item.getVersionId(), item.getVersion(),
                    item.getUserId(), item.getUserNickname(), item.getPlatform(), item.getCreateTime(),
                    submission == null ? "" : submission.getFieldsSnapshotJson(),
                    submission == null ? "" : submission.getAnswersJson(),
                    submission == null ? "" : submission.getDictSnapshotJson()));
        }
        try {
            ExcelUtils.write(response, "强制表单提交.xls", "提交记录", head, rows);
        } catch (IOException exception) {
            throw new IllegalStateException("导出强制表单提交失败", exception);
        }
    }

    @Override
    @cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit(action = "forced-form.cleanup-temporary-files", targetType = "forced-form-file")
    public int cleanupTemporaryFiles() {
        LocalDateTime expiredAt = LocalDateTime.now().minusHours(24);
        List<ForcedFormSubmissionFileDO> files = submissionFileMapper.selectExpiredTemporaryFiles(expiredAt);
        int deleted = 0;
        for (ForcedFormSubmissionFileDO row : files) {
            try {
                if (row.getInfraFileId() != null) {
                    fileApi.deleteFileIfExists(row.getInfraFileId());
                }
                row.setStatus(FILE_EXPIRED);
                submissionFileMapper.updateById(row);
                deleted++;
            } catch (Exception ignored) {
                // Keep the row for the next retry; the scheduler records the failure in logs.
            }
        }
        return deleted;
    }

    private ForcedFormRespVO toFormVO(ForcedFormDO form) {
        if (form == null) {
            throw exception(ZsjosErrorCodeConstants.FORCED_FORM_NOT_EXISTS);
        }
        ForcedFormRespVO vo = toBean(form, ForcedFormRespVO.class);
        vo.setCurrentVersionId(form.getCurrentVersionId());
        vo.setRecipientCount(Math.toIntExact(recipientMapper.selectCount(ForcedFormRecipientDO::getFormId, form.getId())));
        vo.setCompletedCount(Math.toIntExact(recipientMapper.selectCount(Wrappers.<ForcedFormRecipientDO>lambdaQuery()
                .eq(ForcedFormRecipientDO::getFormId, form.getId())
                .eq(ForcedFormRecipientDO::getStatus, RECIPIENT_COMPLETED))));
        vo.setPendingCount(Math.max(0, vo.getRecipientCount() - vo.getCompletedCount()));
        ForcedFormBatchDO latestBatch = batchMapper.selectList(Wrappers.<ForcedFormBatchDO>lambdaQuery()
                        .eq(ForcedFormBatchDO::getFormId, form.getId())
                        .orderByDesc(ForcedFormBatchDO::getSentAt)
                        .orderByDesc(ForcedFormBatchDO::getId))
                .stream().findFirst().orElse(null);
        vo.setLastSentAt(latestBatch == null ? null : latestBatch.getSentAt());
        return vo;
    }

    private ForcedFormDO requireForm(Long id) {
        ForcedFormDO form = formMapper.selectById(id);
        if (form == null) {
            throw exception(ZsjosErrorCodeConstants.FORCED_FORM_NOT_EXISTS);
        }
        return form;
    }

    private ForcedFormDO requirePublishedForm(Long id) {
        ForcedFormDO form = requireForm(id);
        if (!STATUS_PUBLISHED.equals(form.getStatus()) || form.getCurrentVersionId() == null) {
            throw exception(ZsjosErrorCodeConstants.FORCED_FORM_VERSION_INVALID);
        }
        return form;
    }

    private ForcedFormVersionDO requireCurrentVersion(ForcedFormDO form) {
        ForcedFormVersionDO version = versionMapper.selectById(form.getCurrentVersionId());
        if (version == null || !Objects.equals(version.getFormId(), form.getId())) {
            throw exception(ZsjosErrorCodeConstants.FORCED_FORM_VERSION_INVALID);
        }
        return version;
    }

    private ForcedFormFieldDefinition requireField(ForcedFormVersionDO version, String fieldKey) {
        List<ForcedFormFieldDefinition> fields = fieldValidator.parseAndValidate(version.getFieldsJson());
        ForcedFormFieldDefinition field = fields.stream()
                .filter(item -> Objects.equals(item.getKey(), fieldKey)).findFirst().orElse(null);
        if (field == null) {
            throw exception(ZsjosErrorCodeConstants.FORCED_FORM_FIELD_INVALID);
        }
        return field;
    }

    private ForcedFormRecipientDO requirePendingRecipient(Long formId, Long userId) {
        ForcedFormRecipientDO recipient = recipientMapper.selectOne(Wrappers.<ForcedFormRecipientDO>lambdaQuery()
                .eq(ForcedFormRecipientDO::getFormId, formId)
                .eq(ForcedFormRecipientDO::getUserId, userId)
                .eq(ForcedFormRecipientDO::getStatus, RECIPIENT_PENDING));
        if (recipient == null) {
            throw exception(ZsjosErrorCodeConstants.FORCED_FORM_PERMISSION_DENIED);
        }
        return recipient;
    }

    private void validateSubmissionNotExists(Long formId, Long userId) {
        ForcedFormSubmissionDO existing = submissionMapper.selectOne(Wrappers.<ForcedFormSubmissionDO>lambdaQuery()
                .eq(ForcedFormSubmissionDO::getFormId, formId)
                .eq(ForcedFormSubmissionDO::getUserId, userId));
        if (existing != null) {
            throw exception(ZsjosErrorCodeConstants.FORCED_FORM_IDEMPOTENCY_CONFLICT);
        }
    }

    private List<RecipientCandidate> resolveRecipients(ForcedFormSendReqVO req) {
        String scopeType = req.getScopeType().trim().toUpperCase(Locale.ROOT);
        List<AdminUserRespDTO> users = switch (scopeType) {
            case "ALL" -> adminUserApi.getUserListByStatus(CommonStatusEnum.ENABLE.getStatus());
            case "USERS" -> {
                List<Long> userIds = normalizeIds(req.getUserIds());
                adminUserApi.validateUserList(userIds);
                yield adminUserApi.getUserList(userIds);
            }
            case "DEPARTMENTS" -> resolveUsersByDepartments(req.getDeptIds());
            case "POSTS" -> resolveUsersByPosts(req.getPostIds());
            default -> throw exception(ZsjosErrorCodeConstants.FORCED_FORM_BATCH_INVALID);
        };
        return buildCandidates(users);
    }

    private List<AdminUserRespDTO> resolveUsersByDepartments(List<Long> deptIds) {
        List<Long> ids = normalizeIds(deptIds);
        deptApi.validateDeptList(ids);
        Set<Long> resolvedDeptIds = new LinkedHashSet<>(ids);
        resolvedDeptIds.addAll(deptApi.getChildDeptList(ids).stream().map(DeptRespDTO::getId).toList());
        return adminUserApi.getUserListByDeptIds(resolvedDeptIds);
    }

    private List<AdminUserRespDTO> resolveUsersByPosts(List<Long> postIds) {
        List<Long> ids = normalizeIds(postIds);
        postApi.validPostList(ids);
        return adminUserApi.getUserListByPostIds(ids);
    }

    private List<RecipientCandidate> buildCandidates(List<AdminUserRespDTO> users) {
        if (CollUtil.isEmpty(users)) {
            return List.of();
        }
        Map<Long, DeptRespDTO> deptMap = deptApi.getDeptMap(users.stream().map(AdminUserRespDTO::getDeptId)
                .filter(Objects::nonNull).distinct().toList());
        Map<Long, PostRespDTO> postMap = postApi.getPostMap(users.stream().flatMap(user -> user.getPostIds() == null
                ? java.util.stream.Stream.<Long>empty() : user.getPostIds().stream()).filter(Objects::nonNull)
                .distinct().toList());
        return users.stream()
                .filter(user -> user != null && CommonStatusEnum.ENABLE.getStatus().equals(user.getStatus()))
                .map(user -> new RecipientCandidate(user.getId(), user.getNickname(),
                        deptName(user, deptMap), postNames(user, postMap)))
                .distinct()
                .toList();
    }

    private String deptName(AdminUserRespDTO user, Map<Long, DeptRespDTO> deptMap) {
        if (user.getDeptId() == null) {
            return null;
        }
        DeptRespDTO dept = deptMap.get(user.getDeptId());
        return dept == null ? null : dept.getName();
    }

    private String postNames(AdminUserRespDTO user, Map<Long, PostRespDTO> postMap) {
        if (CollUtil.isEmpty(user.getPostIds())) {
            return null;
        }
        return user.getPostIds().stream().map(postMap::get).filter(Objects::nonNull)
                .map(PostRespDTO::getName).distinct().collect(Collectors.joining("、"));
    }

    private Map<Long, ForcedFormRecipientDO> existingRecipients(Long formId, List<RecipientCandidate> recipients) {
        if (CollUtil.isEmpty(recipients)) {
            return Map.of();
        }
        return recipientMapper.selectList(Wrappers.<ForcedFormRecipientDO>lambdaQuery()
                        .eq(ForcedFormRecipientDO::getFormId, formId)
                        .in(ForcedFormRecipientDO::getUserId, recipients.stream().map(c -> c.userId).toList()))
                .stream().collect(Collectors.toMap(ForcedFormRecipientDO::getUserId, item -> item, (left, right) -> left));
    }

    private Map<Long, ForcedFormSubmissionDO> existingSubmissions(Long formId, List<RecipientCandidate> recipients) {
        if (CollUtil.isEmpty(recipients)) {
            return Map.of();
        }
        return submissionMapper.selectList(Wrappers.<ForcedFormSubmissionDO>lambdaQuery()
                        .eq(ForcedFormSubmissionDO::getFormId, formId)
                        .in(ForcedFormSubmissionDO::getUserId, recipients.stream().map(c -> c.userId).toList()))
                .stream().collect(Collectors.toMap(ForcedFormSubmissionDO::getUserId, item -> item, (left, right) -> left));
    }

    private ForcedFormRecipientPreviewRespVO toPreviewVO(List<RecipientCandidate> candidates,
                                                         Map<Long, ForcedFormRecipientDO> existing,
                                                         Map<Long, ForcedFormSubmissionDO> completed) {
        ForcedFormRecipientPreviewRespVO resp = new ForcedFormRecipientPreviewRespVO();
        List<ForcedFormRecipientPreviewRespVO.RecipientVO> recipients = new ArrayList<>();
        int filtered = 0;
        int skippedCompleted = 0;
        for (RecipientCandidate candidate : candidates) {
            if (completed.containsKey(candidate.userId)) {
                skippedCompleted++;
                continue;
            }
            if (existing.containsKey(candidate.userId)) {
                filtered++;
                continue;
            }
            ForcedFormRecipientPreviewRespVO.RecipientVO recipient = new ForcedFormRecipientPreviewRespVO.RecipientVO();
            recipient.setUserId(candidate.userId);
            recipient.setNickname(candidate.nickname);
            recipient.setDeptName(candidate.deptName);
            recipient.setPostNames(candidate.postNames);
            recipients.add(recipient);
        }
        resp.setRecipients(recipients);
        resp.setRecipientCount(recipients.size());
        resp.setSkippedCompletedCount(skippedCompleted);
        resp.setFilteredCount(filtered);
        return resp;
    }

    private void bindAttachments(ForcedFormDO form, ForcedFormVersionDO version, Long userId, Long submissionId,
                                 List<ForcedFormFieldDefinition> fields, Map<String, Object> answers) {
        for (ForcedFormFieldDefinition field : fields) {
            if (!"attachment".equals(field.getType())) {
                continue;
            }
            List<String> tokens = normalizeTokens(answers.get(field.getKey()));
            if (CollUtil.isEmpty(tokens)) {
                continue;
            }
            if (field.getMaxCount() != null && tokens.size() > field.getMaxCount()) {
                throw exception(ZsjosErrorCodeConstants.FORCED_FORM_ATTACHMENT_INVALID);
            }
            for (String token : tokens) {
                ForcedFormSubmissionFileDO row = submissionFileMapper.selectOne(Wrappers.<ForcedFormSubmissionFileDO>lambdaQuery()
                        .eq(ForcedFormSubmissionFileDO::getUploadToken, token)
                        .eq(ForcedFormSubmissionFileDO::getFormId, form.getId())
                        .eq(ForcedFormSubmissionFileDO::getVersionId, version.getId())
                        .eq(ForcedFormSubmissionFileDO::getUserId, userId)
                        .eq(ForcedFormSubmissionFileDO::getFieldKey, field.getKey())
                        .eq(ForcedFormSubmissionFileDO::getStatus, FILE_TEMPORARY));
                if (row == null) {
                    throw exception(ZsjosErrorCodeConstants.FORCED_FORM_ATTACHMENT_INVALID);
                }
                row.setSubmissionId(submissionId);
                row.setStatus(FILE_BOUND);
                submissionFileMapper.updateById(row);
            }
        }
    }

    private ForcedFormSubmissionRespVO toSubmissionVO(ForcedFormSubmissionDO submission) {
        ForcedFormSubmissionRespVO vo = toBean(submission, ForcedFormSubmissionRespVO.class);
        ForcedFormDO form = formMapper.selectById(submission.getFormId());
        vo.setFormName(form == null ? null : form.getName());
        ForcedFormVersionDO version = submission.getVersionId() == null ? null : versionMapper.selectById(submission.getVersionId());
        vo.setVersion(version == null ? null : version.getVersionNo());
        AdminUserRespDTO user = submission.getUserId() == null ? null : adminUserApi.getUser(submission.getUserId());
        vo.setUserNickname(user == null ? null : user.getNickname());
        return vo;
    }

    private List<ForcedFormSubmissionListRespVO> toSubmissionList(List<ForcedFormSubmissionDO> submissions) {
        if (CollUtil.isEmpty(submissions)) {
            return List.of();
        }
        Map<Long, ForcedFormDO> forms = formMapper.selectList(ForcedFormDO::getId, submissions.stream().map(ForcedFormSubmissionDO::getFormId)
                .filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(ForcedFormDO::getId, item -> item));
        Map<Long, ForcedFormVersionDO> versions = versionMapper.selectList(ForcedFormVersionDO::getId, submissions.stream()
                .map(ForcedFormSubmissionDO::getVersionId).filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(ForcedFormVersionDO::getId, item -> item));
        Map<Long, AdminUserRespDTO> users = convertMap(adminUserApi.getUserList(submissions.stream()
                .map(ForcedFormSubmissionDO::getUserId).filter(Objects::nonNull).distinct().toList()), AdminUserRespDTO::getId);
        return submissions.stream().map(submission -> {
            ForcedFormSubmissionListRespVO vo = new ForcedFormSubmissionListRespVO();
            vo.setId(submission.getId());
            vo.setFormId(submission.getFormId());
            vo.setFormName(Optional.ofNullable(forms.get(submission.getFormId())).map(ForcedFormDO::getName).orElse(null));
            vo.setVersionId(submission.getVersionId());
            vo.setVersion(Optional.ofNullable(versions.get(submission.getVersionId())).map(ForcedFormVersionDO::getVersionNo).orElse(null));
            vo.setUserId(submission.getUserId());
            vo.setUserNickname(Optional.ofNullable(users.get(submission.getUserId())).map(AdminUserRespDTO::getNickname).orElse(null));
            vo.setPlatform(submission.getPlatform());
            vo.setCreateTime(submission.getCreateTime());
            return vo;
        }).toList();
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private static String schemaHash(List<ForcedFormFieldDefinition> fields) {
        return Integer.toHexString(JsonUtils.toJsonString(fields).hashCode());
    }

    private static String newUploadToken() {
        return "ff_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String buildDirectory(Long formId, Long userId) {
        return "zsjos/forced-form/" + formId + "/" + userId;
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("读取上传文件失败", exception);
        }
    }

    private static String normalizeFileName(String fileName) {
        return StrUtil.blankToDefault(fileName, "forced-form-attachment");
    }

    private void validateAttachment(MultipartFile file, ForcedFormFieldDefinition field) {
        if (file == null || file.isEmpty()) {
            throw exception(ZsjosErrorCodeConstants.FORCED_FORM_ATTACHMENT_INVALID);
        }
        if (field.getMaxSizeMb() != null && file.getSize() > field.getMaxSizeMb() * 1024L * 1024L) {
            throw exception(ZsjosErrorCodeConstants.FORCED_FORM_ATTACHMENT_INVALID);
        }
        if (field.getAllowedExtensions() != null && !field.getAllowedExtensions().isEmpty()) {
            String original = file.getOriginalFilename();
            String ext = original == null || !original.contains(".") ? "" : original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            if (!field.getAllowedExtensions().stream().map(value -> value.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet()).contains(ext)) {
                throw exception(ZsjosErrorCodeConstants.FORCED_FORM_ATTACHMENT_INVALID);
            }
        }
    }

    private List<String> normalizeTokens(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().filter(Objects::nonNull).map(String::valueOf).toList();
        }
        if (value instanceof String str && !str.isBlank()) {
            return List.of(str);
        }
        throw exception(ZsjosErrorCodeConstants.FORCED_FORM_ATTACHMENT_INVALID);
    }

    private ForcedFormRuntimeRespVO.FieldVO toRuntimeField(ForcedFormFieldDefinition field) {
        ForcedFormRuntimeRespVO.FieldVO vo = new ForcedFormRuntimeRespVO.FieldVO();
        vo.setKey(field.getKey());
        vo.setType(field.getType());
        vo.setLabel(field.getLabel());
        vo.setRequired(field.getRequired());
        vo.setDictType(field.getDictType());
        vo.setMaxLength(field.getMaxLength());
        vo.setMaxCount(field.getMaxCount());
        vo.setMaxSizeMb(field.getMaxSizeMb());
        vo.setAllowedExtensions(field.getAllowedExtensions());
        if (Set.of("radio", "multi-select").contains(field.getType()) && field.getDictType() != null) {
            vo.setOptions(field.getDictType() == null ? List.of() : fieldOptions(field.getDictType()));
        } else {
            vo.setOptions(List.of());
        }
        return vo;
    }

    private List<ForcedFormRuntimeRespVO.OptionVO> fieldOptions(String dictType) {
        List<DictDataRespDTO> dictDataList = dictDataApi.getDictDataList(dictType);
        return dictDataList.stream().map(item -> {
            ForcedFormRuntimeRespVO.OptionVO vo = new ForcedFormRuntimeRespVO.OptionVO();
            vo.setLabel(item.getLabel());
            vo.setValue(item.getValue());
            return vo;
        }).toList();
    }

    private ForcedFormSubmissionDO requireSubmission(Long id) {
        ForcedFormSubmissionDO submission = submissionMapper.selectById(id);
        if (submission == null) {
            throw exception(ZsjosErrorCodeConstants.FORCED_FORM_NOT_EXISTS);
        }
        return submission;
    }

    private ForcedFormSubmissionPageReqVO copyPageSizeNone(ForcedFormSubmissionPageReqVO req) {
        ForcedFormSubmissionPageReqVO copy = new ForcedFormSubmissionPageReqVO();
        copy.setPageNo(1);
        copy.setPageSize(cn.iocoder.yudao.framework.common.pojo.PageParam.PAGE_SIZE_NONE);
        copy.setFormId(req.getFormId());
        copy.setUserId(req.getUserId());
        copy.setPlatform(req.getPlatform());
        return copy;
    }

    private static final class RecipientCandidate {
        private final Long userId;
        private final String nickname;
        private final String deptName;
        private final String postNames;

        private RecipientCandidate(Long userId, String nickname, String deptName, String postNames) {
            this.userId = userId;
            this.nickname = nickname;
            this.deptName = deptName;
            this.postNames = postNames;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof RecipientCandidate other && Objects.equals(userId, other.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(userId);
        }
    }
}
