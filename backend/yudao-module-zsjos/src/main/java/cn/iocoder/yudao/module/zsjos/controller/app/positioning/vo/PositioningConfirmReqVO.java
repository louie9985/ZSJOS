package cn.iocoder.yudao.module.zsjos.controller.app.positioning.vo;
import jakarta.validation.constraints.*; import lombok.Data;
@Data public class PositioningConfirmReqVO { @NotNull private Integer version; private String comment; }
