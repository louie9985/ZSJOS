package cn.iocoder.yudao.module.zsjos.controller.admin.payment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.PaymentRefundApplyReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.PaymentRefundRespVO;
import cn.iocoder.yudao.module.zsjos.service.payment.PaymentRefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "ZSJOS - 支付退款")
@RestController
@RequestMapping("/zsjos/payment-refund")
@Validated
public class PaymentRefundController {
    @Resource private PaymentRefundService service;

    @PostMapping("/apply")
    @Operation(summary = "申请退款")
    @PreAuthorize("@ss.hasPermission('zsjos:sales-order:refund-apply')")
    public CommonResult<PaymentRefundRespVO> apply(@Valid @RequestBody PaymentRefundApplyReqVO req) {
        return success(service.apply(req, WebFrameworkUtils.getLoginUserId()));
    }

    @PostMapping("/direct")
    @Operation(summary = "财务直接退款")
    @PreAuthorize("@ss.hasPermission('zsjos:payment-refund:direct')")
    public CommonResult<PaymentRefundRespVO> direct(@Valid @RequestBody PaymentRefundApplyReqVO req) {
        return success(service.direct(req, WebFrameworkUtils.getLoginUserId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('zsjos:payment-refund:read')")
    public CommonResult<PaymentRefundRespVO> get(@PathVariable Long id) { return success(service.get(id)); }

    @PostMapping("/{id}/refresh")
    @PreAuthorize("@ss.hasPermission('zsjos:payment-refund:refresh')")
    public CommonResult<PaymentRefundRespVO> refresh(@PathVariable Long id) { return success(service.refresh(id)); }
}
