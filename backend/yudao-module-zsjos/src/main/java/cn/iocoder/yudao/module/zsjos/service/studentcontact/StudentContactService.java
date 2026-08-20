package cn.iocoder.yudao.module.zsjos.service.studentcontact;

import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.*;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface StudentContactService {
    StudentContactContextRespVO getContext(Long relationId, Long userId);
    List<StudentContactRecordRespVO> getRecords(Long relationId, Long userId);
    void accept(Long relationId, StudentServiceAcceptReqVO request, Long userId);
    Long submitFirstContact(Long relationId, StudentFirstContactSubmitReqVO request, Long userId);
    Long submitStudyPlan(Long relationId, StudentStudyPlanSubmitReqVO request, Long userId);
    Long submitContact(Long relationId, StudentContactSubmitReqVO request, Long userId);
    List<StudyPlannerSimpleRespVO> getCollaboratorCandidates(Long relationId, String type, Long userId);
    void assignCollaborator(Long relationId, StudentCollaboratorAssignReqVO request, Long userId);
    void withdrawExtension(Long extensionId, String reason, Long userId);
    void handleExtensionResult(String processInstanceId, Integer processStatus, String reason);
    List<StudentContactExtensionRespVO> getExtensions(Long userId);
    void completeAssistance(Long taskId, String remark, Long userId);
    StudentContactAttachmentRespVO uploadAttachment(MultipartFile file) throws IOException;
}
