package cn.iocoder.yudao.module.eam.controller.admin.employeeasset.vo;

import lombok.Data;

@Data
public class EamEmployeeAssetTaskItemRespVO {
    private Long id;
    private Long taskId;
    private Long assetId;
    private Long holdingId;
    private String assetNameSnapshot;
    private Integer action;
    private Long transferToEmployeeId;
    private Integer status;
    private String remark;
}
