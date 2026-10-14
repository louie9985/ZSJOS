package cn.iocoder.yudao.module.zsjos.service.workorder;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "zsjos.work-order.upload")
public class WorkOrderUploadProperties {
    public static final long MAX_FILE_BYTES = 100L * 1024 * 1024;

    @Min(1) @Max(2)
    private int maxConcurrentRequests = 2;

    @Min(1) @Max(MAX_FILE_BYTES)
    private long maxInFlightBytes = MAX_FILE_BYTES;
}
