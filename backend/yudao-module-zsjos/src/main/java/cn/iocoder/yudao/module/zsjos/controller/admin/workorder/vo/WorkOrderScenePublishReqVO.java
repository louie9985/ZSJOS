package cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkOrderScenePublishReqVO {
    @NotNull private Long id;
    @NotNull private Integer version;
}
