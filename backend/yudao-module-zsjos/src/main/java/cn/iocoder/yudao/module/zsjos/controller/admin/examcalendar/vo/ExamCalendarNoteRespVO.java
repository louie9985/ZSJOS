package cn.iocoder.yudao.module.zsjos.controller.admin.examcalendar.vo;

import lombok.Data;
import java.util.List;

@Data
public class ExamCalendarNoteRespVO {
    private String content;
    private Long version;
    private List<ExamCalendarNoteImageRespVO> images;
}
