package cn.iocoder.yudao.module.zsjos.controller.admin.account.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class MediaAccountUpdateReqVO {
    @NotNull private Integer version;
    private String nickname;
    private String platformAccountId;
    private String leadDirection;
    private Long directorUserId;
    private String accountGradeValue;
    private String accountGradeLabelSnapshot;
    private String healthStatusValue;
    private String healthStatusLabelSnapshot;
    private String riskLevelValue;
    private String riskLevelLabelSnapshot;
    private String healthJson;
    private Map<String, Object> detailValues;
}
