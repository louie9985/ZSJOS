package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.LeadDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.task.BusinessTaskDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.LeadMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.module.zsjos.enums.LeadConstants.TASK_TYPE_FIRST_FOLLOW_UP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeadBusinessTaskSceneProviderTest {

    @InjectMocks
    private LeadBusinessTaskSceneProvider provider;
    @Mock
    private LeadMapper leadMapper;

    @Test
    void usesLeadNumberWithoutInternalIdFallback() {
        BusinessTaskDO task = new BusinessTaskDO();
        task.setId(1L);
        task.setBizId(8L);
        task.setTaskType(TASK_TYPE_FIRST_FOLLOW_UP);
        when(leadMapper.selectBatchIds(List.of(8L))).thenReturn(List.of(
                new LeadDO().setId(8L).setLeadNo("KZ202608160000000008")));

        var display = provider.getDisplayMap(List.of(task)).get(1L);

        assertEquals("首次跟进：KZ202608160000000008", display.title());
    }
}
