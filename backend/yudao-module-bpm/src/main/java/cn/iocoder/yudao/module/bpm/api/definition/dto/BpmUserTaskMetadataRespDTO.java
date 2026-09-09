package cn.iocoder.yudao.module.bpm.api.definition.dto;

import lombok.Data;

import java.util.List;

/** Public user-task metadata without exposing Flowable model types. */
@Data
public class BpmUserTaskMetadataRespDTO {

    private String key;
    private String name;
    private String executionMode;
    private List<String> nextUserTaskKeys;

}
