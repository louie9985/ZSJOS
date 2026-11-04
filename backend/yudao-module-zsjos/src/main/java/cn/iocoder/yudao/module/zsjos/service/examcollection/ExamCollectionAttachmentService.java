package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Image;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.examcollection.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.examcollection.ExamCollectionAttachmentMapper;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoAttachmentValidator;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoImageService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.*;
import static cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionConstants.SESSION_INVALID;

@Service
public class ExamCollectionAttachmentService {
    @Resource private ExamCollectionPublicService service;
    @Resource private ExamCollectionAttachmentMapper attachments;
    @Resource private FileApi files;
    @Resource private StudentInfoImageService cipher;

    @Transactional(rollbackFor=Exception.class)
    public Image upload(String token, String session, String fieldKey, String side, MultipartFile upload) {
        return service.withSession(token, session, true, (task, submission) -> {
            var field = service.definitions(task).stream().filter(f -> fieldKey.equals(f.getKey()) && "images".equals(f.getType()))
                    .findFirst().orElseThrow(() -> exception(FIELD_INVALID, "未选择的材料字段"));
            boolean identity = "identity_images".equals(fieldKey);
            if (identity ? !("front".equals(side) || "back".equals(side)) : side != null && !side.isEmpty())
                throw exception(FIELD_INVALID, field.getLabel());
            var existing = attachments.selectList(new LambdaQueryWrapperX<ExamCollectionAttachmentDO>()
                    .eq(ExamCollectionAttachmentDO::getSubmissionId, submission.getId()).eq(ExamCollectionAttachmentDO::getFieldKey, fieldKey));
            if (existing.size() >= (identity ? 2 : field.getMaxImages())
                    || identity && existing.stream().anyMatch(f -> side.equals(f.getSide()))) throw exception(FIELD_INVALID, "材料数量");
            if (upload.isEmpty()) throw exception(ATTACHMENT_EMPTY);
            if (upload.getSize() > field.getMaxSizeMb() * 1024L * 1024) throw exception(ATTACHMENT_TOO_LARGE, field.getMaxSizeMb());
            try {
                byte[] content = upload.getBytes();
                String type = StudentInfoAttachmentValidator.validate(content, upload.getOriginalFilename(), field.getAllowedExtensions());
                var stored = files.createFileInfo(cipher.encryptPrivateMaterial(content), UUID.randomUUID() + ".bin",
                        "exam-collection-private/" + task.getId() + "/" + submission.getId(), "application/octet-stream");
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) try { files.deleteFileIfExists(stored.getId()); } catch (Exception ignored) { }
                    }
                });
                var row = new ExamCollectionAttachmentDO(); row.setTenantId(task.getTenantId()); row.setTaskId(task.getId());
                row.setSubmissionId(submission.getId()); row.setFieldKey(fieldKey); row.setSide(identity ? side : "");
                row.setInfraFileId(stored.getId()); row.setFileSize((long) content.length); row.setContentType(type);
                row.setOriginalName(safeName(upload.getOriginalFilename())); attachments.insert(row); return project(row);
            } catch (cn.iocoder.yudao.framework.common.exception.ServiceException error) { throw error; }
            catch (Exception error) { throw exception(FIELD_INVALID, "材料上传失败，请重试"); }
        });
    }
    @Transactional(rollbackFor=Exception.class)
    public void remove(String token, String session, Long id) {
        service.withSession(token, session, true, (task, submission) -> {
            require(task, submission, id);
            // Keep encrypted storage for controlled orphan retention; immutable submissions never use this path.
            attachments.deleteById(id); return null;
        });
    }
    @Transactional(rollbackFor=Exception.class)
    public byte[] read(String token, String session, Long id) {
        return service.withSession(token, session, true, (task, submission) -> {
            var row = require(task, submission, id);
            try { return cipher.decryptPrivateMaterial(files.getFileContent(row.getInfraFileId())); }
            catch (Exception error) { throw exception(FIELD_INVALID, "材料读取失败，请重试"); }
        });
    }
    private ExamCollectionAttachmentDO require(ExamCollectionTaskDO task, ExamCollectionSubmissionDO submission, Long id) {
        var row = attachments.selectById(id);
        if (row == null || !Objects.equals(row.getTenantId(), task.getTenantId())
                || !Objects.equals(row.getTaskId(), task.getId()) || !Objects.equals(row.getSubmissionId(), submission.getId()))
            throw exception(SESSION_INVALID);
        return row;
    }
    static String safeName(String original) {
        String name = original == null ? "材料" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
        if (name.isBlank()) name = "材料";
        return name.length() > 512 ? name.substring(0, 512) : name;
    }
    private Image project(ExamCollectionAttachmentDO row) {
        var result = new Image(); result.setId(row.getId()); result.setFieldKey(row.getFieldKey()); result.setSide(row.getSide());
        result.setName(row.getOriginalName()); result.setContentType(row.getContentType()); result.setSize(row.getFileSize()); return result;
    }
}
