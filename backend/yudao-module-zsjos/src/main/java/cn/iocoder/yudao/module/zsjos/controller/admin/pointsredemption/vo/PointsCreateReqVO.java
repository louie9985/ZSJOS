package cn.iocoder.yudao.module.zsjos.controller.admin.pointsredemption.vo;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;
@Data
public class PointsCreateReqVO {
    @NotNull private Long packageId;
    @NotBlank @Size(max=2000) private String reason;
    @NotEmpty @Size(max=10) private List<@NotNull Long> attachmentIds;
    @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{16,64}") private String submissionKey;
}
