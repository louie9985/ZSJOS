package cn.iocoder.yudao.module.zsjos.controller.admin.account.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class MediaAccountUpdateReqVO {
    @NotNull private Integer version;
    private String nickname;
    private String platformAccountId;
    private String leadDirection;
    private Long directorUserId;
    @Size(max = 100) private String accountTypePrimaryValue;
    @Size(max = 100) private String accountTypeSecondaryValue;
    @Size(max = 100) private String trackPrimaryValue;
    @Size(max = 100) private String trackSecondaryValue;
    private String accountGradeValue;
    private String accountGradeLabelSnapshot;
    private String healthStatusValue;
    private String healthStatusLabelSnapshot;
    private String riskLevelValue;
    private String riskLevelLabelSnapshot;
    private String healthJson;
    private Map<String, Object> detailValues;
}
