package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import lombok.Data;
import java.util.List;

@Data
public class ContentReviewSubmitContextRespVO {
    private String processDefinitionId;
    private Integer processDefinitionVersion;
    private String directorTaskKey;
    private List<Long> roleIds;
    private List<Candidate> candidates;
    private Long previousDirectorUserId;
    public record Candidate(Long id, String nickname) { }
}
