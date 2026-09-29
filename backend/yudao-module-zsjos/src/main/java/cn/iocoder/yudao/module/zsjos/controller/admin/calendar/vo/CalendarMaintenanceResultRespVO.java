package cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo;

public record CalendarMaintenanceResultRespVO(Long calendarId, String calendarType, String eventType,
                                               String notificationStatus, String reasonCode, Long batchId) {}
