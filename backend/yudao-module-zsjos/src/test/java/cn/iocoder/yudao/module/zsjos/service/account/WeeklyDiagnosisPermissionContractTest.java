package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.module.zsjos.controller.admin.account.MediaAccountDiagnosisController;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosPermission;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class WeeklyDiagnosisPermissionContractTest {
    @Test void everyWeeklyEndpointRetainsConfiguredFeaturePermission(){
        var names=Set.of("overview","nodes","preview","entryMetrics","submit");
        for(var method:MediaAccountDiagnosisController.class.getDeclaredMethods())if(names.contains(method.getName())){
            var feature=method.getAnnotation(PreAuthorize.class);assertNotNull(feature,method.getName());
            assertTrue(feature.value().contains("zsjos:media-account:"));
        }
    }
    @Test void objectBearingWeeklyMethodsHaveIndependentObjectChecks(){
        for(var method:WeeklyDiagnosisService.class.getDeclaredMethods())if(Set.of("overview","nodes","preview","history","submit").contains(method.getName())){
            var object=method.getAnnotation(ZsjosPermission.class);assertNotNull(object,method.getName());
            assertEquals("media-account",object.bizType());assertEquals("#accountId",object.bizId());
            assertEquals(method.getName().equals("history")?"read":"edit",object.action());
        }
    }
}
