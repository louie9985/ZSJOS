package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import lombok.Data;

@Data
public class MediaScreenContributionRow {
    private Long contributorUserId;
    private Long sourceDeptId;
    private String contributorName;
    private String departmentName;
    private Long supervisorUserId;
    private String supervisorName;
    private String contributionType;
    private Long providerOwnerId;
    private String providerOwnerName;
    private Long todayCount;
    private Long weekCount;
    private Long monthTotal;
    private Long monthEffective;
}
