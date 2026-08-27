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

    FeedbackFormRespVO getCurrentForm(String type);

    Long create(String type, FeedbackCreateReqVO request, Long userId);

    void resubmit(Long id, FeedbackActionVO.ResubmitReq request, Long userId);

    PageResult<FeedbackRespVO> getMyPage(FeedbackPageReqVO request, Long userId);

    FeedbackRespVO getOwn(Long id, Long userId);

    void markRead(Long id, FeedbackActionVO.VersionedCommand request, Long userId);

    void replyOwn(Long id, FeedbackActionVO.ReplyReq request, Long userId);

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

    void handleProcessResult(BpmProcessInstanceStatusEvent event);
}
