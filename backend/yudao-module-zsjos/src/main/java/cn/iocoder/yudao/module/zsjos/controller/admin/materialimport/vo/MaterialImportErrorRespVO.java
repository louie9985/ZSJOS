package cn.iocoder.yudao.module.zsjos.controller.admin.materialimport.vo;

import lombok.Data;

@Data
public class MaterialImportErrorRespVO {
    private Long id;
    private String sheetName;
    private Integer rowNo;
    private String fieldKey;
    private String errorCode;
    private String errorMessage;
    private String rowSnapshotJson;
}
