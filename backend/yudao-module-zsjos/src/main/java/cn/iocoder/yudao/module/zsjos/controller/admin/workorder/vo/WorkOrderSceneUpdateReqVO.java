package cn.iocoder.yudao.module.zsjos.controller.admin.workorder.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkOrderSceneUpdateReqVO extends WorkOrderSceneCreateReqVO {
    @NotNull private Long id;
    @NotNull private Integer version;
}
