package cn.iocoder.yudao.module.zsjos.controller.admin.account.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class MediaAccountFieldConfigSaveReqVO {
    @NotNull private Long id;
    @NotNull private Integer version;
    @Valid @NotEmpty private List<MediaAccountFieldConfigRespVO.FieldVO> fields;
}
