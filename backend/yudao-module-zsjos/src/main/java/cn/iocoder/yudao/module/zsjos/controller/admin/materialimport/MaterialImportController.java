package cn.iocoder.yudao.module.zsjos.controller.admin.materialimport;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.materialimport.vo.MaterialImportCommitReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.materialimport.vo.MaterialImportPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.materialimport.vo.MaterialImportRespVO;
import cn.iocoder.yudao.module.zsjos.service.material.MaterialImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.MATERIAL_IMPORT_FILE_TOO_LARGE;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.MATERIAL_IMPORT_INVALID;

@Tag(name = "管理后台 - 素材 Excel 导入")
@RestController
@RequestMapping("/zsjos/material-import")
@Validated
public class MaterialImportController {

    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    @Resource private MaterialImportService importService;

    @GetMapping("/template")
    @Operation(summary = "下载当前素材模板的 Excel 导入文件")
    @PreAuthorize("@ss.hasPermission('zsjos:material-import:download')")
    public void downloadTemplate(@RequestParam("materialTypeId") @NotNull Long materialTypeId,
                                 HttpServletResponse response) throws IOException {
        write(response, importService.buildTemplate(materialTypeId));
    }

    @PostMapping("/preview")
    @Operation(summary = "预检素材 Excel 导入")
    @PreAuthorize("@ss.hasPermission('zsjos:material-import:preview')")
    public CommonResult<MaterialImportRespVO> preview(
            @RequestParam("materialTypeId") @NotNull Long materialTypeId,
            @RequestParam("idempotencyKey") @NotBlank @Size(max = 128) String idempotencyKey,
            @RequestPart("file") MultipartFile file) throws IOException {
        validateFile(file);
        return success(importService.preview(materialTypeId, file.getOriginalFilename(), file.getBytes(),
                idempotencyKey, getLoginUserId()));
    }

    @PostMapping("/{id}/commit")
    @Operation(summary = "确认素材导入")
    @PreAuthorize("@ss.hasPermission('zsjos:material-import:commit')")
    public CommonResult<Boolean> commit(@PathVariable("id") Long id,
                                        @Valid @RequestBody MaterialImportCommitReqVO request) {
        importService.commit(id, request.getExpectedVersion(), getLoginUserId());
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "获得素材导入批次分页")
    @PreAuthorize("@ss.hasPermission('zsjos:material-import:query')")
    public CommonResult<PageResult<MaterialImportRespVO>> getPage(@Valid MaterialImportPageReqVO request) {
        return success(importService.getPage(request, getLoginUserId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获得素材导入批次详情")
    @PreAuthorize("@ss.hasPermission('zsjos:material-import:query')")
    public CommonResult<MaterialImportRespVO> get(@PathVariable("id") Long id) {
        return success(importService.get(id, getLoginUserId()));
    }

    @GetMapping("/{id}/error-report")
    @Operation(summary = "下载素材导入错误报告")
    @PreAuthorize("@ss.hasPermission('zsjos:material-import:error-download')")
    public void downloadErrorReport(@PathVariable("id") Long id, HttpServletResponse response) throws IOException {
        write(response, importService.buildErrorReport(id, getLoginUserId()));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw exception(MATERIAL_IMPORT_INVALID, "导入文件为空");
        if (file.getSize() > MAX_FILE_SIZE) throw exception(MATERIAL_IMPORT_FILE_TOO_LARGE);
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) {
            throw exception(MATERIAL_IMPORT_INVALID, "只支持 .xlsx 文件");
        }
    }

    private void write(HttpServletResponse response, MaterialImportService.WorkbookFile file) throws IOException {
        response.addHeader("Content-Disposition", "attachment;filename=" + HttpUtils.encodeUtf8(file.fileName()));
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=UTF-8");
        response.getOutputStream().write(file.content());
    }
}
