package cn.iocoder.yudao.module.eam.controller.admin.employeeasset.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class EamEmployeeAssetTaskRespVO {
    private Long id;
    private Integer type;
    private Integer status;
    private String processInstanceId;
    private Long demandId;
    private LocalDateTime plannedLeaveTime;
    private String remark;
    private LocalDateTime createTime;
    private List<EamEmployeeAssetTaskItemRespVO> items;
}
