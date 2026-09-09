package cn.iocoder.yudao.module.zsjos.controller.admin.material.vo;

import lombok.Data;

@Data
public class MaterialUploadRespVO {
    private Long fileId;
    private String name;
    private String contentType;
    private Long size;
    private String previewUrl;
}
