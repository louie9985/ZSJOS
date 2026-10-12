package cn.iocoder.yudao.module.zsjos.controller.admin.pointsredemption.vo;
import lombok.Data;
@Data public class PointsPackageRespVO {
    private Long id;
    private String name;
    private Integer creditsAmount;
    private Integer validDays;
    private Boolean enabled;
}
