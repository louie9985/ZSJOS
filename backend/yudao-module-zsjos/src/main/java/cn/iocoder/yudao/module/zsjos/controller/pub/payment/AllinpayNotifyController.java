package cn.iocoder.yudao.module.zsjos.controller.pub.payment;

import cn.iocoder.yudao.module.zsjos.service.payment.PurchaseIntentService;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.zsjos.framework.allinpay.AllinpayProperties;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.net.URI;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/zsjos/payment/allinpay")
@PermitAll
@TenantIgnore
public class AllinpayNotifyController {
    @Resource private PurchaseIntentService service;
    @Resource private AllinpayProperties properties;

    @PostMapping("/notify")
    public String notify(@RequestParam Map<String, Object> payload) {
        service.notify(payload);
        return "success";
    }

    @GetMapping("/result")
    public ResponseEntity<Void> result() {
        String base = properties.getPublicBaseUrl();
        if (base == null || base.isBlank()) return ResponseEntity.notFound().build();
        return ResponseEntity.status(302).location(URI.create(base.replaceAll("/+$", "") + "/payment-result")).build();
    }
}
