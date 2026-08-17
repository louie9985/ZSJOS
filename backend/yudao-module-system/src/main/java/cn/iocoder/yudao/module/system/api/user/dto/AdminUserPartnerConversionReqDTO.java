package cn.iocoder.yudao.module.system.api.user.dto;

import lombok.Data;

import java.util.Set;

@Data
public class AdminUserPartnerConversionReqDTO {
    private Long existingUserId;
    private String username;
    private String password;
    private String nickname;
    private String mobile;
    private Long deptId;
    private Set<Long> postIds;
}
