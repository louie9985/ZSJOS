package cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo;

import lombok.Data;

@Data
public class ExamScheduleAttachmentRespVO {
    private Long fileId;
    private String name;
    private String type;
    private Long size;
    private String url;
}
