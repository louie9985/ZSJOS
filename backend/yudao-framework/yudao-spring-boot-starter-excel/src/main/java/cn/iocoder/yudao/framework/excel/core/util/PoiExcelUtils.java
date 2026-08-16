package cn.iocoder.yudao.framework.excel.core.util;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import lombok.experimental.UtilityClass;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;

import java.math.BigDecimal;
import java.util.Collection;

/**
 * 原生 POI Excel 工具，用于 FastExcel 无法表达的复杂模板。
 */
@UtilityClass
public class PoiExcelUtils {

    private static final DataFormatter DATA_FORMATTER = new DataFormatter();

    public static String getCellText(Row row, Integer column, FormulaEvaluator evaluator) {
        if (row == null || column == null || row.getCell(column) == null) {
            return "";
        }
        return evaluator == null
                ? DATA_FORMATTER.formatCellValue(row.getCell(column))
                : DATA_FORMATTER.formatCellValue(row.getCell(column), evaluator);
    }

    public static BigDecimal getDecimal(Row row, Integer column, FormulaEvaluator evaluator) {
        String value = getCellText(row, column, evaluator).replace(",", "");
        return StrUtil.isBlank(value) ? null : NumberUtil.toBigDecimal(value);
    }

    public static boolean isEmptyRow(Row row, FormulaEvaluator evaluator, Collection<Integer> columns) {
        if (row == null) {
            return true;
        }
        for (Integer column : columns) {
            if (StrUtil.isNotBlank(getCellText(row, column, evaluator))) {
                return false;
            }
        }
        return true;
    }

    public static void writeText(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(StrUtil.nullToEmpty(value));
        cell.setCellStyle(style);
    }

    public static void writeNumber(Row row, int column, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
        cell.setCellStyle(style);
    }

    public static void writeFormula(Row row, int column, String formula, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellFormula(formula);
        cell.setCellStyle(style);
    }

    public static void mergeHeader(Sheet sheet, Row firstRow, Row secondRow,
                                   int beginColumn, int endColumn, String title, CellStyle style) {
        sheet.addMergedRegion(new CellRangeAddress(firstRow.getRowNum(), secondRow.getRowNum(),
                beginColumn, endColumn));
        Cell cell = firstRow.createCell(beginColumn);
        cell.setCellValue(title);
        cell.setCellStyle(style);
        secondRow.createCell(beginColumn).setCellStyle(style);
    }

    public static void addComment(Cell cell, String text) {
        CreationHelper helper = cell.getSheet().getWorkbook().getCreationHelper();
        ClientAnchor anchor = helper.createClientAnchor();
        anchor.setCol1(cell.getColumnIndex());
        anchor.setCol2(cell.getColumnIndex() + 3);
        anchor.setRow1(cell.getRowIndex());
        anchor.setRow2(cell.getRowIndex() + 3);
        Comment comment = cell.getSheet().createDrawingPatriarch().createCellComment(anchor);
        comment.setString(helper.createRichTextString(text));
        cell.setCellComment(comment);
    }

    public static CellStyle createBorderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        return style;
    }

    public static CellStyle createDataStyle(Workbook workbook, boolean locked, IndexedColors fillColor) {
        CellStyle style = createBorderStyle(workbook);
        style.setLocked(locked);
        if (fillColor != null) {
            style.setFillForegroundColor(fillColor.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    public static CellStyle createNumberStyle(Workbook workbook, CellStyle base, String format) {
        CellStyle style = workbook.createCellStyle();
        style.cloneStyleFrom(base);
        style.setDataFormat(workbook.createDataFormat().getFormat(format));
        return style;
    }

    public static CellStyle createHeaderStyle(Workbook workbook, IndexedColors fillColor) {
        CellStyle style = createBorderStyle(workbook);
        style.setFillForegroundColor(fillColor.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    public static CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 20);
        style.setFont(font);
        return style;
    }

}
