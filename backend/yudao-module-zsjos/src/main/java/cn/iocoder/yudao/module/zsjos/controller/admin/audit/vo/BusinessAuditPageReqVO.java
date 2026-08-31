package cn.iocoder.yudao.module.zsjos.controller.admin.audit.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Data
@EqualsAndHashCode(callSuper = true)
public class BusinessAuditPageReqVO extends PageParam {

    private String categoryCode;
    private String actionCode;
    private String targetType;
    private String sourceType;
    private String resultStatus;
    private Long operatorUserId;

    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] occurredAt;
}
