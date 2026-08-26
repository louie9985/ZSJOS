package cn.iocoder.yudao.module.zsjos.controller.pub.positioning.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class PublicPositioningConfirmationRespVO {
    private String state;
    private String accountName;
    private String platformLabel;
    private LocalDateTime submittedAt;
    private List<?> fields;
    private Map<String, Object> values;
    private Map<String, Object> dictSnapshots;
    private Map<String, Object> legacySections;
}
