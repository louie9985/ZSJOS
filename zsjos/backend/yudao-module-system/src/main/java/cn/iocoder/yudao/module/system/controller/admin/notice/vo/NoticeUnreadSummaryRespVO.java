package cn.iocoder.yudao.module.system.controller.admin.notice.vo;

import lombok.Data;

@Data
public class NoticeUnreadSummaryRespVO {
    private Long unreadCount;
    private NoticeMyRespVO latest;
}
