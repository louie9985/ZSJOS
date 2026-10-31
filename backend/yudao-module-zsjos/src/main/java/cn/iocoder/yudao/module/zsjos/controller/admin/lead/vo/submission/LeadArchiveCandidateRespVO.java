package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission;

import lombok.Data;

@Data
public class LeadArchiveCandidateRespVO {
    private Long personId;
    private String personNo;
    private String name;
    private String mobile;
    private String wechatId;
    private String snapshotKey;
}
