package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerStudentInvitationContextRespVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MediaStudentPartnerContextRespVO extends PartnerStudentInvitationContextRespVO {
    private boolean canInviteStudent;
}
