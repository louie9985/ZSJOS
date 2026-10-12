package cn.iocoder.yudao.module.system.service.notice;

import cn.iocoder.yudao.module.system.controller.pub.notice.vo.NoticeSharePublicRespVO;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NoticeShareContentTest {
    private static final String FIXED = """
            <p>中世健考试安排</p><table style="width:900px;table-layout:fixed"><colgroup>
            <col width="200"><col width="180"><col width="140"><col width="380"></colgroup><tbody>
            <tr style="height:56px"><th>考试轮次</th><th>考试日期</th><th>报名截止</th><th>科目安排</th></tr>
            <tr style="height:64px"><td style="background-color:rgb(240,248,255);text-align:center;vertical-align:middle;border:2px solid #123456"><span style="color:rgb(200,0,0);font-weight:bold">第一期考试</span></td><td>2027年3月27日</td><td>2027年3月3日</td><td>基础知识：下午13:00—14:30；专业技能：下午15:30—17:00</td></tr>
            <tr><td colspan="2">合并单元格</td><td rowspan="2">保留跨行</td><td>中文与 English 123</td></tr>
            <tr><td>第四行</td><td>日期待定</td><td>最后一列</td></tr></tbody></table><p>正文结束</p>
            """;

    @Test void preservesEditorWidthsMergedCellsAndSafeFormattingWithoutEditingOriginal() {
        String clean = NoticeShareContent.clean(FIXED);
        var doc = Jsoup.parseBodyFragment(clean);
        var table = doc.selectFirst("table");
        assertTrue(table.attr("style").contains("width:900px"));
        assertTrue(table.attr("style").contains("table-layout:fixed"));
        assertTrue(table.attr("style").contains("min-width:900.0px"));
        assertEquals(List.of("200", "180", "140", "380"), doc.select("col").eachAttr("width"));
        assertEquals("2", doc.selectFirst("[colspan]").attr("colspan"));
        assertEquals("2", doc.selectFirst("[rowspan]").attr("rowspan"));
        assertEquals("height:56px", doc.selectFirst("tr").attr("style"));
        assertTrue(doc.selectFirst("td").attr("style").contains("text-align:center"));
        assertTrue(doc.selectFirst("td").attr("style").contains("border:2px solid #123456"));
        assertTrue(doc.selectFirst("span").attr("style").contains("color:rgb(200,0,0)"));
        assertEquals(1, doc.select(".notice-table-scroll > table").size());
        assertEquals("0", table.parent().attr("tabindex"));
        assertEquals(clean, NoticeShareContent.clean(clean));
        assertFalse(FIXED.contains("notice-table-scroll"));
    }

    @Test void rejectsExecutableResourceAndOverlayCssWhileKeepingIndependentSafeDeclarations() {
        String input = """
                <style>body{display:none}</style><p style="position:fixed">正文</p>
                <table style="width:900px;position:fixed;z-index:999;display:none;background:url(https://tracker.test/a);table-layout:fixed">
                <tr><td onclick="bad()" style="text-align:center;background-image:url(javascript:bad());color:expression(bad());width:expression(bad());height:20px;behavior:url(x);--secret:url(x);padding:2px 4px">
                <span style="color:r\\65 d;position:absolute">内容</span><script>bad()</script><img src="javascript:bad()"></td></tr></table>
                """;
        var doc = Jsoup.parseBodyFragment(NoticeShareContent.clean(input));
        assertTrue(doc.select("script,style,[onclick],[src^=javascript]").isEmpty());
        String styles = String.join(" ", doc.select("[style]").eachAttr("style"));
        for (String forbidden : List.of("url", "expression", "position", "z-index", "display", "behavior", "secret", "\\")) {
            assertFalse(styles.contains(forbidden), forbidden);
        }
        assertTrue(styles.contains("height:20px"));
        assertTrue(styles.contains("padding:2px 4px"));
        assertFalse(doc.selectFirst("p").hasAttr("style"));
    }

    @Test void handlesPercentageCellWidthsNestedAndMultipleTablesIdempotently() {
        String input = "<table style='width:100%'><tr><td style='width:25%'>A</td><td style='width:75%'>B</td></tr></table>"
                + "<table><tr><td style='width:200px'>C</td><td>D</td></tr></table>"
                + "<table><tr><td><table style='width:900px'><tr><td>inner</td></tr></table></td></tr></table>";
        String clean = NoticeShareContent.clean(input);
        var doc = Jsoup.parseBodyFragment(clean);
        assertEquals(4, doc.select(".notice-table-scroll > table").size());
        assertTrue(doc.select("table").get(0).attr("style").contains("width:100%"));
        assertTrue(doc.select("table").get(1).attr("style").contains("min-width:280.0px"));
        assertTrue(doc.select("table").get(2).attr("style").contains("min-width:80.0px"));
        assertEquals(clean, NoticeShareContent.clean(clean));
    }

    @Test void exportsRealSanitizedFixturesForBothPublicReaders() throws Exception {
        Map<String, String> samples = Map.of(
                "fixed", FIXED,
                "percent", "<table style='width:100%'><tr><td style='width:25%'>第一列</td><td style='width:75%'>第二列可换行的长中文内容，用来验证百分比列宽。</td></tr></table>",
                "cells", "<table><tr><th style='width:200px'>第一列</th><th style='width:300px'>第二列</th><th style='width:240px'>第三列</th></tr><tr><td>中文</td><td>English_abcdefghijklmnopqrstuvwxyz_0123456789</td><td>最后一列</td></tr></table>",
                "plain", "<p>无表格正文，保留<strong>加粗</strong>和普通段落。</p>");
        Path output = Path.of("target", "notice-share-tables");
        Files.createDirectories(output);
        for (var sample : samples.entrySet()) {
            String clean = NoticeShareContent.clean(sample.getValue());
            Files.writeString(output.resolve(sample.getKey() + ".content.html"), clean, StandardCharsets.UTF_8);
            var notice = new NoticeSharePublicRespVO();
            notice.setTitle("公告表格格式验证"); notice.setContent(clean); notice.setAttachments(List.of());
            notice.setPublishTime(LocalDateTime.of(2026, 10, 9, 16, 0));
            Files.writeString(output.resolve(sample.getKey() + ".page.html"), NoticeSharePage.notice(notice, "a".repeat(43)), StandardCharsets.UTF_8);
            assertEquals(clean, NoticeShareContent.clean(clean));
        }
    }
}
