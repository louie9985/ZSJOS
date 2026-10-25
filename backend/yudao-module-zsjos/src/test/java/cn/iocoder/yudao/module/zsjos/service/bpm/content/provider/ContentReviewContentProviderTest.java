package cn.iocoder.yudao.module.zsjos.service.bpm.content.provider;

import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewBatchItemRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo.ContentReviewBatchRespVO;
import cn.iocoder.yudao.module.zsjos.service.contentreview.ContentReviewBatchService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ContentReviewContentProviderTest {
    @Test void publicBpmCardUsesOnlyFrozenTimingAndKeepsOldMissing() {
        var service = mock(ContentReviewBatchService.class);
        var provider = new ContentReviewContentProvider();
        ReflectionTestUtils.setField(provider, "batchService", service);
        var batch = new ContentReviewBatchRespVO();
        batch.setItems(List.of());
        batch.setContextSnapshot(java.util.Map.of("accountSnapshots", List.of(
                java.util.Map.of("nickname", "历史账号", "sStageLabel", "历史阶段", "sStageEnteredAt", 1792425600000L,
                        "sStageDurationAsOf", 1792512000000L, "sStageDurationDays", 1, "sStageTimingSource", "HISTORY_RECONSTRUCTED"),
                java.util.Map.of("nickname", "旧账号"))));
        when(service.get(12L, 7L)).thenReturn(batch);
        var groups = provider.detail("12", 7L).getGroups();
        org.junit.jupiter.api.Assertions.assertTrue(groups.get(2).getFields().get(2).getValue().contains("已维持 1 天"));
        org.junit.jupiter.api.Assertions.assertTrue(groups.get(2).getFields().get(2).getValue().contains("根据诊断历史恢复"));
        assertEquals("阶段时长未记录", groups.get(3).getFields().get(2).getValue());
        verify(service).get(12L, 7L);
    }

    @Test
    void locatesExactBatchInBothSummaryAndDetail() {
        var service = mock(ContentReviewBatchService.class);
        var provider = new ContentReviewContentProvider();
        ReflectionTestUtils.setField(provider, "batchService", service);
        var batch = new ContentReviewBatchRespVO();
        batch.setItems(List.of());
        when(service.get(12L, 7L)).thenReturn(batch);
        assertEquals(12L, provider.brief("12", 7L).getQuery().get("batchId"));
        assertEquals(12L, provider.detail("12", 7L).getQuery().get("batchId"));
    }

    @Test
    void retainsBothOpinionsWithoutAttachmentsAndDistinguishesEmptyFromPending() {
        var service = mock(ContentReviewBatchService.class);
        var provider = new ContentReviewContentProvider();
        ReflectionTestUtils.setField(provider, "batchService", service);
        var reviewed = new ContentReviewBatchItemRespVO();
        reviewed.setDirectorDecision("APPROVED");
        reviewed.setDirectorComment("编导意见\n第二行");
        reviewed.setFinalDecision("RETURNED");
        reviewed.setFinalComment("终审意见");
        var empty = new ContentReviewBatchItemRespVO();
        empty.setDirectorDecision("APPROVED");
        var batch = new ContentReviewBatchRespVO();
        batch.setItems(List.of(reviewed, empty));
        when(service.get(12L, 7L)).thenReturn(batch);
        var groups = provider.detail("12", 7L).getGroups();
        assertEquals(4, groups.size());
        assertEquals("编导意见\n第二行", groups.get(2).getFields().get(0).getValue());
        assertEquals("终审意见", groups.get(2).getFields().get(1).getValue());
        assertEquals("未填写意见", groups.get(3).getFields().get(0).getValue());
        assertEquals("尚未审核", groups.get(3).getFields().get(1).getValue());
        verify(service).get(12L, 7L);
    }
}
