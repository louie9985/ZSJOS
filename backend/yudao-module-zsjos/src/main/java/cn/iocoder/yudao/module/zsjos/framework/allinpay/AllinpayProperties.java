package cn.iocoder.yudao.module.zsjos.framework.allinpay;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "zsjos.payment.allinpay")
public class AllinpayProperties {
    private boolean enabled;
    private String cusid;
    private String appid;
    private String orgid;
    private String unionorderUrl = "https://syb-test.allinpay.com/apiweb/h5unionpay/unionorder";
    private String unitorderPayUrl = "https://syb-test.allinpay.com/apiweb/unitorder/pay";
    private String queryUrl = "https://syb-test.allinpay.com/apiweb/tranx/query";
    private String closeUrl = "https://syb-test.allinpay.com/apiweb/tranx/close";
    private String refundUrl = "https://syb-test.allinpay.com/apiweb/tranx/refund";
    private String refundQueryUrl;
    private String refundNotifyUrl;
    private String refundVersion = "11";
    private String returnUrl;
    private String notifyUrl;
    private String merchantPrivateKeyLocation;
    private String platformPublicKeyLocation;
    private String linkHmacSecret;
    private String publicBaseUrl;
    private long linkTtlHours = 24;
    private int connectTimeoutSeconds = 10;
    private int readTimeoutSeconds = 20;
    private String allowedPayinfoHosts = "";
}
