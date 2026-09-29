package cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo;

/** Acceptance is not a delivery receipt; channel outcomes are queried from batch history. */
public record CalendarNotifySendRespVO(Long batchId, int acceptedCount, boolean resend,
                                       String sourceEventKey, int skippedCount, String status) {}
