package cn.iocoder.yudao.module.bpm.api.definition.dto;

import lombok.Data;

import java.util.List;

/** Public user-task metadata without exposing Flowable model types. */
@Data
public class BpmUserTaskMetadataRespDTO {

    private String key;
    private String name;
    private String executionMode;
    /** Whether this node consumes the assignees supplied at process creation. */
    private Boolean startUserSelectAssignees;
    /** Whether rejection ends the round instead of returning to another user task. */
    private Boolean rejectEndsProcess;
    private List<String> nextUserTaskKeys;

}
