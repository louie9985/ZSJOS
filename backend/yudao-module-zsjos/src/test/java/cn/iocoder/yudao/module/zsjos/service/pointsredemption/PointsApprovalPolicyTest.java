package cn.iocoder.yudao.module.zsjos.service.pointsredemption;
import cn.iocoder.yudao.module.bpm.api.definition.BpmDefinitionReadApi;
import cn.iocoder.yudao.module.bpm.api.definition.dto.BpmProcessDefinitionMetadataRespDTO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class PointsApprovalPolicyTest {
    @Test void delegatesApprovalSettingsToPublishedBpmDefinition() {
        var api = mock(BpmDefinitionReadApi.class);
        var policy = new PointsApprovalPolicy();
        ReflectionTestUtils.setField(policy, "definitions", api);
        var definition = new BpmProcessDefinitionMetadataRespDTO();
        definition.setMandatoryHumanChain(false);
        definition.setConsecutiveApprovalDeduplicationChain(false);
        when(api.getPublishedProcessDefinition(PointsApprovalPolicy.PROCESS_KEY)).thenReturn(definition);
        assertSame(definition, policy.validate(7L));
        definition.setSuspended(true);
        assertEquals(1_090_296_005, assertThrows(ServiceException.class, () -> policy.validate(7L)).getCode());
        when(api.getPublishedProcessDefinition(PointsApprovalPolicy.PROCESS_KEY)).thenReturn(null);
        assertEquals(1_090_296_005, assertThrows(ServiceException.class, () -> policy.validate(7L)).getCode());
    }
}
