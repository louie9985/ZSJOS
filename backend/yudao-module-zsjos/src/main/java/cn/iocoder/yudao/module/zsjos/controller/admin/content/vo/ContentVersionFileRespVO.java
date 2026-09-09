package cn.iocoder.yudao.module.zsjos.controller.admin.content.vo;

import lombok.Data;

@Data
public class ContentVersionFileRespVO {
    private Long id;
    private String fieldKey;
    private Integer sortNo;
    private Long infraFileId;
    private String fileUrlSnapshot;
    private String originalName;
    private String contentType;
    private Long fileSize;
    private Long uploadedByUserId;
    private String previewUrl;
}
