package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskPageReqDTO;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskRespDTO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.contentreview.ContentReviewBatchDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.contentreview.ContentReviewBatchItemMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class ContentReviewAccessService {

    private static final int TASK_PAGE_SIZE = 100;

    @Resource private BpmProcessTaskApi processTaskApi;
    @Resource private ContentReviewBatchItemMapper itemMapper;

    public Set<String> getCurrentTaskProcessInstanceIds(Long userId, Collection<String> definitionKeys) {
        Set<String> result = new LinkedHashSet<>();
        for (String definitionKey : definitionKeys) {
            if (definitionKey == null || definitionKey.isBlank()) continue;
            int pageNo = 1;
            long total;
            do {
                BpmTaskPageReqDTO request = new BpmTaskPageReqDTO();
                request.setPageNo(pageNo++);
                request.setPageSize(TASK_PAGE_SIZE);
                request.setProcessDefinitionKey(definitionKey);
                var page = processTaskApi.getTodoTaskPage(userId, request);
                page.getList().stream()
                        .filter(task -> task.getBusinessKey() != null
                                && task.getBusinessKey().startsWith(ContentReviewBatchService.BUSINESS_KEY_PREFIX))
                        .map(BpmTaskRespDTO::getProcessInstanceId)
                        .filter(Objects::nonNull)
                        .forEach(result::add);
                total = page.getTotal();
            } while ((long) (pageNo - 1) * TASK_PAGE_SIZE < total);
        }
        return result;
    }

    public boolean hasCurrentTask(ContentReviewBatchDO batch, Long userId) {
        if (batch.getProcessInstanceId() == null || batch.getProcessDefinitionKey() == null) return false;
        BpmTaskPageReqDTO request = new BpmTaskPageReqDTO();
        request.setPageNo(1);
        request.setPageSize(2);
        request.setProcessDefinitionKey(batch.getProcessDefinitionKey());
        request.setProcessInstanceIds(List.of(batch.getProcessInstanceId()));
        return processTaskApi.getTodoTaskPage(userId, request).getList().stream().anyMatch(task ->
                Objects.equals(task.getProcessInstanceId(), batch.getProcessInstanceId())
                        && Objects.equals(task.getBusinessKey(), batch.getBusinessKey()));
    }

    public boolean hasReviewedHistory(Long batchId, Long userId) {
        return itemMapper.existsReviewedBy(batchId, userId);
    }
}
