package cn.iocoder.yudao.module.zsjos.controller.admin.material.vo;

import lombok.Data;

@Data
public class MaterialFileRespVO {
    private Long id;
    private String fieldKey;
    private Integer groupIndex;
    private Long fileId;
    private String name;
    private String contentType;
    private Long size;
    private String previewUrl;
}
