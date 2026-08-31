package cn.iocoder.yudao.module.zsjos.controller.admin.account.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
public class MediaAccountCalendarPageReqVO extends PageParam {
    @NotNull private LocalDate rangeStart;
    @NotNull private LocalDate rangeEnd;
    private String keyword;
    private String currentStatusValue;
    private String stageValue;
    private Long directorUserId;
    private Long operatorUserId;
}
