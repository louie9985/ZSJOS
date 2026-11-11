package cn.iocoder.yudao.module.zsjos.controller.admin.examcollection;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo.ExamCollectionExportVO.*;
import cn.iocoder.yudao.module.zsjos.service.examcollection.ExamCollectionExportService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.file.Files;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@Validated
@RequestMapping("/zsjos/exam-collection/{taskId}/material-export")
public class ExamCollectionExportController {
    @Resource private ExamCollectionExportService service;
    @Resource private cn.iocoder.yudao.module.zsjos.service.export.ExportTaskService exportTasks;
    @PostMapping("/tasks")
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:query')")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    public CommonResult<Long> createTask(@PathVariable Long taskId, @Valid @RequestBody Request request) {
        var filter = new cn.iocoder.yudao.module.zsjos.service.export.provider.ExamCollectionExportTypeProvider.Filter(taskId, request);
        return success(exportTasks.create(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId(),
                cn.iocoder.yudao.module.zsjos.service.export.provider.ExamCollectionExportTypeProvider.TYPE,
                cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(filter)));
    }
    @GetMapping("/settings")
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:query')")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    public CommonResult<Settings> settings(@PathVariable Long taskId, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return success(service.settings(taskId));
    }
    @PutMapping("/settings")
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:query')")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    public CommonResult<Settings> save(@PathVariable Long taskId, @Valid @RequestBody Settings request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return success(service.save(taskId, request));
    }
    @PostMapping("/preview")
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:query')")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    public CommonResult<Preview> preview(@PathVariable Long taskId, @Valid @RequestBody Request request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return success(service.preview(taskId, request));
    }
    @PostMapping("/download")
    @PreAuthorize("@ss.hasPermission('zsjos:exam-collection:query')")
    @ApiAccessLog(requestEnable=false, responseEnable=false)
    public void download(@PathVariable Long taskId, @Valid @RequestBody Request request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        var output = service.download(taskId, request);
        try {
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" +
                    org.springframework.web.util.UriUtils.encode(service.downloadName(taskId), java.nio.charset.StandardCharsets.UTF_8));
            response.setContentLengthLong(Files.size(output));
            Files.copy(output, response.getOutputStream());
        } finally { Files.deleteIfExists(output); }
    }
}
