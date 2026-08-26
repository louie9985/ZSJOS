package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import lombok.Data;

@Data
public class MediaScreenContributionRow {
    private Long contributorUserId;
    private Long sourceDeptId;
    private String sourceType;
    private Long sourceProviderUserId;
    private Long todayCount;
    private Long weekCount;
    private Long monthTotal;
    private Long monthEffective;
}
