package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.*;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface RegistrationService {
    Long ensureCaseAfterRegistrationApproval(Long orderId, LocalDateTime approvedAt);
    PageResult<RegistrationCaseRespVO> getPoolPage(RegistrationPoolPageReqVO reqVO);
    RegistrationCaseRespVO getCase(Long caseId);
    List<StudyPlannerSimpleRespVO> getStudyPlannerCandidates(Long userId);
    List<StudyPlannerSimpleRespVO> getRouteCandidates(Long caseId, Long routeId, Long userId);
    RegistrationCaseRespVO updateChecklistItem(Long caseId, Long itemId, Long userId, RegistrationChecklistItemUpdateReqVO reqVO);
    RegistrationCaseRespVO updateStudyPlanner(Long caseId, Long userId, RegistrationPlannerUpdateReqVO reqVO);
    RegistrationCaseRespVO updateRoutes(Long caseId, Long userId, RegistrationRoutesUpdateReqVO reqVO);
    RegistrationAttachmentUploadRespVO uploadAttachment(Long caseId, Long itemId, Long userId, Integer version,
                                                         String idempotencyKey, MultipartFile file) throws IOException;
    RegistrationCaseRespVO deleteAttachment(Long caseId, Long itemId, Long attachmentId, Long userId,
                                             RegistrationAttachmentDeleteReqVO reqVO);
    void complete(Long caseId, Long userId, RegistrationVersionReqVO reqVO);
    void close(Long caseId, Long userId, RegistrationCloseReqVO reqVO);
    void cancelByOrderId(Long orderId, String reason, LocalDateTime now);
}
