package cn.iocoder.yudao.module.zsjos.service.examcollection;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExamCollectionContentTest {
    @Test void preservesAuthoredFormattingAndRemovesActiveContent() {
        String html = ExamCollectionContent.clean("<p style='text-align:center;color:#ff0000;font-size:24px;position:fixed'>考试材料</p><table style='width:960px'><colgroup><col style='width:320px'></colgroup><tr><td colspan='2'>内容</td></tr></table><img src='https://example.com/a.png' onerror='alert(1)'><script>alert(1)</script><a href='javascript:alert(1)'>链接</a>");
        assertTrue(html.contains("text-align:center"));
        assertTrue(html.contains("font-size:24px"));
        assertTrue(html.contains("width:960px"));
        assertTrue(html.contains("width:320px"));
        assertTrue(html.contains("colspan=\"2\""));
        assertFalse(html.contains("position"));
        assertFalse(html.contains("onerror"));
        assertFalse(html.contains("script"));
    }
    @Test void oldTasksHaveEmptyBody() {
        assertEquals("", ExamCollectionContent.clean(null));
        assertEquals("", ExamCollectionContent.clean(""));
    }
}
