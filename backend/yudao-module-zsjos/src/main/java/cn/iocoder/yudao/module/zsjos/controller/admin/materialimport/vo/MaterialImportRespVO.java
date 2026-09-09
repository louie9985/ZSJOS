package cn.iocoder.yudao.module.zsjos.controller.admin.materialimport.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MaterialImportRespVO {
    private Long id;
    private String batchNo;
    private Long materialTypeId;
    private String materialTypeName;
    private Long schemaVersionId;
    private String sourceFileName;
    private String status;
    private Integer totalCount;
    private Integer successCount;
    private Integer failureCount;
    private Long createdByUserId;
    private Long confirmedByUserId;
    private LocalDateTime confirmedAt;
    private Integer version;
    private LocalDateTime createTime;
    private List<MaterialImportErrorRespVO> errors;
}
