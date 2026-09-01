package cn.iocoder.yudao.module.system.controller.admin.workbenchlayout.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkbenchLayoutVersionRespVO {

    private Long id;
    private Integer versionNo;
    private Boolean enabled;
    private Integer priority;
    private String publishRemark;
    private Long restoredFromVersionId;
    private Long publisherUserId;
    private LocalDateTime publishTime;

}
