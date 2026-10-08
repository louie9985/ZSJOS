package cn.iocoder.yudao.module.zsjos.service.examcalendar;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ExamCalendarNoteContentTest {
    @Test void preservesFormattingTablesAndStableImages() {
        var clean = ExamCalendarNoteContent.clean("<h2>免考项目</h2><p><strong>说明</strong><em>斜体</em><u>下划线</u><span style='color:rgb(255, 0, 0);text-align:center'>重点</span></p><ol><li>项目甲</li></ol><table><tr><td colspan='2'>表格</td></tr></table><img src='exam-note-image:123'>");
        assertEquals(Set.of(123L), clean.fileIds());
        var doc = Jsoup.parse(clean.html());
        assertEquals("rgb(255, 0, 0)", doc.selectFirst("span").attr("style").split(";")[0].substring(6));
        assertEquals("exam-note-image:123", doc.selectFirst("img").attr("src"));
        assertEquals(1, doc.select("table td[colspan=2]").size());
        assertEquals(1, doc.select("strong").size());
        assertEquals(clean.html(), ExamCalendarNoteContent.clean(clean.html()).html());
    }
    @Test void stripsExecutableContentAndUnsafeStyle() {
        String clean = ExamCalendarNoteContent.clean("<script>alert(1)</script><svg onload='alert(1)'></svg><p onclick='run()' style='position:fixed;background-image:url(https://bad);color:#ff0000'>正文</p><a href='javascript:alert(1)'>链接</a><a href='https://example.com'>正常</a>").html();
        assertFalse(clean.contains("script")); assertFalse(clean.contains("onclick")); assertFalse(clean.contains("fixed")); assertFalse(clean.contains("url("));
        assertTrue(clean.contains("color:#ff0000")); assertTrue(clean.contains("https://example.com"));
    }
    @Test void rejectsUnmanagedImagesAndLimits() {
        for (String src : new String[]{"https://example.com/image.png", "data:image/png;base64,AA", "exam-note-image:-1", "exam-note-image:999999999999999999999"}) {
            assertEquals(1900018023, assertThrows(ServiceException.class, () -> ExamCalendarNoteContent.clean("<img src='" + src + "'>")).getCode());
        }
        assertThrows(ServiceException.class, () -> ExamCalendarNoteContent.clean("字".repeat(5001)));
        assertThrows(ServiceException.class, () -> ExamCalendarNoteContent.clean("<img src='exam-note-image:1'>".repeat(21)));
        assertThrows(ServiceException.class, () -> ExamCalendarNoteContent.clean("<!--" + "字".repeat(70000) + "-->"));
        assertEquals("", ExamCalendarNoteContent.clean("<p><br></p>").html());
        assertEquals(Set.of(Long.MAX_VALUE), ExamCalendarNoteContent.clean("<img src='exam-note-image:9223372036854775807'>").fileIds());
        assertThrows(ServiceException.class, () -> ExamCalendarNoteContent.clean("<img src='exam-note-image:9223372036854775808'>"));
    }
    @Test void detectsImageTypesFromBytesRatherThanFilename() {
        assertEquals("image/png", ExamCalendarNoteService.imageType(new byte[]{(byte)137,80,78,71,13,10,26,10}));
        assertEquals("image/jpeg", ExamCalendarNoteService.imageType(new byte[]{-1,-40,-1}));
        assertEquals("image/gif", ExamCalendarNoteService.imageType("GIF89a".getBytes()));
        assertEquals("image/webp", ExamCalendarNoteService.imageType("RIFF0000WEBP".getBytes()));
        assertThrows(ServiceException.class, () -> ExamCalendarNoteService.imageType("<svg>fake.png</svg>".getBytes()));
    }
}
