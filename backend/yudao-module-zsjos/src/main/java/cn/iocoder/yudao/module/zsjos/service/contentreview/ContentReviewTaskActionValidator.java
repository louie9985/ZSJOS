package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.module.bpm.api.task.BpmTaskActionValidator;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskActionContext;
import jakarta.annotation.Resource;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.module.zsjos.service.contentreview.ContentReviewBatchService.BUSINESS_KEY_PREFIX;

@Component
public class ContentReviewTaskActionValidator implements BpmTaskActionValidator, Ordered {

    @Resource private ContentReviewBatchService batchService;

    @Override
    public void validate(BpmTaskActionContext context) {
        if (context.getBusinessKey() != null && context.getBusinessKey().startsWith(BUSINESS_KEY_PREFIX)) {
            batchService.validateTaskAction(context);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
