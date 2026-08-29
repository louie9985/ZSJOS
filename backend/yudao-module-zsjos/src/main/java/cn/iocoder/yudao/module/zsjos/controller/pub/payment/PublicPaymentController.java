package cn.iocoder.yudao.module.zsjos.controller.pub.payment;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.zsjos.controller.pub.payment.vo.PublicPaymentDetailRespVO;
import cn.iocoder.yudao.module.zsjos.service.payment.PurchaseIntentService;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/zsjos/payment")
@Validated
@PermitAll
@TenantIgnore
public class PublicPaymentController {
    @Resource private PurchaseIntentService service;

    @GetMapping("/{no}")
    public CommonResult<PublicPaymentDetailRespVO> detail(@PathVariable String no, @RequestParam @NotBlank String token) {
        return success(service.publicDetail(no, token));
    }

    @PostMapping(value = "/{no}/order", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CommonResult<Object> order(@PathVariable String no, @RequestBody Map<String, String> request) {
        Object data = service.publicOrder(no, request.get("token"), request.get("channel"));
        return success(data);
    }

    @PostMapping(value = "/{no}/order", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> wechatOrder(@PathVariable String no, @RequestParam @NotBlank String token,
                                               @RequestParam String channel) {
        if (!"wechat".equals(channel)) return ResponseEntity.badRequest().body("unsupported channel");
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(service.publicWechatOrderHtml(no, token));
    }

    @PostMapping("/{no}/status")
    public CommonResult<Boolean> status(@PathVariable String no, @RequestBody Map<String, String> request) {
        return success(service.publicStatus(no, request.get("token")));
    }
}
