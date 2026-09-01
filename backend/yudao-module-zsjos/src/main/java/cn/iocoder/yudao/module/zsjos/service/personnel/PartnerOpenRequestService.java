package cn.iocoder.yudao.module.zsjos.service.personnel;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.assignment.LeadAssignmentUserRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerOpenRequestCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerOpenRequestPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.personnel.vo.PartnerOpenRequestRespVO;

public interface PartnerOpenRequestService {

    Long create(PartnerOpenRequestCreateReqVO reqVO, Long applicantUserId);

    PageResult<PartnerOpenRequestRespVO> getPage(PartnerOpenRequestPageReqVO reqVO, Long userId);

    PartnerOpenRequestRespVO getDetail(Long id, Long userId);

    void cancel(Long id, Long userId);

    PageResult<LeadAssignmentUserRespVO> getAssigneeCandidatePage(Long deptId, String keyword,
                                                                  Integer pageNo, Integer pageSize);

    void handleProcessResult(String processInstanceId, Integer processStatus, String reason);
}
