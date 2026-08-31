package cn.iocoder.yudao.module.system.api.user.dto;

import lombok.Data;

import java.util.Set;

@Data
public class AdminUserOrganizationUpdateReqDTO {
    private Long userId;
    private Long deptId;
    private Set<Long> postIds;
}
