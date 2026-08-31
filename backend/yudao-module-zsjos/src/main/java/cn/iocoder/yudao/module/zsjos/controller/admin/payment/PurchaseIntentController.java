package cn.iocoder.yudao.module.zsjos.controller.admin.payment;

import cn.iocoder.yudao.module.zsjos.framework.audit.ZsjosAudit;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.PurchaseIntentRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.PurchaseIntentSaveDraftReqVO;
import cn.iocoder.yudao.module.zsjos.service.payment.PurchaseIntentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "ZSJOS - 订单购买草稿与支付")
@RestController
@RequestMapping("/zsjos/purchase-intent")
@Validated
public class PurchaseIntentController {
    @Resource private PurchaseIntentService service;

    @PostMapping("/current")
    @ZsjosAudit(mode = ZsjosAudit.Mode.READ_ONLY)
    @Operation(summary = "查询当前购买草稿")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:create')")
    public CommonResult<PurchaseIntentRespVO> current(@RequestBody PurchaseIntentSaveDraftReqVO request) {
        return success(service.current(request, WebFrameworkUtils.getLoginUserId()));
    }

    @PostMapping("/save-draft")
    @Operation(summary = "保存订单购买草稿")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:create')")
    public CommonResult<PurchaseIntentRespVO> saveDraft(@Valid @RequestBody PurchaseIntentSaveDraftReqVO request) {
        return success(service.saveDraft(request, WebFrameworkUtils.getLoginUserId()));
    }

    @PostMapping("/save-and-create-payment-link")
    @Operation(summary = "保存草稿并生成支付链接")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:create')")
    public CommonResult<PurchaseIntentRespVO> createPaymentLink(@Valid @RequestBody PurchaseIntentSaveDraftReqVO request) {
        return success(service.createPaymentLink(request, WebFrameworkUtils.getLoginUserId()));
    }

    @PostMapping("/{id}/refresh-payment")
    @Operation(summary = "刷新支付草稿状态")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:create')")
    public CommonResult<PurchaseIntentRespVO> refresh(@PathVariable Long id) {
        return success(service.refreshPayment(id, WebFrameworkUtils.getLoginUserId()));
    }
}
