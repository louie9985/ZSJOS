package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MediaStudentTargetRespVO {
    private Long personId;
    private String targetTab;
    private Long recordId;
    private Long serviceRelationId;
    public MediaStudentTargetRespVO(Long personId, String targetTab, Long recordId) {
        this(personId, targetTab, recordId, null);
    }
}
