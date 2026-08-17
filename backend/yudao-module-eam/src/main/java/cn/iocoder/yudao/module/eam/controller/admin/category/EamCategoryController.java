package cn.iocoder.yudao.module.eam.controller.admin.category;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.http.HttpUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.eam.controller.admin.category.vo.EamCategoryRespVO;
import cn.iocoder.yudao.module.eam.controller.admin.category.vo.EamCategoryImportRespVO;
import cn.iocoder.yudao.module.eam.controller.admin.category.vo.EamCategorySaveReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryDO;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryService;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
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
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - EAM 资产分类")
@RestController
@RequestMapping("/eam/category")
@Validated
public class EamCategoryController {

    @Resource
    private EamCategoryService categoryService;
    @Resource
    private EamCategoryImportService categoryImportService;

    @PostMapping("/create")
    @Operation(summary = "创建资产分类")
    @PreAuthorize("@ss.hasPermission('eam:category:create')")
    public CommonResult<Long> createCategory(@Valid @RequestBody EamCategorySaveReqVO reqVO) {
        return success(categoryService.createCategory(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新资产分类")
    @PreAuthorize("@ss.hasPermission('eam:category:update')")
    public CommonResult<Boolean> updateCategory(@Valid @RequestBody EamCategorySaveReqVO reqVO) {
        categoryService.updateCategory(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除资产分类")
    @Parameter(name = "id", description = "分类编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('eam:category:delete')")
    public CommonResult<Boolean> deleteCategory(@RequestParam("id") Long id) {
        categoryService.deleteCategory(id);
        return success(true);
    }

    @GetMapping("/list")
    @Operation(summary = "获得资产分类列表")
    @PreAuthorize("@ss.hasPermission('eam:category:query')")
    public CommonResult<List<EamCategoryRespVO>> getCategoryList() {
        List<EamCategoryDO> list = categoryService.getCategoryList();
        return success(BeanUtils.toBean(list, EamCategoryRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得资产分类")
    @Parameter(name = "id", description = "分类编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('eam:category:query')")
    public CommonResult<EamCategoryRespVO> getCategory(@RequestParam("id") Long id) {
        EamCategoryDO category = categoryService.getCategory(id);
        return success(BeanUtils.toBean(category, EamCategoryRespVO.class));
    }

    @GetMapping("/get-import-template")
    @Operation(summary = "下载资产分类配置导入模板")
    @PreAuthorize("@ss.hasPermission('eam:category:import')")
    public void getImportTemplate(HttpServletResponse response) throws IOException {
        ClassPathResource resource = new ClassPathResource("eam/eam-category-config-template.xlsx");
        response.addHeader("Content-Disposition", "attachment;filename="
                + HttpUtils.encodeUtf8("中世健EAM分类配置模板.xlsx"));
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        try (var input = resource.getInputStream()) {
            input.transferTo(response.getOutputStream());
        }
    }

    @PostMapping("/import/preview")
    @Operation(summary = "预检资产分类配置")
    @PreAuthorize("@ss.hasPermission('eam:category:import')")
    public CommonResult<EamCategoryImportRespVO> previewImport(@RequestParam("file") MultipartFile file)
            throws IOException {
        return success(categoryImportService.preview(file.getBytes()));
    }

    @PostMapping("/import/commit")
    @Operation(summary = "提交资产分类配置导入")
    @PreAuthorize("@ss.hasPermission('eam:category:import')")
    public CommonResult<EamCategoryImportRespVO> commitImport(@RequestParam("file") MultipartFile file)
            throws IOException {
        return success(categoryImportService.commit(file.getBytes()));
    }

}
