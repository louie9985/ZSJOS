package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.module.zsjos.service.contentreview.ContentReviewBatchService.BUSINESS_KEY_PREFIX;

@Component
public class ContentReviewProcessStatusListener implements ApplicationListener<BpmProcessInstanceStatusEvent> {

    @Resource private ContentReviewBatchService batchService;

    @Override
    public void onApplicationEvent(BpmProcessInstanceStatusEvent event) {
        if (event.getBusinessKey() != null && event.getBusinessKey().startsWith(BUSINESS_KEY_PREFIX)) {
            batchService.handleProcessResult(event);
        }
    }
}
