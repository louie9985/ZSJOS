package cn.iocoder.yudao.module.zsjos.controller.app.partner.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PartnerHomeStatisticsRespVO {
    private String period;
    private Long leadCount;
    private BigDecimal withdrawnAmount;
    private Long validLeadCount;
    private Long convertedLeadCount;
}
