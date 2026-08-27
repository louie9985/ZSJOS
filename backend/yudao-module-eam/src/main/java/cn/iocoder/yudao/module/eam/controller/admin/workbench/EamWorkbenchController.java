package cn.iocoder.yudao.module.eam.controller.admin.workbench;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.eam.controller.admin.category.vo.EamCategoryFieldRespVO;
import cn.iocoder.yudao.module.eam.controller.admin.category.vo.EamCategoryRespVO;
import cn.iocoder.yudao.module.eam.controller.admin.employeeasset.vo.EamEmployeeAssetSummaryRespVO;
import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.EamDemandCreateReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.EamDemandItemReqVO;
import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.EamDemandRespVO;
import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.EamStockCandidateRespVO;
import cn.iocoder.yudao.module.eam.controller.admin.repair.vo.EamRepairCreateReqVO;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryFieldService;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryService;
import cn.iocoder.yudao.module.eam.service.employeeasset.EamEmployeeAssetService;
import cn.iocoder.yudao.module.eam.service.procurement.EamDemandService;
import cn.iocoder.yudao.module.eam.service.repair.EamRepairService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.ASSET_NOT_EXISTS;

@RestController
@RequestMapping("/eam/workbench")
public class EamWorkbenchController {

    @Resource
    private EamEmployeeAssetService employeeAssetService;
    @Resource
    private EamDemandService demandService;
    @Resource
    private EamRepairService repairService;
    @Resource
    private EamCategoryService categoryService;
    @Resource
    private EamCategoryFieldService categoryFieldService;

    @GetMapping("/my-assets")
    @PreAuthorize("@ss.hasPermission('eam:workbench:asset:query')")
    public CommonResult<EamEmployeeAssetSummaryRespVO> myAssets() {
        return success(employeeAssetService.getByUserId(SecurityFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/my-demands")
    @PreAuthorize("@ss.hasPermission('eam:workbench:demand:query')")
    public CommonResult<List<EamDemandRespVO>> myDemands() {
        return success(demandService.getMyDemands(SecurityFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/categories")
    @PreAuthorize("@ss.hasPermission('eam:workbench:demand:create')")
    public CommonResult<List<EamCategoryRespVO>> categories() {
        List<EamCategoryRespVO> result = categoryService.getCategoryList().stream().map(category -> {
            EamCategoryRespVO item = BeanUtils.toBean(category, EamCategoryRespVO.class);
            try {
                var policy = categoryService.getEffectivePolicy(category.getId());
                item.setEffectiveDeliveryMode(policy.deliveryMode());
                item.setEffectiveCustodyMode(policy.custodyMode());
            } catch (cn.iocoder.yudao.framework.common.exception.ServiceException ignored) {
                // 历史分类在管理员确认策略前仍可展示，但不能用于新申请。
            }
            return item;
        }).toList();
        return success(result);
    }

    @GetMapping("/category-fields")
    @PreAuthorize("@ss.hasPermission('eam:workbench:demand:create')")
    public CommonResult<List<EamCategoryFieldRespVO>> categoryFields(@RequestParam Long categoryId) {
        return success(BeanUtils.toBean(categoryFieldService.getEffectiveFieldList(categoryId),
                EamCategoryFieldRespVO.class));
    }

    @PostMapping("/stock-candidates")
    @PreAuthorize("@ss.hasPermission('eam:workbench:demand:create')")
    public CommonResult<List<EamStockCandidateRespVO>> stockCandidates(
            @Valid @RequestBody EamDemandItemReqVO reqVO) {
        return success(demandService.previewCandidates(reqVO));
    }

    @PostMapping("/demand")
    @PreAuthorize("@ss.hasPermission('eam:workbench:demand:create')")
    public CommonResult<Long> createDemand(@Valid @RequestBody EamDemandCreateReqVO reqVO) {
        reqVO.setEmployeeId(null);
        return success(demandService.createDemand(reqVO, SecurityFrameworkUtils.getLoginUserId()));
    }

    @PutMapping("/holding/{id}/sign")
    @PreAuthorize("@ss.hasPermission('eam:workbench:asset:sign')")
    public CommonResult<Boolean> sign(@PathVariable Long id) {
        employeeAssetService.sign(id, SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }

    @PutMapping("/holding/{id}/return")
    @PreAuthorize("@ss.hasPermission('eam:workbench:asset:return')")
    public CommonResult<Boolean> applyReturn(@PathVariable Long id,
                                              @RequestParam(required = false) String remark) {
        employeeAssetService.applyReturn(id, SecurityFrameworkUtils.getLoginUserId(), remark);
        return success(true);
    }

    @PostMapping("/repair")
    @PreAuthorize("@ss.hasPermission('eam:workbench:asset:repair')")
    public CommonResult<Long> repair(@Valid @RequestBody EamRepairCreateReqVO reqVO) {
        EamEmployeeAssetSummaryRespVO mine = employeeAssetService
                .getByUserId(SecurityFrameworkUtils.getLoginUserId());
        if (mine.getItems().stream().noneMatch(item -> reqVO.getAssetId().equals(item.getAssetId()))) {
            throw exception(ASSET_NOT_EXISTS);
        }
        return success(repairService.createRepair(reqVO));
    }

}
