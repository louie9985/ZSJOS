package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PartnerLeaderboardMetricRow {
    private Long partnerId;
    private BigDecimal value;
}
