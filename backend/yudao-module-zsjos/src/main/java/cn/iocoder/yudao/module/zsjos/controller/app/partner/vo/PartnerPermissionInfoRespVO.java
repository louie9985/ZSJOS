package cn.iocoder.yudao.module.zsjos.controller.app.partner.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
public class PartnerPermissionInfoRespVO {
    private User user;
    private Set<String> roles;
    private Set<String> permissions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class User {
        private Long id;
        private String nickname;
        private String avatar;
    }
}
