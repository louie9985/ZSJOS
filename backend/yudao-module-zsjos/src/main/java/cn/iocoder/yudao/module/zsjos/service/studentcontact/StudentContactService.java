package cn.iocoder.yudao.module.zsjos.service.studentcontact;

import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.*;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;

public interface StudentContactService {
    StudentContactContextRespVO getContext(Long relationId, Long userId);
    PageResult<StudentContactRecordRespVO> getRecords(Long relationId, PageParam page, Long userId);
    void accept(Long relationId, StudentServiceAcceptReqVO request, Long userId);
    void updateBasicInfo(Long relationId, StudentBasicInfoUpdateReqVO request, Long userId);
    Long submitFirstContact(Long relationId, StudentFirstContactSubmitReqVO request, Long userId);
    Long submitStudyPlan(Long relationId, StudentStudyPlanSubmitReqVO request, Long userId);
    Long submitContact(Long relationId, StudentContactSubmitReqVO request, Long userId);
    List<StudyPlannerSimpleRespVO> getCollaboratorCandidates(Long relationId, String type, Long userId);
    void assignCollaborator(Long relationId, StudentCollaboratorAssignReqVO request, Long userId);
    void withdrawExtension(Long extensionId, Integer version, String reason, String idempotencyKey, Long userId);
    void handleExtensionResult(String processInstanceId, Integer processStatus, String reason);
    PageResult<StudentContactExtensionRespVO> getExtensions(PageParam page, String statusScope, Long userId);
    void completeAssistance(Long taskId, String remark, Long userId);
    StudentContactAttachmentRespVO uploadAttachment(Long relationId, Long userId, MultipartFile file) throws IOException;
}
