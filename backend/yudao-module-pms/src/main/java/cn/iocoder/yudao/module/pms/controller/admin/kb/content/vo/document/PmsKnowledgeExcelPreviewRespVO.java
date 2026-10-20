package cn.iocoder.yudao.module.pms.controller.admin.kb.content.vo.document;

import java.util.List;

/** Original zero-based sheet indices and display text only. */
public record PmsKnowledgeExcelPreviewRespVO(List<Sheet> sheets, int sheetIndex,
        List<List<String>> rows, int columnCount, boolean truncated, List<String> notices,
        List<Merge> merges) {
    public PmsKnowledgeExcelPreviewRespVO(List<Sheet> sheets, int sheetIndex, List<List<String>> rows,
            int columnCount, boolean truncated, List<String> notices) {
        this(sheets, sheetIndex, rows, columnCount, truncated, notices, List.of());
    }
    public record Merge(int row, int column, int rowSpan, int colSpan) {}
    public record Sheet(int index, String name) {}
}
