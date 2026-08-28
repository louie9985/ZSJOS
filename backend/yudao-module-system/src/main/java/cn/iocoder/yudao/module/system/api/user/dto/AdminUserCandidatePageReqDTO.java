package cn.iocoder.yudao.module.system.api.user.dto;

import lombok.Data;

import java.util.Set;

/** Cross-module query for enabled users matching a frozen role/department qualification. */
@Data
public class AdminUserCandidatePageReqDTO {

    private String qualificationMode;
    private Set<Long> roleIds;
    private Set<Long> deptIds;
    private String keyword;
    private Integer pageNo;
    private Integer pageSize;
}
