package cn.iocoder.yudao.module.zsjos.service.forcedform;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.controller.admin.forcedform.vo.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

public interface ForcedFormService {

    PageResult<ForcedFormRespVO> page(ForcedFormPageReqVO req);

    Long create(ForcedFormSaveReqVO req, Long actor);

    void update(ForcedFormSaveReqVO req);

    ForcedFormRespVO get(Long id);

    void delete(Long id, Long actor);

    ForcedFormRespVO copy(Long id, Long actor);

    void publish(Long id, Long actor);

    void withdraw(Long id, Long actor);

    ForcedFormRecipientPreviewRespVO recipientPreview(Long id, ForcedFormSendReqVO req);

    ForcedFormSendRespVO send(Long id, ForcedFormSendReqVO req, Long actor);

    List<ForcedFormPendingRespVO> pending(Long userId);

    ForcedFormRuntimeRespVO runtime(Long id, Long userId);

    ForcedFormAttachmentUploadRespVO uploadAttachment(Long id, Long userId, String fieldKey, MultipartFile file);

    void submit(Long id, ForcedFormSubmitReqVO req, Long userId);

    ForcedFormStatusRespVO status(Long userId);

    PageResult<ForcedFormSubmissionListRespVO> submissionPage(ForcedFormSubmissionPageReqVO req);

    ForcedFormSubmissionRespVO submission(Long id);

    void exportSubmissions(ForcedFormSubmissionPageReqVO req, HttpServletResponse response);

    int cleanupTemporaryFiles();
}
