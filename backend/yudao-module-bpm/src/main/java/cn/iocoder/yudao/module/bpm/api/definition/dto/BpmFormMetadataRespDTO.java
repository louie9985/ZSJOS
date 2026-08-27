package cn.iocoder.yudao.module.bpm.api.definition.dto;

import lombok.Data;
import java.util.List;

@Data
public class BpmFormMetadataRespDTO {
    private Long id;
    private String name;
    private Integer status;
    private String conf;
    private List<String> fields;
    private String remark;
}
