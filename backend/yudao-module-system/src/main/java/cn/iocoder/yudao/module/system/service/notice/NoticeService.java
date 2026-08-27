package cn.iocoder.yudao.module.system.service.notice;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.controller.admin.notice.vo.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface NoticeService {
    Long createNotice(NoticeSaveReqVO reqVO, Long userId);
    void updateNotice(NoticeSaveReqVO reqVO, Long userId);
    void deleteNotice(Long id);
    void deleteNoticeList(List<Long> ids);
    PageResult<NoticeRespVO> getNoticePage(NoticePageReqVO reqVO);
    NoticeRespVO getNotice(Long id);
    NoticeAttachmentVO uploadAttachment(MultipartFile file, Long userId) throws Exception;
    void publishNotice(Long id);
    void offlineNotice(Long id);
    Long copyNotice(Long id);
    PageResult<NoticeMyRespVO> getMyNoticePage(PageParam reqVO, Long userId);
    NoticeMyRespVO getMyNotice(Long id, Long userId);
    NoticeUnreadSummaryRespVO getUnreadSummary(Long userId);
    void markRead(Long id, Long userId);
}
