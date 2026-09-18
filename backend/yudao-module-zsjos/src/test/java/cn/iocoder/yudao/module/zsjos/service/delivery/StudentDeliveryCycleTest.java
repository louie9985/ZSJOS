package cn.iocoder.yudao.module.zsjos.service.delivery;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardSubmissionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardSubmissionMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.delivery.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.delivery.*;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentDeliveryCycleTest {
    @Mock PositioningCardSubmissionMapper submissions;
    @InjectMocks DeliveryPositioningSource source;
    @Test void weekBoundaryIsMondayIncludingYearBoundary() {
        assertEquals(LocalDateTime.of(2025,12,29,0,0),StudentDeliveryCycleService.weekStart(LocalDate.of(2026,1,4)));
        assertEquals(LocalDateTime.of(2026,1,5,0,0),StudentDeliveryCycleService.weekStart(LocalDate.of(2026,1,5)));
    }
    @Test void sourceRejectsPendingEvidenceAndKeepsFrozenText() {
        var account=new MediaAccountDO().setStudentPersonId(1L).setCreateServiceRelationId(2L);
        var missing=new PositioningCardSubmissionDO().setStatus("confirmed").setEvidenceRequired(true).setEvidenceJson("[]");
        var ready=new PositioningCardSubmissionDO().setId(9L).setCardId(3L).setSubmissionNo(2).setStatus("confirmed").setEvidenceRequired(true)
                .setEvidenceJson("[{\"id\":1}]").setValuesSnapshotJson("{\"pc_delivery_s0\":\"冻结交付约定\"}").setDictSnapshotJson("{}");
        when(submissions.selectList(any(Wrapper.class))).thenReturn(List.of(missing,ready));
        assertSame(ready,source.latest(account));
        assertEquals("冻结交付约定",source.snapshot(ready,"S0").get("agreement"));
        assertEquals(9L,source.snapshot(ready,"S0").get("submissionId"));
    }
    @Test void absentServiceDoesNotInferFromOtherStudentAccounts() {
        assertNull(source.latest(new MediaAccountDO().setStudentPersonId(1L))); verifyNoInteractions(submissions);
    }
    @Test void s6RequiresAllThreeParallelConfirmations() {
        var plans=mock(StudentDeliveryPlanMapper.class);var stages=mock(StudentDeliveryStageMapper.class);var configs=mock(StudentDeliveryConfigMapper.class);
        var service=new StudentDeliveryPlanServiceImpl(plans,stages,configs);
        when(stages.selectCount(any(Wrapper.class))).thenReturn(2L);
        service.createNextStages(1L,2L,3L,"S5",LocalDateTime.of(2026,9,18,12,0));
        verify(stages,never()).insert(any(StudentDeliveryStageDO.class));
        when(stages.selectCount(any(Wrapper.class))).thenReturn(3L,0L);
        service.createNextStages(1L,2L,3L,"S3",LocalDateTime.of(2026,9,18,12,0));
        verify(stages).insert(argThat((StudentDeliveryStageDO stage)->"S6".equals(stage.getStageCode())&&"MONITORING".equals(stage.getStatus())));
    }
}
