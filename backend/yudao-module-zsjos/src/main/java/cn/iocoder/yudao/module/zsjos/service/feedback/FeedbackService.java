package cn.iocoder.yudao.module.zsjos.service.feedback;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackActionVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackConfigVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackCreateReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackFormRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackPageReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.feedback.vo.FeedbackRespVO;

import java.util.List;

public interface FeedbackService {

    FeedbackRespVO.Portal getPortal(Long userId);

    FeedbackRespVO.Portal getPartnerPortal(Long accountId, Long partnerId);

    FeedbackFormRespVO getCurrentForm(String type);

    Long create(String type, FeedbackCreateReqVO request, Long userId);

    Long createForPartner(String type, FeedbackCreateReqVO request, Long accountId, Long partnerId);

    void resubmit(Long id, FeedbackActionVO.ResubmitReq request, Long userId);

    PageResult<FeedbackRespVO> getMyPage(FeedbackPageReqVO request, Long userId);

    PageResult<FeedbackRespVO> getPartnerPage(FeedbackPageReqVO request, Long accountId, Long partnerId);

    FeedbackRespVO getOwn(Long id, Long userId);

    FeedbackRespVO getPartnerOwn(Long id, Long accountId, Long partnerId);

    void markRead(Long id, FeedbackActionVO.VersionedCommand request, Long userId);

    void markReadForPartner(Long id, FeedbackActionVO.VersionedCommand request, Long accountId, Long partnerId);

    void replyOwn(Long id, FeedbackActionVO.ReplyReq request, Long userId);

    void replyForPartner(Long id, FeedbackActionVO.ReplyReq request, Long accountId, Long partnerId);

    void submitSurvey(Long id, FeedbackActionVO.SurveySubmitReq request, Long userId);

    PageResult<FeedbackRespVO> getAdminPage(String type, FeedbackPageReqVO request, Long userId);

    FeedbackRespVO getAdmin(Long id, Long userId);

    void assign(Long id, FeedbackActionVO.AssignReq request, Long userId);

    void replyAdmin(Long id, FeedbackActionVO.ReplyReq request, Long userId);

    void complete(Long id, FeedbackActionVO.CompleteReq request, Long userId);

    void requestSurvey(Long id, FeedbackActionVO.VersionedCommand request, Long userId);

    List<FeedbackConfigVO.Resp> getConfigs();

    void saveConfig(FeedbackConfigVO.SaveReq request, Long userId);

    List<FeedbackConfigVO.UserOption> getCandidates(String type);

    List<FeedbackConfigVO.FormOption> getFormOptions();

    List<FeedbackConfigVO.ProcessOption> getProcessOptions();

    FileInfoRespDTO upload(byte[] content, String name, String contentType, Long userId);

    FileInfoRespDTO uploadForPartner(byte[] content, String name, String contentType, Long accountId);

    void handleProcessResult(BpmProcessInstanceStatusEvent event);
}
