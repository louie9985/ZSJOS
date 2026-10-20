package cn.iocoder.yudao.module.zsjos.service.educationteam;

import cn.iocoder.yudao.module.zsjos.service.lead.*;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.educationteam.vo.*;

import java.util.List;

public interface EducationTeamService {
    PageResult<EducationTeamRespVO> getPage(EducationTeamPageReqVO reqVO, Long managerUserId);
    EducationTeamRespVO getOverview(Long salesUserId, Long managerUserId);
    PageResult<LeadManagementRespVO> getLeadPage(Long salesUserId, LeadManagementPageReqVO reqVO,
                                                  Long managerUserId);
    PageResult<EducationTeamTaskRespVO> getTaskPage(Long salesUserId, EducationTeamTaskPageReqVO reqVO,
                                                   Long managerUserId);
    List<LeadAssignmentUserRespVO> getTransferCandidates(Long managerUserId);
    List<LeadAssignmentUserRespVO> getTransferCandidates(Long managerUserId, Long leadId);
    List<LeadAssignmentUserRespVO> getBatchTransferCandidates(Long managerUserId, List<Long> leadIds);
    void updateAccountStatus(Long salesUserId, EducationTeamAccountStatusReqVO reqVO, Long managerUserId);
    void transferLead(Long leadId, EducationTeamLeadActionReqVO reqVO, Long managerUserId);
    void restoreLead(Long leadId, EducationTeamLeadActionReqVO reqVO, Long managerUserId);
    void recycleLead(Long leadId, EducationTeamLeadActionReqVO reqVO, Long managerUserId);
    EducationTeamBatchResultVO batchLeadAction(String action, EducationTeamBatchLeadActionReqVO reqVO,
                                             Long managerUserId);
}
