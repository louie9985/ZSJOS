package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import lombok.Data;

@Data
public class MediaScreenTimedContributionRow {
    private String bucket;
    private Long contributorUserId;
    private Long sourceDeptId;
    private String contributionType;
    private Long submittedCount;
    private Long validCount;
}
