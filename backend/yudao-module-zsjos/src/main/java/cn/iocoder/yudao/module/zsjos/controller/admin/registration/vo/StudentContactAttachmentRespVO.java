package cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StudentContactAttachmentRespVO {
    private Long fileId;
    private String name;
    private String url;
    private String contentType;
    private Long size;
}
