package cn.iocoder.yudao.module.zsjos.controller.admin.material.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class MaterialSubmitReqVO {
    @NotNull private Integer expectedVersion;
    private Map<String, List<Long>> startUserSelectAssignees;
}
