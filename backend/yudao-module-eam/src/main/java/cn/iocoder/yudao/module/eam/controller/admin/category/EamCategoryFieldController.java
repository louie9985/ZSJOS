package cn.iocoder.yudao.module.eam.controller.admin.category;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.eam.controller.admin.category.vo.EamCategoryFieldRespVO;
import cn.iocoder.yudao.module.eam.controller.admin.category.vo.EamCategoryFieldSaveReqVO;
import cn.iocoder.yudao.module.eam.dal.dataobject.category.EamCategoryFieldDO;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryFieldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
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

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - EAM 分类自定义字段")
@RestController
@RequestMapping("/eam/category-field")
@Validated
public class EamCategoryFieldController {

    @Resource
    private EamCategoryFieldService fieldService;

    @PostMapping("/create")
    @Operation(summary = "创建分类自定义字段")
    @PreAuthorize("@ss.hasPermission('eam:category-field:create')")
    public CommonResult<Long> createField(@Valid @RequestBody EamCategoryFieldSaveReqVO reqVO) {
        return success(fieldService.createField(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新分类自定义字段")
    @PreAuthorize("@ss.hasPermission('eam:category-field:update')")
    public CommonResult<Boolean> updateField(@Valid @RequestBody EamCategoryFieldSaveReqVO reqVO) {
        fieldService.updateField(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除分类自定义字段")
    @Parameter(name = "id", description = "字段编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('eam:category-field:delete')")
    public CommonResult<Boolean> deleteField(@RequestParam("id") Long id) {
        fieldService.deleteField(id);
        return success(true);
    }

    @GetMapping("/list")
    @Operation(summary = "获得分类直接定义的字段列表")
    @Parameter(name = "categoryId", description = "分类编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('eam:category-field:query')")
    public CommonResult<List<EamCategoryFieldRespVO>> getFieldList(
            @RequestParam("categoryId") Long categoryId) {
        List<EamCategoryFieldDO> list = fieldService.getFieldListByCategoryId(categoryId);
        List<EamCategoryFieldRespVO> result = BeanUtils.toBean(list, EamCategoryFieldRespVO.class);
        result.forEach(vo -> vo.setInherited(false));
        return success(result);
    }

    @GetMapping("/effective-list")
    @Operation(summary = "获得分类生效的字段列表（含继承）", description = "资产表单按此列表渲染动态字段")
    @Parameter(name = "categoryId", description = "分类编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('eam:category-field:query')")
    public CommonResult<List<EamCategoryFieldRespVO>> getEffectiveFieldList(
            @RequestParam("categoryId") Long categoryId) {
        List<EamCategoryFieldDO> list = fieldService.getEffectiveFieldList(categoryId);
        List<EamCategoryFieldRespVO> result = BeanUtils.toBean(list, EamCategoryFieldRespVO.class);
        // 字段归属分类与当前分类不一致，即为继承而来，前端据此禁用就地编辑
        result.forEach(vo -> vo.setInherited(!Objects.equals(vo.getCategoryId(), categoryId)));
        return success(result);
    }

}
