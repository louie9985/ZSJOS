package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.*;

import java.time.LocalDateTime;
import java.util.List;

public interface RegistrationService {
    Long ensureCaseAfterRegistrationApproval(Long orderId, LocalDateTime approvedAt);
    PageResult<RegistrationCaseRespVO> getPoolPage(PageParam pageParam, String status, String keyword);
    RegistrationCaseRespVO getCase(Long caseId);
    List<StudyPlannerSimpleRespVO> getStudyPlannerCandidates();
    RegistrationCaseRespVO updateChecklistItem(Long caseId, Long itemId, Long userId, RegistrationChecklistItemUpdateReqVO reqVO);
    RegistrationCaseRespVO updateStudyPlanner(Long caseId, Long userId, RegistrationPlannerUpdateReqVO reqVO);
    void complete(Long caseId, Long userId, RegistrationVersionReqVO reqVO);
    void cancelByOrderId(Long orderId, String reason, LocalDateTime now);
}
