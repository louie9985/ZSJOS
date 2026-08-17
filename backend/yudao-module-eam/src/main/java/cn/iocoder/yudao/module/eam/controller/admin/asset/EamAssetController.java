package cn.iocoder.yudao.module.eam.controller.admin.asset;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import cn.iocoder.yudao.module.eam.controller.admin.asset.vo.EamAssetChangeLogRespVO;
import cn.iocoder.yudao.module.eam.controller.admin.asset.vo.EamAssetImportPreviewRespVO;
import cn.iocoder.yudao.module.eam.controller.admin.asset.vo.EamAssetPageReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.asset.vo.EamAssetRespVO;
import cn.iocoder.yudao.module.eam.controller.admin.asset.vo.EamAssetSaveReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetChangeLogDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.asset.EamAssetDO;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryDO;
import cn.iocoder.yudao.module.eam.service.asset.EamAssetChangeLogService;
import cn.iocoder.yudao.module.eam.service.asset.EamAssetService;
import cn.iocoder.yudao.module.eam.service.asset.EamAssetLedgerImportService;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryService;
import cn.iocoder.yudao.module.eam.util.EamQrCodeUtil;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;

@Tag(name = "管理后台 - EAM 资产")
@RestController
@RequestMapping("/eam/asset")
@Validated
public class EamAssetController {

    @Resource
    private EamAssetService assetService;
    @Resource
    private EamAssetLedgerImportService ledgerImportService;
    @Resource
    private EamAssetChangeLogService changeLogService;
    @Resource
    private EamCategoryService categoryService;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private DeptApi deptApi;

    @PostMapping("/create")
    @Operation(summary = "创建资产")
    @PreAuthorize("@ss.hasPermission('eam:asset:create')")
    public CommonResult<Long> createAsset(@Valid @RequestBody EamAssetSaveReqVO reqVO) {
        return success(assetService.createAsset(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新资产")
    @PreAuthorize("@ss.hasPermission('eam:asset:update')")
    public CommonResult<Boolean> updateAsset(@Valid @RequestBody EamAssetSaveReqVO reqVO) {
        assetService.updateAsset(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除资产")
    @Parameter(name = "id", description = "资产编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('eam:asset:delete')")
    public CommonResult<Boolean> deleteAsset(@RequestParam("id") Long id) {
        assetService.deleteAsset(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得资产")
    @Parameter(name = "id", description = "资产编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('eam:asset:query')")
    public CommonResult<EamAssetRespVO> getAsset(@RequestParam("id") Long id) {
        EamAssetDO asset = assetService.getAsset(id);
        if (asset == null) {
            return success(null);
        }
        return success(buildAssetVOList(List.of(asset)).get(0));
    }

    @GetMapping("/page")
    @Operation(summary = "获得资产分页")
    @PreAuthorize("@ss.hasPermission('eam:asset:query')")
    public CommonResult<PageResult<EamAssetRespVO>> getAssetPage(@Valid EamAssetPageReqVO reqVO) {
        PageResult<EamAssetDO> pageResult = assetService.getAssetPage(reqVO);
        return success(new PageResult<>(buildAssetVOList(pageResult.getList()), pageResult.getTotal()));
    }

    @GetMapping("/change-log")
    @Operation(summary = "获得资产变更时间线")
    @Parameter(name = "assetId", description = "资产编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('eam:asset:query')")
    public CommonResult<List<EamAssetChangeLogRespVO>> getChangeLogList(
            @RequestParam("assetId") Long assetId) {
        List<EamAssetChangeLogDO> logs = changeLogService.getChangeLogListByAssetId(assetId);
        List<EamAssetChangeLogRespVO> result = BeanUtils.toBean(logs, EamAssetChangeLogRespVO.class);
        // 补齐操作人名称，前端时间线直接展示
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(
                convertSet(logs, EamAssetChangeLogDO::getOperatorId));
        result.forEach(vo -> {
            AdminUserRespDTO user = userMap.get(vo.getOperatorId());
            vo.setOperatorName(user != null ? user.getNickname() : null);
        });
        return success(result);
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出资产 Excel")
    @PreAuthorize("@ss.hasPermission('eam:asset:export')")
    @ApiAccessLog(operateName = "导出资产")
    public void exportAssetExcel(@Valid EamAssetPageReqVO reqVO, HttpServletResponse response)
            throws IOException {
        reqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<EamAssetDO> list = assetService.getAssetPage(reqVO).getList();
        ExcelUtils.write(response, "资产台账.xlsx", "资产列表",
                EamAssetRespVO.class, buildAssetVOList(list));
    }

    @GetMapping("/qrcode")
    @Operation(summary = "生成资产二维码", description = "返回 PNG 图片流，内容为资产业务编号")
    @Parameter(name = "id", description = "资产编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('eam:asset:qrcode')")
    public ResponseEntity<byte[]> getAssetQrCode(@RequestParam("id") Long id,
                                                 @RequestParam(value = "size", required = false) Integer size) {
        byte[] png = EamQrCodeUtil.generatePng(assetService.buildQrContent(id), size);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(png);
    }

    @GetMapping("/get-import-template")
    @Operation(summary = "获得中世健资产台账导入模板")
    @PreAuthorize("@ss.hasPermission('eam:asset:import')")
    public void getImportTemplate(HttpServletResponse response) throws IOException {
        ClassPathResource resource = new ClassPathResource("eam/eam-asset-ledger-template.xlsx");
        response.addHeader("Content-Disposition", "attachment;filename="
                + HttpUtils.encodeUtf8("中世健资产台账导入模板.xlsx"));
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        try (var input = resource.getInputStream()) {
            input.transferTo(response.getOutputStream());
        }
    }

    @PostMapping("/import/preview")
    @Operation(summary = "预检中世健资产台账", description = "只读取在岗资产初始申报表，不写入数据库")
    @PreAuthorize("@ss.hasPermission('eam:asset:import')")
    public CommonResult<EamAssetImportPreviewRespVO> previewLedgerImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "updateExisting", defaultValue = "false") boolean updateExisting)
            throws IOException {
        return success(ledgerImportService.preview(file.getBytes(), file.getOriginalFilename(), updateExisting));
    }

    @PostMapping("/import/commit")
    @Operation(summary = "提交中世健资产台账导入", description = "按文件摘要和 Excel 行号幂等导入")
    @PreAuthorize("@ss.hasPermission('eam:asset:import')")
    @ApiAccessLog(operateName = "导入中世健资产台账")
    public CommonResult<EamAssetImportPreviewRespVO> commitLedgerImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "updateExisting", defaultValue = "false") boolean updateExisting)
            throws IOException {
        return success(ledgerImportService.commit(file.getBytes(), file.getOriginalFilename(), updateExisting));
    }

    /**
     * 批量补齐分类、部门、使用人名称，避免前端逐行回查
     */
    private List<EamAssetRespVO> buildAssetVOList(List<EamAssetDO> list) {
        List<EamAssetRespVO> result = BeanUtils.toBean(list, EamAssetRespVO.class);
        if (result.isEmpty()) {
            return result;
        }
        Map<Long, String> categoryNameMap = categoryService.getCategoryList().stream()
                .collect(Collectors.toMap(EamCategoryDO::getId, EamCategoryDO::getName, (a, b) -> a));
        Map<Long, AdminUserRespDTO> userMap =
                adminUserApi.getUserMap(convertSet(list, EamAssetDO::getUseUserId));
        Map<Long, DeptRespDTO> deptMap =
                deptApi.getDeptMap(convertSet(list, EamAssetDO::getUseDeptId));

        result.forEach(vo -> {
            vo.setCategoryName(categoryNameMap.get(vo.getCategoryId()));
            AdminUserRespDTO user = userMap.get(vo.getUseUserId());
            vo.setUseUserName(user != null ? user.getNickname() : null);
            DeptRespDTO dept = deptMap.get(vo.getUseDeptId());
            vo.setUseDeptName(dept != null ? dept.getName() : null);
        });
        return result;
    }

}
