package cn.iocoder.yudao.module.zsjos.service.media;

import cn.iocoder.yudao.module.system.api.notify.NotifySceneProvider;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneRespDTO;
import cn.iocoder.yudao.module.zsjos.service.contentreview.ContentReviewNotifySceneProvider;
import cn.iocoder.yudao.module.zsjos.service.studentcontact.StudentContactNotifySceneProvider;
import cn.iocoder.yudao.module.zsjos.service.workorder.WorkOrderNotifySceneProvider;
import cn.iocoder.yudao.module.zsjos.service.workplan.WorkPlanNotifySceneProvider;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MediaNotificationDefaultsTest {
    @Test void everyDefaultUsesRegisteredRecipientsActionsAndTiming() {
        Map<String,NotifySceneRespDTO> scenes=new HashMap<>();
        List<NotifySceneProvider> providers=List.of(new MediaNotifySceneProvider(),new ContentReviewNotifySceneProvider(),
                new StudentContactNotifySceneProvider(),new WorkOrderNotifySceneProvider(),new WorkPlanNotifySceneProvider());
        providers.forEach(p->p.getScenes().forEach(s->scenes.put(s.getCode(),s)));
        var defaults=MediaNotificationTenantInitializer.defaultRules();
        assertEquals(59,defaults.size());
        for(var rule:defaults) {
            var scene=scenes.get(rule.getSceneCode());assertNotNull(scene,rule.getSceneCode());
            var roles=scene.getRecipientRoles().stream().map(r->r.getCode()).toList();
            assertTrue(roles.containsAll(rule.getRecipientRoles()),rule.getSceneCode());
            assertTrue(scene.getAllowedActions().contains(rule.getActionType()),rule.getSceneCode());
            if(rule.getTimingStage()!=null) assertEquals(true,scene.getTimed(),rule.getSceneCode());
        }
    }
}
