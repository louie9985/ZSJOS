package cn.iocoder.yudao.module.bpm.api.definition.dto;

import lombok.Data;

import java.util.List;

@Data
public class BpmProcessDefinitionMetadataRespDTO {
    private String id;
    private String key;
    private String name;
    private Integer version;
    private String deploymentId;
    private Boolean suspended;
    private String category;
    private Long formId;
    private String description;
    /** Strict unconditional chain including the Simple submission task. */
    private Boolean mandatoryHumanChain;
    /** Unconditional chain using BPM's consecutive-same-approver deduplication policy. */
    private Boolean consecutiveApprovalDeduplicationChain;
    private List<String> orderedUserTaskKeys;
    private Boolean simpleSequentialApproval;
    private List<BpmUserTaskMetadataRespDTO> userTasks;
}
