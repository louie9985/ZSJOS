package cn.iocoder.yudao.module.zsjos.controller.pub.payment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.zsjos.service.payment.PaymentRefundService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import java.util.Map;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "ZSJOS - 通联退款回调")
@RestController
@RequestMapping("/public-api/zsjos/payment/allinpay")
public class PaymentRefundNotifyController {
    @Resource private PaymentRefundService service;
    @PostMapping("/refund-notify")
    @PreAuthorize("permitAll()")
    @TenantIgnore
    public CommonResult<Boolean> notify(@RequestBody Map<String, Object> payload) {
        service.notify(payload); return success(true);
    }
}
