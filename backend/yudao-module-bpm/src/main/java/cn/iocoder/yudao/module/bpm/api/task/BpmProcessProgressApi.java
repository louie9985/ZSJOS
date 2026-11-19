package cn.iocoder.yudao.module.bpm.api.task;

import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessProgressDTO;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Internal API. Caller must authorize the business object; BPM independently enforces tenant scope. */
public interface BpmProcessProgressApi {
    Map<String, List<BpmProcessProgressDTO.PendingTask>> getCurrentTasks(Set<String> processInstanceIds);
    BpmProcessProgressDTO getProgress(String processInstanceId, Long viewerId);
    List<Comment> getComments(String processInstanceId);
    void createTaskComment(Long userId, String taskId, String message);

    record Comment(String id, String taskId, String processInstanceId, String type, String message,
                   java.time.LocalDateTime createTime, CommentUser user) {}
    record CommentUser(Long id, String nickname) {}

    boolean isParticipant(String processInstanceId, Long userId);
}
