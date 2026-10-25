package cn.iocoder.yudao.module.zsjos.service.delivery;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.positioning.PositioningCardSubmissionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.positioning.PositioningCardSubmissionMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryPositioningSourceTest {
    @Mock PositioningCardSubmissionMapper submissions;
    @InjectMocks DeliveryPositioningSource service;
    private final MediaAccountDO account = new MediaAccountDO().setStudentPersonId(2L).setCreateServiceRelationId(3L);

    private PositioningCardSubmissionDO completed(long id, String values) {
        return new PositioningCardSubmissionDO().setId(id).setStatus("confirmed")
                .setSubmittedAt(LocalDateTime.of(2026, 10, (int) id, 9, 0))
                .setStudentDecidedAt(LocalDateTime.of(2026, 10, (int) id, 10, 0))
                .setEvidenceJson("[{\"id\":1,\"uploadedAt\":\"2026-10-01T09:00:00\"}]")
                .setValuesSnapshotJson(values);
    }

    @Test void firstCompletionUsesLaterOfConfirmationAndReceipt() {
        var first = completed(1, "{\"pc_days7\":\"目标\"}");
        first.setEvidenceJson("[{\"id\":1,\"uploadedAt\":\"2026-10-02T12:00:00\"}]");
        when(submissions.selectList(any(Wrapper.class))).thenReturn(List.of(first));
        assertSame(first, service.diagnosisRound(account));
        assertEquals(LocalDateTime.of(2026,10,2,12,0), DeliveryPositioningSource.effectiveAt(first));
    }

    @Test void changedCompletedVersionStartsRoundAndRevertingContentAlsoStartsRound() {
        var first = completed(1, "{\"pc_days7\":\"旧目标\"}").setStatus("superseded");
        var changed = completed(2, "{\"pc_days7\":\"新目标\"}");
        when(submissions.selectList(any(Wrapper.class))).thenReturn(List.of(first, changed));
        assertSame(changed, service.diagnosisRound(account));
        var reverted = completed(3, first.getValuesSnapshotJson());
        when(submissions.selectList(any(Wrapper.class))).thenReturn(List.of(first, changed, reverted));
        assertSame(reverted, service.diagnosisRound(account));
    }

    @Test void unchangedCompletedVersionIgnoresJsonOrderWorkflowAndExtraReceipt() {
        var first = completed(1, "{\"a\":1,\"b\":2}").setStatus("superseded");
        var unchanged = completed(2, "{\"b\":2,\"a\":1}").setDirectorUserId(99L);
        unchanged.setEvidenceJson("[{\"id\":2,\"uploadedAt\":\"2026-10-05T12:00:00\"}]");
        when(submissions.selectList(any(Wrapper.class))).thenReturn(List.of(first, unchanged));
        assertSame(first, service.diagnosisRound(account));
    }

    @Test void revisionDraftReviewAndMissingEvidenceDoNotReset() {
        var first = completed(1, "{\"a\":1}");
        for (String status : List.of("co_creating", "operator_reviewing", "student_evidence_pending", "student_agreed")) {
            var pending = completed(2, "{\"a\":2}").setStatus(status);
            when(submissions.selectList(any(Wrapper.class))).thenReturn(List.of(first, pending));
            assertSame(first, service.diagnosisRound(account));
        }
        var missing = completed(2, "{\"a\":2}").setEvidenceJson("[]");
        when(submissions.selectList(any(Wrapper.class))).thenReturn(List.of(first, missing));
        assertSame(first, service.diagnosisRound(account));
    }

    @Test void sourceQueryIsStudentAndServiceScopedAndDoesNotUseAccountCreation() {
        when(submissions.selectList(any(Wrapper.class))).thenAnswer(call -> {
            var query = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) call.getArgument(0);
            // Initialize metadata like a mapper does before inspecting the scoped query.
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("service_relation_id") && sql.contains("student_person_id"));
            assertFalse(sql.contains("account_id"));
            assertTrue(query.getParamNameValuePairs().containsValue(2L));
            assertTrue(query.getParamNameValuePairs().containsValue(3L));
            return List.of();
        });
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), PositioningCardSubmissionDO.class);
        assertNull(service.diagnosisRound(account));
    }

    @Test void noServiceCannotReadAnotherServicesClock() {
        assertNull(service.diagnosisRound(new MediaAccountDO().setStudentPersonId(2L)));
        verifyNoInteractions(submissions);
    }
}
