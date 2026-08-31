package cn.iocoder.yudao.module.eam.controller.admin.employeeasset.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class EamEmployeeAssetTaskActionReqVO {
    @Valid
    @NotNull(message = "资产复核明细不能为空")
    private List<Item> items;
    private String remark;

    @Data
    public static class Item {
        @NotNull(message = "任务明细不能为空")
        private Long id;
        @NotNull(message = "处理动作不能为空")
        private Integer action;
        private Long transferToEmployeeId;
        private String remark;
    }
}
