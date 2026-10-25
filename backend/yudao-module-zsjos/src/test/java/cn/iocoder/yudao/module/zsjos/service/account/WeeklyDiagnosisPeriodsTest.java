package cn.iocoder.yudao.module.zsjos.service.account;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WeeklyDiagnosisPeriodsTest {
    @Test void fixedNodesHaveCorrectParts(){
        assertEquals(List.of(7),WeeklyDiagnosisPeriods.intervals(1));
        assertEquals(List.of(7,14),WeeklyDiagnosisPeriods.intervals(2));
        assertEquals(List.of(7),WeeklyDiagnosisPeriods.intervals(3));
        assertEquals(List.of(7,14,28),WeeklyDiagnosisPeriods.intervals(4));
        assertEquals(List.of(7,14,28),WeeklyDiagnosisPeriods.intervals(8));
    }
    @Test void submissionTimeCannotMoveTheAnchor(){
        var anchor=LocalDateTime.parse("2026-10-01T12:00:00");
        assertEquals(2,WeeklyDiagnosisPeriods.week(anchor,LocalDateTime.parse("2026-10-15T00:00:00")));
        assertEquals("10:round:2026-10-15",WeeklyDiagnosisPeriods.key(10L,"round",LocalDateTime.parse("2026-10-15T00:00:00")));
        assertThrows(IllegalArgumentException.class,()->WeeklyDiagnosisPeriods.week(anchor,anchor.plusDays(6)));
    }
    @Test void zeroDenominatorIsNotInventedAsZeroPercent(){
        assertNull(DiagnosisMetricService.rate(0,0));
        assertEquals(new java.math.BigDecimal("0.2500"),DiagnosisMetricService.rate(1,4));
    }
    @Test void persistedNodeDatesRoundTripWithApplicationJsonContract(){
        var due=LocalDateTime.parse("2026-10-29T00:00:00");
        var node=new cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.WeeklyDiagnosisVO.Node("key","round",4,due,List.of(),List.of());
        assertEquals(node,cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(node),node.getClass()));
    }
}
