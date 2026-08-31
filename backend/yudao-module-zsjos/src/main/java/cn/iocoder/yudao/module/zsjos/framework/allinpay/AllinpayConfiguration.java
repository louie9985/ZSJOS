package cn.iocoder.yudao.module.zsjos.framework.allinpay;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AllinpayProperties.class)
public class AllinpayConfiguration {
}
