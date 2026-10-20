package cn.iocoder.yudao.module.pms.controller.admin.kb.interaction;

import cn.iocoder.yudao.module.pms.controller.pub.kb.PmsKnowledgeSharePageController;
import cn.iocoder.yudao.module.pms.controller.admin.kb.content.vo.document.PmsKnowledgeExcelPreviewRespVO;
import cn.iocoder.yudao.module.pms.service.kb.content.PmsKnowledgeExcelPreviewService;
import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.*;

class PmsKnowledgeExcelShareTest {
    @Test void safeHtmlLinksHeadAndFailureStates() throws Exception {
        var service=mock(PmsKnowledgeExcelPreviewService.class);
        var controller=new PmsKnowledgeSharePageController();
        ReflectionTestUtils.setField(controller,"excel",service);
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        var data=new PmsKnowledgeExcelPreviewRespVO(List.of(new PmsKnowledgeExcelPreviewRespVO.Sheet(0,"中文工作表"),
                new PmsKnowledgeExcelPreviewRespVO.Sheet(1,"第二表")),0,
                List.of(List.of("<script>bad()</script>","中文单元格")),2,true,List.of("内容已截断"));
        when(service.publicPreview("fixture",9L,-1)).thenReturn(data);
        var response=mvc.perform(get("/pms/kb/document-share/excel").param("token","fixture").param("fileId","9"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse();
        String html=response.getContentAsString(StandardCharsets.UTF_8);
        var page=Jsoup.parse(html);
        assertTrue(page.select("script").isEmpty());
        assertEquals("<script>bad()</script>",page.selectFirst("td").text());
        assertTrue(page.select("nav a").get(1).attr("href").contains("fileId=9&sheetIndex=1"));
        assertTrue(response.getHeader("Content-Security-Policy").contains("script-src 'none'"));
        mvc.perform(head("/pms/kb/document-share/excel").param("token","fixture").param("fileId","9"))
                .andExpect(status().isOk()).andExpect(content().string(""));
        String output=System.getProperty("excel.html.output");
        if(output!=null) Files.writeString(Path.of(output),html,StandardCharsets.UTF_8);
        when(service.publicPreview("fixture",9L,-1)).thenThrow(exception(KNOWLEDGE_EXCEL_ENCRYPTED));
        mvc.perform(get("/pms/kb/document-share/excel").param("token","fixture").param("fileId","9"))
                .andExpect(status().isUnprocessableEntity());
        when(service.publicPreview("closed",9L,-1)).thenThrow(exception(KNOWLEDGE_DOCUMENT_SHARE_INVALID));
        mvc.perform(get("/pms/kb/document-share/excel").param("token","closed").param("fileId","9"))
                .andExpect(status().isGone());
    }
    @Test void legacyJsonUsesSamePublicAuthorization() throws Exception {
        var service=mock(PmsKnowledgeExcelPreviewService.class);
        var controller=new PmsKnowledgeExcelShareController();
        ReflectionTestUtils.setField(controller,"excel",service);
        var mvc=MockMvcBuilders.standaloneSetup(controller).build();
        when(service.publicPreview("fixture",null,-1)).thenReturn(new PmsKnowledgeExcelPreviewRespVO(List.of(),-1,List.of(),0,false,List.of()));
        mvc.perform(get("/pms/kb/document-share/excel-preview").param("token","fixture"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.data.rows").isEmpty());
        verify(service).publicPreview("fixture",null,-1);
    }
    @Test void publicHtmlUsesSpansAndOmitsCoveredCells() {
        var projection = new PmsKnowledgeExcelPreviewRespVO(List.of(), 0,
                List.of(List.of("合并标题", ""), List.of("", "")), 2, false, List.of(),
                List.of(new PmsKnowledgeExcelPreviewRespVO.Merge(0,0,2,2)));
        var page = Jsoup.parse(cn.iocoder.yudao.module.pms.service.kb.interaction.PmsKnowledgeSharePage.excel(projection,"fixture",null));
        assertEquals(1,page.select("td").size());
        assertEquals("2",page.selectFirst("td").attr("rowspan"));
        assertEquals("2",page.selectFirst("td").attr("colspan"));
        assertEquals(2,page.select("tbody tr").size());
    }
}
