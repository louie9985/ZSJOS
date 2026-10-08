package cn.iocoder.yudao.module.system.service.notice;
import cn.iocoder.yudao.module.system.controller.admin.notice.vo.*;
import cn.iocoder.yudao.module.system.controller.pub.notice.vo.NoticeSharePublicRespVO;
public interface NoticeShareService {
    NoticeShareRespVO get(Long noticeId);
    NoticeShareRespVO open(NoticeShareOpenReqVO request, Long userId);
    void close(NoticeShareCloseReqVO request, Long userId);
    NoticeSharePublicRespVO publicNotice(String token);
    String attachmentUrl(String token, Long attachmentId);
}
