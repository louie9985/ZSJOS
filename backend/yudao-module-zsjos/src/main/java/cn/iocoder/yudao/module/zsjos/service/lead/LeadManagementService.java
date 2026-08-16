package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.CursorPageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadBasicInfoUpdateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadInboxFilterProfileRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;

import java.util.List;

public interface LeadManagementService {

    PageResult<LeadManagementRespVO> getLeadPage(LeadManagementPageReqVO reqVO, Long userId);
    CursorPageResult<LeadManagementRespVO> getLeadCursor(LeadManagementPageReqVO reqVO, Long userId);

    LeadManagementRespVO getLead(Long id, Long userId);

    void updateBasicInfo(Long id, Long userId, LeadBasicInfoUpdateReqVO reqVO);

    java.util.Map<String, Long> getStatusCounts(Long userId);

    List<LeadAssignmentUserRespVO> getVisibleUsers(Long userId);

    LeadInboxFilterProfileRespVO getInboxFilterProfile(Long userId, String audience);

    PageResult<LeadManagementRespVO> getManagedOwnerLeadPage(LeadManagementPageReqVO reqVO,
                                                              Long managerUserId, Long ownerUserId);
}
