package cn.iocoder.yudao.module.zsjos.service.material;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.BUSINESS_KEY_PREFIX;

/** Material types can bind different BPM keys, so routing uses the frozen business-key namespace. */
@Component
public class MaterialProcessStatusListener implements ApplicationListener<BpmProcessInstanceStatusEvent> {

    @Resource
    private MaterialService materialService;

    @Override
    public void onApplicationEvent(BpmProcessInstanceStatusEvent event) {
        if (event.getBusinessKey() != null && event.getBusinessKey().startsWith(BUSINESS_KEY_PREFIX)) {
            materialService.handleProcessResult(event);
        }
    }
}
