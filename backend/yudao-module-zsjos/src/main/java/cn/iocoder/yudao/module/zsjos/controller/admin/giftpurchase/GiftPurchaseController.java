package cn.iocoder.yudao.module.zsjos.controller.admin.giftpurchase;

import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.giftpurchase.vo.*;
import cn.iocoder.yudao.module.zsjos.service.giftpurchase.GiftPurchaseService;
import cn.iocoder.yudao.module.zsjos.service.gift.GiftConfigService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台-礼品采购")
@RestController
@RequestMapping("/zsjos/gift-purchase")
@Validated
public class GiftPurchaseController {
    @Resource private GiftPurchaseService service;
    @Resource private GiftConfigService giftConfigService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('zsjos:gift-purchase:query')")
    public CommonResult<PageResult<GiftPurchaseRespVO>> page(@Valid GiftPurchasePageReqVO req) {
        return success(service.page(req));
    }

    @GetMapping("/export-excel")
    @PreAuthorize("@ss.hasPermission('zsjos:gift-purchase:export')")
    public void exportExcel(@Valid GiftPurchasePageReqVO req, jakarta.servlet.http.HttpServletResponse response)
            throws java.io.IOException {
        byte[] workbook = service.exportExcel(req);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=gift-purchases.xlsx");
        response.getOutputStream().write(workbook);
    }

    @PutMapping("/batch-purchase")
    @PreAuthorize("@ss.hasPermission('zsjos:gift-purchase:batch-purchase')")
    public CommonResult<GiftPurchaseBatchRespVO> batchPurchase(@Valid @RequestBody GiftPurchaseBatchReqVO req) {
        return success(service.batchPurchase(req));
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:gift-purchase:detail','zsjos:gift-purchase:update')")
    public CommonResult<GiftPurchaseRespVO> get(@RequestParam Long id) { return success(service.get(id)); }

    @GetMapping("/operations")
    @PreAuthorize("@ss.hasPermission('zsjos:gift-purchase:detail')")
    public CommonResult<List<GiftPurchaseOperationRespVO>> operations(@RequestParam Long id) {
        return success(service.operations(id));
    }

    public record GiftOption(Long id, Long parentId, String name, String code, Integer status) {}

    @GetMapping("/gift-options")
    @PreAuthorize("@ss.hasAnyPermissions('zsjos:gift-purchase:create','zsjos:gift-purchase:update')")
    public CommonResult<List<GiftOption>> giftOptions() {
        return success(giftConfigService.listFlat(null).stream().map(g -> new GiftOption(
                g.getId(), g.getParentId(), g.getName(), g.getCode(), g.getStatus())).toList());
    }

    @PostMapping("/create")
    @PreAuthorize("@ss.hasPermission('zsjos:gift-purchase:create')")
    public CommonResult<Long> create(@Valid @RequestBody GiftPurchaseSaveReqVO req) {
        return success(service.create(req));
    }

    @PutMapping("/update")
    @PreAuthorize("@ss.hasPermission('zsjos:gift-purchase:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody GiftPurchaseSaveReqVO req) {
        if (req.getId() == null) throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                cn.iocoder.yudao.module.zsjos.enums.GiftPurchaseErrorCodes.NOT_FOUND);
        service.update(req); return success(true);
    }

    @PutMapping("/purchase")
    @PreAuthorize("@ss.hasPermission('zsjos:gift-purchase:update')")
    public CommonResult<Boolean> purchase(@RequestParam @NotNull Long id) {
        service.setPurchased(id, true); return success(true);
    }

    @PutMapping("/unpurchase")
    @PreAuthorize("@ss.hasPermission('zsjos:gift-purchase:update')")
    public CommonResult<Boolean> unpurchase(@RequestParam @NotNull Long id) {
        service.setPurchased(id, false); return success(true);
    }

    @DeleteMapping("/delete")
    @PreAuthorize("@ss.hasPermission('zsjos:gift-purchase:delete')")
    public CommonResult<Boolean> delete(@RequestParam Long id) { service.delete(id); return success(true); }
}
