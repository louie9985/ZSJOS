package cn.iocoder.yudao.module.zsjos.controller.admin.export.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExportTaskRespVO {
    private Long id;
    private String taskNo;
    private String exportType;
    private String status;
    private Integer attemptCount;
    private String resultFileName;
    private Long resultFileSize;
    private LocalDateTime readyAt;
    private LocalDateTime expiresAt;
    private String failureCode;
    private String failureMessage;
    private LocalDateTime createTime;
}
