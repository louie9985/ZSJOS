package cn.iocoder.yudao.module.zsjos.service.personnel;

import lombok.Data;

@Data
public class PartnerInvitationCreateCommand {

    private String name;
    private String mobile;
    private Long assignedOperatorUserId;
}
