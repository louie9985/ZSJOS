package cn.iocoder.yudao.module.system.controller.admin.notice.vo;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;
@Data
public class NoticeShareOpenReqVO {
    @NotNull @Positive private Long noticeId;
    @NotNull @Size(max = 10) private List<@NotNull @Positive Long> attachmentIds;
}
