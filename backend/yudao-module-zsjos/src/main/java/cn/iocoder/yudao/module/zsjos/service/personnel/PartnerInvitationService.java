package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerInvitationCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerInvitationPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerInvitationRespVO;
import cn.iocoder.yudao.module.zsjos.controller.app.partner.vo.PartnerActivateReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personnel.PartnerAccountDO;

public interface PartnerInvitationService {

    PartnerInvitationRespVO create(PartnerInvitationCreateReqVO reqVO, Long operatorUserId);

    PageResult<PartnerInvitationRespVO> getPage(PartnerInvitationPageReqVO reqVO);

    void voidInvitation(Long id, Long operatorUserId);

    PageResult<LeadAssignmentUserRespVO> getOperatorCandidatePage(String keyword, Integer pageNo, Integer pageSize);

    boolean hasActiveInvitation(String mobile);

    PartnerAccountDO activate(PartnerActivateReqVO reqVO);
}
