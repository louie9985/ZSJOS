package cn.iocoder.yudao.module.zsjos.service.homemetric;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;
import cn.iocoder.yudao.module.zsjos.service.registration.MediaStudentService;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import cn.iocoder.yudao.module.zsjos.service.homemetric.source.ExistingPageMetricSources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ExistingPageMetricSourcesTest {
    @InjectMocks ExistingPageMetricSources sources;
    @Mock PermissionApi permissions;
    @Mock AdvancedFilterService filters;
    @Mock MediaStudentService media;
    @Mock cn.iocoder.yudao.module.zsjos.service.lead.LeadAppealService appeals;
    private HomeMetricSpec.Operand operand() {
        return new HomeMetricSpec.Operand(new MetricEvaluation.Operand("students","media_student",MetricAggregate.COUNT,null,
                MetricPeriod.Kind.ALL,false,null,MetricPeriod.Comparison.CURRENT),new AdvancedFilterGroupReqVO(),null);
    }
    @Test void countUsesAuthorizedTotalWithOnlyOneRowRequested() {
        when(permissions.hasAnyPermissions(7L,"zsjos:media-student:query-my")).thenReturn(true);
        when(media.getPage(eq(7L),any())).thenReturn(new PageResult<>(List.of(),1234L));
        assertEquals(new BigDecimal("1234"),sources.aggregate(operand(),null,7L).value());
        var capture=ArgumentCaptor.forClass(MyStudentPageReqVO.class);
        verify(media,times(1)).getPage(eq(7L),capture.capture());
        assertEquals(1,capture.getValue().getPageSize());
        assertNotNull(capture.getValue().getAdvancedFilter());
    }
    @Test void denialNeverCallsBusinessQuery() {
        assertEquals(MetricFormula.Status.FORBIDDEN,sources.aggregate(operand(),null,7L).status());
        verifyNoInteractions(media);
    }
    @Test void periodAddsHalfOpenBoundaryWithoutChangingOriginalOrFilter() {
        var catalog=new AdvancedFilterService().catalog("lead_appeal");
        String field=catalog.fields().stream().filter(f -> "date".equals(f.valueType())).findFirst().orElseThrow().fieldKey();
        when(filters.catalog("lead_appeal")).thenReturn(catalog);
        when(permissions.hasAnyPermissions(7L,"zsjos:lead:appeal:query")).thenReturn(true);
        when(appeals.getMetricInboxPage(any(),eq(7L))).thenReturn(new PageResult<>(List.of(),0L));
        var original=new AdvancedFilterGroupReqVO();original.setLogic("OR");original.setConditions(List.of());original.setGroups(List.of());
        var item=new HomeMetricSpec.Operand(new MetricEvaluation.Operand("a","lead_appeal",MetricAggregate.COUNT,null,
                MetricPeriod.Kind.MONTH,false,null,MetricPeriod.Comparison.CURRENT),original,field);
        var range=new MetricPeriod.Range(java.time.LocalDateTime.of(2026,10,1,0,0),java.time.LocalDateTime.of(2026,11,1,0,0));
        sources.aggregate(item,range,7L);
        var capture=ArgumentCaptor.forClass(cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.appeal.LeadAppealPageReqVO.class);verify(appeals).getMetricInboxPage(capture.capture(),eq(7L));
        var bound=capture.getValue().getAdvancedFilter();
        assertEquals("AND",bound.getLogic());assertEquals("OR",bound.getGroups().getFirst().getLogic());
        assertEquals(List.of("gte","lt"),bound.getConditions().stream().map(c->c.getOperator()).toList());
        assertEquals("2026-11-01T00:00",bound.getConditions().getLast().getValue());
        assertTrue(original.getConditions().isEmpty());
    }
    @Test void detailProjectionDoesNotReturnContactFields() {
        when(permissions.hasAnyPermissions(7L,"zsjos:media-student:query-my")).thenReturn(true);
        var student=new MediaStudentListRespVO();student.setPersonId(9L);student.setPersonNo("PERSON-9");student.setMobile("private");
        when(media.getPage(eq(7L),any())).thenReturn(new PageResult<>(List.of(student),1L));
        var row=sources.details(operand(),null,7L,1,20).getList().getFirst();
        assertEquals("PERSON-9",row.get("businessNo"));
        assertFalse(row.containsKey("mobile"));
    }
}
