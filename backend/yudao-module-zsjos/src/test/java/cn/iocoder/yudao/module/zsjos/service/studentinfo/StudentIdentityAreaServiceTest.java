package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.ip.IdentityAreaApi;
import cn.iocoder.yudao.module.system.api.ip.dto.IdentityAreaRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentIdentityAreaServiceTest {
    @InjectMocks StudentIdentityAreaService service;
    @Mock IdentityAreaApi identityAreas;
    @Spy StudentInfoFields fields = new StudentInfoFields();
    private List<StudentInfoVO.Field> definitions() {
        return fields.presets().stream().filter(f -> Set.of("id_card","household_area").contains(f.getKey())).toList();
    }
    private String card(String code) {
        String body = code+"20000101001";
        int[] weights={7,9,10,5,8,4,2,1,6,3,7,9,10,5,8,4,2}; int sum=0;
        for(int i=0;i<17;i++) sum+=(body.charAt(i)-'0')*weights[i];
        return body+"10X98765432".charAt(sum%11);
    }
    private StudentInfoVO.IdentityAreaInput input(String mode) {
        var in = new StudentInfoVO.IdentityAreaInput(); in.setCode("420621"); in.setMode(mode); return in;
    }
    private void resolved(String status) {
        var result = new IdentityAreaRespDTO(); result.setCode("420621"); result.setStatus(status);
        result.setLabel("湖北省 / 襄樊市 / 襄阳县"); result.setDataVersion("test");
        when(identityAreas.resolve("420621")).thenReturn(result);
    }
    @Test void historicalSnapshotDoesNotRequireCurrentAreaNode() {
        resolved("HISTORICAL");
        var result=service.validate(definitions(),Map.of("id_card",card("420621")),input("AUTO"));
        var area=result.stream().filter(v->"household_area".equals(v.getFieldKey())).findFirst().orElseThrow();
        assertNull(area.getAreaCodePath()); assertEquals("湖北省 / 襄樊市 / 襄阳县",area.getAreaLabelSnapshot());
        assertTrue(area.getIdentityAreaSnapshot().contains("HISTORICAL"));
    }
    @Test void unknownAllowsManualButCannotForgeAutomaticResult() {
        resolved("UNRECOGNIZED");
        assertThrows(ServiceException.class,()->service.validate(definitions(),Map.of("id_card",card("420621")),input("AUTO")));
        var in=input("MANUAL_TEXT"); in.setText("本人填写的历史地区");
        assertEquals(2,service.validate(definitions(),Map.of("id_card",card("420621")),in).size());
    }
    @Test void knownCodeCannotBeOverriddenByClaimingManualFallback() {
        resolved("HISTORICAL"); var in=input("MANUAL_TEXT"); in.setText("其他地区");
        assertThrows(ServiceException.class,()->service.validate(definitions(),Map.of("id_card",card("420621")),in));
    }
    @Test void changedIdentityCannotReusePreviousArea() {
        assertThrows(ServiceException.class,()->service.validate(definitions(),Map.of("id_card",card("362421")),input("AUTO")));
        verifyNoInteractions(identityAreas);
    }
}
