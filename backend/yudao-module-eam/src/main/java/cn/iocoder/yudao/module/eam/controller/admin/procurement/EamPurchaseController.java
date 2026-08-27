package cn.iocoder.yudao.module.eam.controller.admin.procurement;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.eam.controller.admin.procurement.vo.*;
import cn.iocoder.yudao.module.eam.service.procurement.EamPurchaseService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/eam/purchase")
@Validated
public class EamPurchaseController {
    @Resource private EamPurchaseService purchaseService;

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('eam:purchase:create')")
    public CommonResult<Long> create(@Valid @RequestBody EamPurchaseCreateReqVO reqVO) {
        return success(purchaseService.createPurchase(reqVO, SecurityFrameworkUtils.getLoginUserId()));
    }
    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('eam:purchase:query')")
    public CommonResult<EamPurchaseRespVO> get(@RequestParam Long id) { return success(purchaseService.getPurchase(id)); }
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('eam:purchase:query')")
    public CommonResult<List<EamPurchaseRespVO>> list() { return success(purchaseService.getPurchaseList()); }
    @PostMapping("/{id}/receive")
    @PreAuthorize("@ss.hasPermission('eam:purchase:receive')")
    public CommonResult<Long> receive(@PathVariable Long id, @Valid @RequestBody EamReceiptCreateReqVO reqVO) {
        return success(purchaseService.receive(id, reqVO));
    }
    @PostMapping("/{id}/supplier-return")
    @PreAuthorize("@ss.hasPermission('eam:purchase:return')")
    public CommonResult<Long> supplierReturn(@PathVariable Long id, @Valid @RequestBody EamReceiptCreateReqVO reqVO) {
        return success(purchaseService.returnToSupplier(id, reqVO));
    }
    @PutMapping("/{id}/short-close")
    @PreAuthorize("@ss.hasPermission('eam:purchase:close')")
    public CommonResult<Boolean> shortClose(@PathVariable Long id, @Valid @RequestBody EamShortCloseReqVO reqVO) {
        purchaseService.shortClose(id, reqVO);
        return success(true);
    }
    @PostMapping("/{id}/expense")
    @PreAuthorize("@ss.hasPermission('eam:purchase:expense')")
    public CommonResult<Boolean> expense(@PathVariable Long id, @Valid @RequestBody EamExpenseSubmitReqVO reqVO) {
        purchaseService.submitExpense(id, reqVO);
        return success(true);
    }
}
