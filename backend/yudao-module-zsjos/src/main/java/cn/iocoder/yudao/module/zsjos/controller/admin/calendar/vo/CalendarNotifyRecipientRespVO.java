package cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo;

import java.time.LocalDateTime;

public record CalendarNotifyRecipientRespVO(Long userId, Integer userType, String nickname,
                                           String status, String skipReason, Long messageId,
                                           LocalDateTime createTime, LocalDateTime completedTime) {}
