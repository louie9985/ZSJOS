package cn.iocoder.yudao.module.zsjos.controller.admin.calendar.vo;

/** System department identity is additive; recipients remain employee IDs only. */
public record CalendarNotifyUserRespVO(Long id, String nickname, Long deptId) {}
