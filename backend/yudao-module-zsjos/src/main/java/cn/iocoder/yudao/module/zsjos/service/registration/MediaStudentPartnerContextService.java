package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MediaStudentPartnerContextRespVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.ServiceRelationMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerInvitationService;
import cn.iocoder.yudao.module.zsjos.service.personnel.PartnerStudentLinkService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class MediaStudentPartnerContextService {
    @Resource private MyStudentService students;
    @Resource private PermissionApi permissions;
    @Resource private ServiceRelationMapper relations;
    @Resource private PartnerStudentLinkService links;
    @Resource private PartnerInvitationService invitations;

    @ZsjosPermission(bizType = "student", bizId = "#personId", action = "read")
    public MediaStudentPartnerContextRespVO getContext(Long userId, Long personId) {
        students.getMediaStudent(userId, personId);
        // Reading a student's binding must not grant access to registration invitation codes.
        boolean canInvite = permissions.hasAnyPermissions(userId, "zsjos:partner-invitation:create-student")
                && !relations.selectActiveByContentDirectorAndPerson(userId, personId).isEmpty();
        if (canInvite) {
            var result = BeanUtils.toBean(invitations.getStudentContext(personId, userId),
                    MediaStudentPartnerContextRespVO.class);
            result.setCanInviteStudent(true);
            return result;
        }
        var result = new MediaStudentPartnerContextRespVO();
        result.setOpened(links.hasActiveStudentLink(personId));
        return result;
    }
}
