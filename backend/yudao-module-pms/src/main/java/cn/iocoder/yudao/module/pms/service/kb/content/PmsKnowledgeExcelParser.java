package cn.iocoder.yudao.module.pms.service.kb.content;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.controller.admin.kb.content.vo.document.PmsKnowledgeExcelPreviewRespVO;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.springframework.stereotype.Component;
import java.io.*;
import java.util.*;
import java.util.zip.ZipInputStream;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.enums.ErrorCodeConstants.*;

@Component
public class PmsKnowledgeExcelParser {
    public static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final int MAX_ROWS = 500, MAX_COLUMNS = 100;

    public static boolean supports(String name, String type) {
        String value = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (value.startsWith(".")) value = value.substring(1);
        if (Set.of("xls", "xlsx", "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").contains(value)) return true;
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xls") || lower.endsWith(".xlsx");
    }

    public PmsKnowledgeExcelPreviewRespVO parse(byte[] bytes, int requestedSheet) {
        if (bytes == null || bytes.length == 0) throw exception(KNOWLEDGE_EXCEL_INVALID);
        if (bytes.length > MAX_BYTES) throw exception(KNOWLEDGE_EXCEL_TOO_LARGE);
        try {
            validateZip(bytes);
            try (Workbook book = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
                var sheets = new ArrayList<PmsKnowledgeExcelPreviewRespVO.Sheet>();
                for (int i = 0; i < book.getNumberOfSheets(); i++) {
                    if (!book.isSheetHidden(i) && !book.isSheetVeryHidden(i))
                        sheets.add(new PmsKnowledgeExcelPreviewRespVO.Sheet(i, book.getSheetName(i)));
                }
                if (sheets.isEmpty()) return new PmsKnowledgeExcelPreviewRespVO(List.of(), -1, List.of(), 0, false, List.of());
                int index = requestedSheet == -1 ? sheets.getFirst().index() : requestedSheet;
                if (sheets.stream().noneMatch(sheet -> sheet.index() == index)) throw exception(KNOWLEDGE_EXCEL_SHEET_INVALID);
                Sheet sheet = book.getSheetAt(index);
                var rows = new ArrayList<List<String>>();
                int rowCount = sheet.getPhysicalNumberOfRows() == 0 ? 0 : sheet.getLastRowNum() + 1;
                int columns = 0, chars = 0;
                var ranges = sheet.getMergedRegions();
                // Merges can extend through rows/cells that are absent from the file.
                for (var range : ranges) {
                    rowCount = Math.max(rowCount, range.getLastRow() + 1);
                    columns = Math.max(columns, Math.min(MAX_COLUMNS, range.getLastColumn() + 1));
                }
                boolean truncated = rowCount > MAX_ROWS, missingFormula = false;
                truncated |= ranges.stream().anyMatch(range -> range.getLastColumn() >= MAX_COLUMNS);
                DataFormatter formatter = new DataFormatter(Locale.CHINA);
                formatter.setUseCachedValuesForFormulaCells(true);
                for (int r = 0; r < Math.min(rowCount, MAX_ROWS); r++) {
                    Row row = sheet.getRow(r);
                    int count = row == null ? 0 : Math.max(0, row.getLastCellNum());
                    truncated |= count > MAX_COLUMNS;
                    columns = Math.max(columns, Math.min(count, MAX_COLUMNS));
                    var cells = new ArrayList<String>();
                    for (int c = 0; c < Math.min(count, MAX_COLUMNS); c++) {
                        Cell cell = row.getCell(c);
                        String value;
                        // No evaluator: external links and macros are never executed. XLS
                        // caches are mandatory; OOXML may explicitly lack a saved result.
                        if (cell instanceof XSSFCell x && cell.getCellType() == CellType.FORMULA
                                && (!x.getCTCell().isSetV() || (x.getCTCell().getV().isEmpty()
                                    && cell.getCachedFormulaResultType() != CellType.STRING))) {
                            value = "[公式结果未保存]"; missingFormula = true;
                        } else {
                            value = cell == null ? "" : formatter.formatCellValue(cell);
                        }
                        if (chars + value.length() > 2_000_000) { truncated = true; break; }
                        chars += value.length();
                        cells.add(value);
                    }
                    rows.add(cells);
                    if (chars >= 2_000_000 || cells.size() < Math.min(count, MAX_COLUMNS)) {
                        truncated |= r + 1 < rowCount;
                        break;
                    }
                }
                var merges = new ArrayList<PmsKnowledgeExcelPreviewRespVO.Merge>();
                boolean[][] occupied = new boolean[rows.size()][columns];
                for (var range : ranges) {
                    int r = range.getFirstRow(), c = range.getFirstColumn();
                    if (r >= rows.size() || c >= columns) continue;
                    int endRow = Math.min(range.getLastRow() + 1, rows.size());
                    int endColumn = Math.min(range.getLastColumn() + 1, columns);
                    // Reject malformed overlapping ranges instead of emitting an ambiguous table.
                    for (int y = r; y < endRow; y++) for (int x = c; x < endColumn; x++) {
                        if (occupied[y][x]) throw exception(KNOWLEDGE_EXCEL_INVALID);
                        occupied[y][x] = true;
                        if (y != r || x != c) {
                            if (x < rows.get(y).size()) rows.get(y).set(x, "");
                        }
                    }
                    if (endRow - r > 1 || endColumn - c > 1)
                        merges.add(new PmsKnowledgeExcelPreviewRespVO.Merge(r, c, endRow - r, endColumn - c));
                }
                var notices = new ArrayList<String>();
                if (truncated) notices.add("内容已截断：最多展示前 500 行、100 列及 200 万字符，请下载查看完整内容。");
                if (missingFormula) notices.add("部分公式未保存计算结果，请在 Excel 中计算并保存后重新上传。");
                return new PmsKnowledgeExcelPreviewRespVO(sheets, index, rows, columns, truncated, notices, merges);
            }
        } catch (ServiceException e) { throw e;
        } catch (EncryptedDocumentException e) { throw exception(KNOWLEDGE_EXCEL_ENCRYPTED);
        } catch (Exception e) { throw exception(KNOWLEDGE_EXCEL_INVALID); }
    }

    private void validateZip(byte[] bytes) throws IOException {
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') return;
        // Bound the entire package without changing POI global ZIP settings used by
        // other modules. Rejected archives never reach the XML object model.
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            long total = 0; int entries = 0; byte[] buffer = new byte[8192];
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 2048) throw exception(KNOWLEDGE_EXCEL_TOO_LARGE);
                long size = 0; int n;
                while ((n = zip.read(buffer)) != -1) {
                    total += n; size += n;
                    if (total > 32L * 1024 * 1024 || size > 16L * 1024 * 1024)
                        throw exception(KNOWLEDGE_EXCEL_TOO_LARGE);
                }
                long compressed = entry.getCompressedSize();
                if (size > 1024 * 1024 && compressed > 0 && size > compressed * 100)
                    throw exception(KNOWLEDGE_EXCEL_TOO_LARGE);
            }
        }
    }
}
