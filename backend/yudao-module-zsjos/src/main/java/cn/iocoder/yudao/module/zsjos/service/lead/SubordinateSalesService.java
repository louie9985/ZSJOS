package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.management.LeadManagementRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.subordinate.*;

import java.util.List;

public interface SubordinateSalesService {
    PageResult<SubordinateSalesRespVO> getPage(SubordinateSalesPageReqVO reqVO, Long managerUserId);
    SubordinateSalesRespVO getOverview(Long salesUserId, Long managerUserId);
    PageResult<LeadManagementRespVO> getLeadPage(Long salesUserId, LeadManagementPageReqVO reqVO,
                                                  Long managerUserId);
    PageResult<SubordinateTaskRespVO> getTaskPage(Long salesUserId, SubordinateTaskPageReqVO reqVO,
                                                   Long managerUserId);
    List<LeadAssignmentUserRespVO> getTransferCandidates(Long managerUserId);
    void updateAccountStatus(Long salesUserId, SubordinateAccountStatusReqVO reqVO, Long managerUserId);
    void updateDispatchMode(Long salesUserId, SubordinateDispatchModeReqVO reqVO, Long managerUserId);
    SubordinatePauseAllRespVO pauseAllDispatch(Long managerUserId);
    SubordinateBatchResultVO batchTransfer(SubordinateBatchTransferReqVO reqVO, Long managerUserId);
    SubordinateBatchResultVO batchReleasePublicSea(SubordinateBatchPublicSeaReqVO reqVO, Long managerUserId);
}
