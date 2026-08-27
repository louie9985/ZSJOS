package cn.iocoder.yudao.module.eam.controller.admin.employeeasset;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.eam.controller.admin.employeeasset.vo.*;
import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.EamReturnInspectReqVO;
import cn.iocoder.yudao.module.eam.service.employeeasset.EamEmployeeAssetService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/eam/employee-asset")
public class EamEmployeeAssetController {
    @Resource private EamEmployeeAssetService employeeAssetService;

    @GetMapping("/get-by-employee")
    @PreAuthorize("@ss.hasPermission('eam:employee-asset:query')")
    public CommonResult<EamEmployeeAssetSummaryRespVO> getByEmployee(@RequestParam Long employeeId) {
        return success(employeeAssetService.getByEmployeeId(employeeId));
    }
    @GetMapping("/task/get")
    @PreAuthorize("@ss.hasPermission('eam:employee-asset:task')")
    public CommonResult<EamEmployeeAssetTaskRespVO> getTask(@RequestParam Long id) {
        return success(employeeAssetService.getTask(id));
    }
    @PostMapping("/task/{id}/provisioning")
    @PreAuthorize("@ss.hasPermission('eam:employee-asset:task')")
    public CommonResult<Boolean> submitProvisioning(@PathVariable Long id,
                                                     @Valid @RequestBody EamEmployeeAssetTaskSubmitReqVO reqVO) {
        employeeAssetService.submitProvisioning(id, reqVO, SecurityFrameworkUtils.getLoginUserId());
        return success(true);
    }
    @PostMapping("/task/{id}/review")
    @PreAuthorize("@ss.hasPermission('eam:employee-asset:task')")
    public CommonResult<Boolean> submitReview(@PathVariable Long id,
                                               @Valid @RequestBody EamEmployeeAssetTaskActionReqVO reqVO) {
        employeeAssetService.submitReview(id, reqVO);
        return success(true);
    }
    @PutMapping("/holding/{id}/inspect-return")
    @PreAuthorize("@ss.hasPermission('eam:employee-asset:inspect')")
    public CommonResult<Boolean> inspectReturn(@PathVariable Long id,
                                                @Valid @RequestBody EamReturnInspectReqVO reqVO) {
        employeeAssetService.inspectReturn(id, reqVO);
        return success(true);
    }
}
