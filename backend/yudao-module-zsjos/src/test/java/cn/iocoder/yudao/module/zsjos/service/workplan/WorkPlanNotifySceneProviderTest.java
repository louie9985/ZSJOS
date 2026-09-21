package cn.iocoder.yudao.module.zsjos.service.workplan;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.workplan.WorkPlanDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.workplan.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class WorkPlanNotifySceneProviderTest {
 @InjectMocks WorkPlanNotifySceneProvider provider;
 @Mock WorkTaskMapper taskMapper;
 @Mock WorkPlanMapper planMapper;
 @Test void summaryDoesNotResolveSameNumberTaskAsPlan() {
  var plan=new WorkPlanDO();plan.setId(1L);plan.setTitle("计划");when(planMapper.selectById(1L)).thenReturn(plan);
  var values=provider.resolveVariables(NotifyBusinessEvent.builder().bizType("work_plan").bizId(1L).build(),null);
  assertEquals("计划",values.get("plan.title"));verifyNoInteractions(taskMapper);
 }
}
