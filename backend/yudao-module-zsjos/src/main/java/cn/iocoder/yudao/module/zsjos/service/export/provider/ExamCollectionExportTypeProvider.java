package cn.iocoder.yudao.module.zsjos.service.export.provider;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo.ExamCollectionExportVO.Request;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.export.ExportTaskDO;
import cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionExportService;
import cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionPermissionProvider;
import cn.iocoder.yudao.module.zsjos.service.export.ExportTypeProvider;
import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoImageService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;
import java.nio.file.Files;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Component
public class ExamCollectionExportTypeProvider implements ExportTypeProvider {
    public static final String TYPE = "exam_collection";
    public record Filter(@NotNull Long taskId, @NotNull @Valid Request request) {}
    @Resource private ExamCollectionExportService exports;
    @Resource private ExamCollectionPermissionProvider permissions;
    @Resource private StudentInfoImageService cipher;
    @Resource private jakarta.validation.Validator validator;

    @Override public String getType() { return TYPE; }
    @Override public String getCreatePermission() { return "zsjos:exam-collection:query"; }
    @Override public boolean privateResult() { return true; }
    @Override public String contentType() { return "application/zip"; }
    @Override public void validateFilter(String json) {
        var filter = JsonUtils.parseObject(json, Filter.class);
        if (filter == null || !validator.validate(filter).isEmpty() || filter.request().getFingerprint() == null)
            throw new IllegalArgumentException("请先预览考试资料导出");
        permissions.check(filter.taskId(), "query", getLoginUserId());
    }
    @Override public void checkDownload(ExportTaskDO task) {
        var filter = JsonUtils.parseObject(task.getFilterJson(), Filter.class);
        permissions.check(filter.taskId(), "query", getLoginUserId());
    }
    @Override public ExportResult generate(ExportTaskDO task) throws Exception {
        var filter = JsonUtils.parseObject(task.getFilterJson(), Filter.class);
        var path = exports.download(filter.taskId(), filter.request());
        try { return new ExportResult(Files.readAllBytes(path), exports.downloadName(filter.taskId()), 0); }
        finally { Files.deleteIfExists(path); }
    }
    @Override public byte[] encodeResult(byte[] content) throws Exception { return cipher.encryptPrivateMaterial(content); }
    @Override public byte[] decodeResult(byte[] content) throws Exception { return cipher.decryptPrivateMaterial(content); }
}
