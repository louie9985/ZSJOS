package cn.iocoder.yudao.module.zsjos.service.lead.flow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class LeadFlowDisplayTest {
    @ParameterizedTest
    @CsvSource({
        "legacy-assignment-log-12, 历史分配记录",
        "legacy-assignment-log-12 销售确认接单, 历史分配记录：销售确认接单",
        "legacy-claim-45 抢单成功, 历史抢单记录：抢单成功",
        "legacy assignment history baseline, 历史分配初始记录",
        "CRM客户咨询, CRM客户咨询",
        "备注 legacy-assignment-log-12, 备注 legacy-assignment-log-12",
        "legacy-assignment-log-12abc, legacy-assignment-log-12abc"
    })
    void convertsOnlyExactImportPrefixes(String source,String expected) {
        assertEquals(expected,LeadFlowDisplay.reason(source));
    }

    @Test
    void handlesNullAndWhitespaceWithoutLosingBusinessText() {
        assertNull(LeadFlowDisplay.reason(null)); assertEquals("",LeadFlowDisplay.reason(""));
        assertEquals("历史分配记录：原因",LeadFlowDisplay.reason("legacy-assignment-log-1\t原因"));
    }

    @Test
    void unknownSystemChoiceRecordsMissingLabelInsteadOfEnglishCode() {
        String label=ReflectionTestUtils.invokeMethod(LeadFlowRecorder.class,"status","future_status");
        LeadFlowSnapshot.Value value=ReflectionTestUtils.invokeMethod(LeadFlowRecorder.class,"choice","future_status",label);
        assertEquals("future_status",value.code()); assertEquals("历史标签未记录",value.text());
        assertEquals("有效",ReflectionTestUtils.invokeMethod(LeadFlowRecorder.class,"status","valid"));
    }
}
