package cn.iocoder.yudao.module.system.controller.admin.notice.vo;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class NoticeShareCloseReqVO {
    @NotNull @Positive private Long noticeId;
    @NotNull @Positive private Long version;
}
