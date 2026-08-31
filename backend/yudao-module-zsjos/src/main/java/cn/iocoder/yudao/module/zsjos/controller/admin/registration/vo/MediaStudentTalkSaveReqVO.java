package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class MediaStudentTalkSaveReqVO {
    private Long accountId;
    @NotBlank @Size(max = 2000) private String content;
    private List<Long> attachmentFileIds;
}
