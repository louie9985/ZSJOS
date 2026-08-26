package cn.iocoder.yudao.module.zsjos.controller.admin.account.vo;

import lombok.Data;

import java.util.List;

@Data
public class MediaAccountCalendarRespVO {
    private List<MediaAccountCalendarItemRespVO> list;
    private Long total;
    private Long unscheduledCount;
}
