package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.dispatch;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class LeadPendingRespVO {
    private Long id;
    private String dispatchMode;
    private String maskedName;
    private String maskedMobile;
    private String maskedWechatId;
    private String provinceName;
    private String cityName;
    private List<String> intendedProducts;
    private String primaryIntendedProduct;
    private String sourceChannel;
    private String leadCategory;
    private String remark;
    private List<String> attachmentUrls;
    private LocalDateTime submittedAt;
    private LocalDateTime expiresAt;
    private Long remainingSeconds;
    private Boolean rejectable;
    private Boolean deferrable;
    private Long assignmentHistoryId;
}
