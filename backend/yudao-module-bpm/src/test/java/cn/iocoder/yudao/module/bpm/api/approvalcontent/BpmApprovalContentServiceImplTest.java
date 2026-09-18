package cn.iocoder.yudao.module.bpm.api.approvalcontent;

import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalBriefVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalDetailVO;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessTaskApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmTaskRespDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BpmApprovalContentServiceImplTest {

    @Mock private BpmProcessTaskApi processTaskApi;
    @Mock private BpmApprovalContentRegistry registry;
    @InjectMocks private BpmApprovalContentServiceImpl service;

    @Test
    void unresolvedTaskIsTreatedAsDenied() {
        // 任务不属于本人时 BpmProcessTaskApi 返回 null，此时不应再解析业务内容。
        when(processTaskApi.getTodoTask(9L, "t1")).thenReturn(null);

        assertNull(service.getBrief("t1", "todo", 9L));
        assertTrue(service.getBriefMap(List.of("t1"), "todo", 9L).isEmpty());
    }

    @Test
    void taskWithoutBusinessKeyIsSkipped() {
        BpmTaskRespDTO task = new BpmTaskRespDTO();
        task.setId("t1");
        when(processTaskApi.getTodoTask(9L, "t1")).thenReturn(task);

        assertNull(service.getBrief("t1", "todo", 9L));
    }

    @Test
    void doneViewReadsFromDoneTask() {
        BpmTaskRespDTO task = new BpmTaskRespDTO();
        task.setBusinessKey("withdrawal:1");
        when(processTaskApi.getDoneTask(9L, "t2")).thenReturn(task);
        when(registry.resolve("withdrawal:1")).thenReturn(java.util.Optional.empty());

        assertNull(service.getBrief("t2", "done", 9L));
    }

    @Test
    void providerFailureForOneTaskDoesNotBreakTheBatch() {
        BpmTaskRespDTO bad = new BpmTaskRespDTO();
        bad.setBusinessKey("withdrawal:1");
        BpmTaskRespDTO good = new BpmTaskRespDTO();
        good.setBusinessKey("withdrawal:2");
        when(processTaskApi.getTodoTask(9L, "t1")).thenReturn(bad);
        when(processTaskApi.getTodoTask(9L, "t2")).thenReturn(good);

        BpmApprovalContentProvider exploding = new ThrowingProvider();
        when(registry.resolve("withdrawal:1")).thenReturn(java.util.Optional.of(exploding));
        BpmApprovalBriefVO expected = new BpmApprovalBriefVO();
        expected.setTitle("WD2");
        when(registry.resolve("withdrawal:2")).thenReturn(java.util.Optional.of(
                new FixedProvider(expected)));

        Map<String, BpmApprovalBriefVO> result = service.getBriefMap(List.of("t1", "t2"), "todo", 9L);

        assertEquals(1, result.size());
        assertEquals("WD2", result.get("t2").getTitle());
    }

    private static final class ThrowingProvider implements BpmApprovalContentProvider {
        @Override
        public String bizType() {
            return "withdrawal";
        }

        @Override
        public String businessKeyPrefix() {
            return "withdrawal:";
        }

        @Override
        public String parseBusinessId(String businessKey) {
            return businessKey.substring("withdrawal:".length());
        }

        @Override
        public BpmApprovalBriefVO brief(String businessId, Long viewerId) {
            throw new IllegalStateException("boom");
        }

        @Override
        public BpmApprovalDetailVO detail(String businessId, Long viewerId) {
            throw new IllegalStateException("boom");
        }
    }

    private record FixedProvider(BpmApprovalBriefVO brief) implements BpmApprovalContentProvider {
        @Override
        public String bizType() {
            return "withdrawal";
        }

        @Override
        public String businessKeyPrefix() {
            return "withdrawal:";
        }

        @Override
        public String parseBusinessId(String businessKey) {
            return businessKey.substring("withdrawal:".length());
        }

        @Override
        public BpmApprovalBriefVO brief(String businessId, Long viewerId) {
            return brief;
        }

        @Override
        public BpmApprovalDetailVO detail(String businessId, Long viewerId) {
            return null;
        }
    }
}
