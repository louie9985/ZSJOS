package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ContentReviewBatchPageReqVO extends PageParam {
    private String keyword;
    private String status;
    private Long accountId;
    private Boolean mine;
    private List<String> statuses;
    private Long operatorUserId;
    private Long directorUserId;
    private String platformValue;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate submittedFrom;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate submittedTo;
}
