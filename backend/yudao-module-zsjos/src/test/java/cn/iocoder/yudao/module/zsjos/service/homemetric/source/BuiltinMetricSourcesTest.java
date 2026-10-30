package cn.iocoder.yudao.module.zsjos.service.homemetric.source;
import cn.iocoder.yudao.module.zsjos.service.homemetric.*;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.zsjos.service.task.BusinessTaskService;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class BuiltinMetricSourcesTest {
 @InjectMocks BuiltinMetricSources sources;
 @Mock PermissionApi permissions;
 @Mock BpmProcessTaskApi bpm;
 @Mock BusinessTaskService business;
 @Mock AdvancedFilterService filters;
 HomeMetricSpec.Operand operand(MetricPeriod.Kind period){return new HomeMetricSpec.Operand(new MetricEvaluation.Operand("a","bpm_todo",MetricAggregate.COUNT,null,period,false,null,MetricPeriod.Comparison.CURRENT),new AdvancedFilterGroupReqVO(),null);}
 @Test void deniesBeforeCallingPublicTaskApi(){assertThrows(org.springframework.security.access.AccessDeniedException.class,()->sources.details(operand(MetricPeriod.Kind.ALL),null,7L,1,10));verifyNoInteractions(bpm,business);}
 @Test void rejectsUnsupportedTimeFilterInsteadOfIgnoringIt(){assertThrows(IllegalArgumentException.class,()->sources.validate(operand(MetricPeriod.Kind.MONTH)));verifyNoInteractions(bpm,business);}
 @Test void rejectsUnsupportedAdvancedFilter(){var op=operand(MetricPeriod.Kind.ALL);when(filters.hasConditions(op.filter())).thenReturn(true);assertThrows(IllegalArgumentException.class,()->sources.validate(op));}
}
