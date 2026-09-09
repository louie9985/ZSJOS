package cn.iocoder.yudao.module.zsjos.controller.admin.content.vo;

import lombok.Data;

@Data
public class ContentUploadRespVO {
    private Long fileId;
    private String name;
    private String contentType;
    private Long size;
    private String previewUrl;
}
