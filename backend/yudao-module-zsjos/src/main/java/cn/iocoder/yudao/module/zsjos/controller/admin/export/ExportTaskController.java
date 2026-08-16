package cn.iocoder.yudao.module.zsjos.controller.admin.export;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.export.vo.ExportTaskCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.export.vo.ExportTaskRespVO;
import cn.iocoder.yudao.module.zsjos.service.export.ExportTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 异步业务导出")
@RestController
@RequestMapping("/zsjos/export-task")
public class ExportTaskController {
    @Resource private ExportTaskService service;

    @PostMapping
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:export:lead','zsjos:export:order','zsjos:export:cashback','zsjos:export:withdrawal','zsjos:export:finance-order')")
    @Operation(summary = "创建异步导出任务")
    public CommonResult<Long> create(@Valid @RequestBody ExportTaskCreateReqVO request) {
        return success(service.create(WebFrameworkUtils.getLoginUserId(), request.getExportType(), request.getFilterJson()));
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('zsjos:export:query')")
    public CommonResult<PageResult<ExportTaskRespVO>> page(@Valid PageParam page,
                                                           @RequestParam(required = false) String exportType) {
        return success(service.getMyPage(WebFrameworkUtils.getLoginUserId(), page, exportType));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@ss.hasPermission('zsjos:export:query')")
    public CommonResult<Boolean> cancel(@PathVariable Long id) {
        service.cancel(WebFrameworkUtils.getLoginUserId(), id);
        return success(true);
    }

    @GetMapping("/{id}/download-url")
    @PreAuthorize("@ss.hasPermission('zsjos:export:query')")
    public CommonResult<String> downloadUrl(@PathVariable Long id) {
        return success(service.getDownloadUrl(WebFrameworkUtils.getLoginUserId(), id));
    }
}
