package cn.iocoder.yudao.module.zsjos.service.feedback;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.module.zsjos.service.feedback.FeedbackConstants.PROCESS_DEFINITION_KEY;

@Component
public class FeedbackProcessStatusListener extends BpmProcessInstanceStatusEventListener {

    @Resource
    private FeedbackService feedbackService;

    @Override
    protected String getProcessDefinitionKey() {
        return PROCESS_DEFINITION_KEY;
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        feedbackService.handleProcessResult(event);
    }
}
