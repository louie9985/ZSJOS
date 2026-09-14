package cn.iocoder.yudao.module.zsjos.controller.admin.account.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class MediaAccountSaveReqVO {
    @NotNull private Long studentPersonId;
    @NotNull private Long serviceRelationId;
    @NotNull @PositiveOrZero private Integer version;
    @NotBlank @Size(max = 128) private String idempotencyKey;
    private Long directorUserId;
    /** Account creation establishes the relation first; platform/profile fields may be completed later. */
    private String platformValue;
    private String platformLabelSnapshot;
    private String platformAccountId;
    private String nickname;
    private String leadDirection;
    @Size(max = 100) private String accountTypePrimaryValue;
    @Size(max = 100) private String accountTypeSecondaryValue;
    @Size(max = 100) private String trackPrimaryValue;
    @Size(max = 100) private String trackSecondaryValue;
    private Map<String, Object> detailValues;
}
