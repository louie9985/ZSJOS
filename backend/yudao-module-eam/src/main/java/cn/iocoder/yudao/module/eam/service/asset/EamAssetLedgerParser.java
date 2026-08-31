package cn.iocoder.yudao.module.eam.service.asset;

import cn.hutool.core.util.StrUtil;
import cn.idev.excel.FastExcelFactory;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.eam.enums.asset.EamAssetStatusEnum;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.eam.enums.ErrorCodeConstants.ASSET_IMPORT_FILE_INVALID;

/** V3 台账解析器：单层表头，分类编码驱动，列顺序可变。 */
@Component
public class EamAssetLedgerParser {
    public static final String SHEET_NAME = "资产台账";
    private static final String CATEGORY_CODE = "分类编码";
    private static final Map<String, Boolean> STANDARD_COLUMNS = Map.ofEntries(
            Map.entry("资产名称", true), Map.entry("资产编号", true), Map.entry("数量", true),
            Map.entry("资产状态", true), Map.entry("品牌型号", true), Map.entry("规格参数", true),
            Map.entry("序列号", true), Map.entry("条码", true), Map.entry("原值", true),
            Map.entry("净值", true), Map.entry("购入日期", true), Map.entry("资产来源", true),
            Map.entry("保修到期日", true), Map.entry("使用人", true), Map.entry("存放地点", true),
            Map.entry("预计使用年限（月）", true), Map.entry("备注", true), Map.entry("分类编码", true));

    public List<LedgerRow> parse(byte[] content) {
        try {
            List<Map<Integer, String>> rows = FastExcelFactory.read(new ByteArrayInputStream(content))
                    .headRowNumber(0).sheet(SHEET_NAME).doReadSync();
            if (rows.size() < 2) throw exception(ASSET_IMPORT_FILE_INVALID, "资产台账没有表头或数据");
            Map<String, Integer> headers = headers(rows.get(0));
            if (!headers.containsKey(CATEGORY_CODE) || !headers.containsKey("资产名称")) {
                throw exception(ASSET_IMPORT_FILE_INVALID, "台账必须包含分类编码和资产名称列");
            }
            List<LedgerRow> result = new ArrayList<>();
            for (int index = 1; index < rows.size(); index++) {
                if (!isBlankRow(rows.get(index))) result.add(parseRow(index + 1, rows.get(index), headers));
            }
            if (result.isEmpty()) throw exception(ASSET_IMPORT_FILE_INVALID, "资产台账没有有效资产行");
            return result;
        } catch (RuntimeException ex) {
            if (ex instanceof ServiceException) throw ex;
            throw exception(ASSET_IMPORT_FILE_INVALID, "缺少资产台账工作表或文件无法读取");
        }
    }

    private LedgerRow parseRow(int rowNum, Map<Integer, String> row, Map<String, Integer> headers) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> defaults = new ArrayList<>();
        String quantityText = cell(row, headers, "数量");
        Integer quantity = parsePositiveInteger(quantityText);
        String statusText = cell(row, headers, "资产状态");
        int status = parseStatus(statusText, warnings);
        if (StrUtil.isBlank(statusText)) defaults.add("资产状态为空，默认按闲置导入");
        String purchaseText = cell(row, headers, "购入日期");
        LocalDate purchaseDate = parseDate(purchaseText);
        if (StrUtil.isNotBlank(purchaseText) && purchaseDate == null) warnings.add("购入日期无法转换，已留空");
        Map<String, Object> ext = new LinkedHashMap<>();
        Map<String, Object> mapped = new LinkedHashMap<>();
        headers.forEach((name, index) -> {
            String value = cell(row, headers, name);
            if (!name.equals("微信密码") && StrUtil.isNotBlank(value)) mapped.put(name, value);
            if (!STANDARD_COLUMNS.containsKey(name) && !name.equals("微信密码") && StrUtil.isNotBlank(value)) {
                String fieldKey = name.contains(":") ? StrUtil.subBefore(name, ':', false) : name;
                ext.put(fieldKey, value);
            }
        });
        String categoryCode = cell(row, headers, CATEGORY_CODE);
        if (StrUtil.isBlank(categoryCode)) errors.add("分类编码为空");
        String assetName = cell(row, headers, "资产名称");
        if (StrUtil.isBlank(assetName)) errors.add("资产名称为空");
        validateDecimal(mapped.get("原值"), "原值", errors);
        validateDecimal(mapped.get("净值"), "净值", errors);
        validatePositiveInteger(mapped.get("预计使用年限（月）"), "预计使用年限（月）", errors);
        validateDate(mapped.get("保修到期日"), "保修到期日", errors);
        return new LedgerRow(rowNum, categoryCode, assetName,
                cell(row, headers, "资产编号"), cell(row, headers, "条码"), cell(row, headers, "品牌型号"),
                cell(row, headers, "序列号"), cell(row, headers, "存放地点"), quantity, status, purchaseDate,
                cell(row, headers, "备注"), cell(row, headers, "使用人"), null, null, null, null, ext, mapped, defaults, warnings, errors);
    }

    private static Map<String, Integer> headers(Map<Integer, String> row) {
        Map<String, Integer> result = new LinkedHashMap<>();
        row.forEach((index, value) -> { String name = StrUtil.trim(value); if (StrUtil.isNotBlank(name)) result.put(name, index); });
        return result;
    }
    private static String cell(Map<Integer, String> row, Map<String, Integer> headers, String header) {
        Integer index = headers.get(header); return index == null ? "" : StrUtil.trim(row.get(index));
    }
    private static boolean isBlankRow(Map<Integer, String> row) { return row.values().stream().allMatch(StrUtil::isBlank); }
    private static Integer parsePositiveInteger(String value) {
        if (StrUtil.isBlank(value)) return null;
        try { int number = new java.math.BigDecimal(value).intValueExact(); return number > 0 ? number : null; }
        catch (Exception ignored) { return null; }
    }

    private static void validateDecimal(Object value, String label, List<String> errors) {
        if (value == null || StrUtil.isBlank(String.valueOf(value))) return;
        try { new java.math.BigDecimal(String.valueOf(value)); }
        catch (NumberFormatException e) { errors.add(label + "必须为数字"); }
    }

    private static void validatePositiveInteger(Object value, String label, List<String> errors) {
        if (value == null || StrUtil.isBlank(String.valueOf(value))) return;
        if (parsePositiveInteger(String.valueOf(value)) == null) errors.add(label + "必须为正整数");
    }

    private static void validateDate(Object value, String label, List<String> errors) {
        if (value == null || StrUtil.isBlank(String.valueOf(value))) return;
        if (parseDate(String.valueOf(value)) == null) errors.add(label + "日期格式无效");
    }
    private static int parseStatus(String value, List<String> warnings) {
        if (StrUtil.isBlank(value)) return EamAssetStatusEnum.IDLE.getStatus();
        return switch (value) {
            case "在用", "正常使用中" -> EamAssetStatusEnum.IN_USE.getStatus();
            case "闲置", "闲置备用", "闲置在库" -> EamAssetStatusEnum.IDLE.getStatus();
            case "维修中" -> EamAssetStatusEnum.REPAIRING.getStatus();
            case "待报废" -> EamAssetStatusEnum.PENDING_SCRAP.getStatus();
            default -> { warnings.add("资产状态无法识别，已按闲置导入"); yield EamAssetStatusEnum.IDLE.getStatus(); }
        };
    }
    static LocalDate parseDate(String value) {
        if (StrUtil.isBlank(value)) return null;
        String normalized = value.trim().replace('年', '-').replace('月', '-').replace('日', ' ').replace('.', '-').replace('/', '-').trim();
        if (normalized.endsWith("-")) normalized = normalized.substring(0, normalized.length() - 1);
        try {
            if (normalized.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) return LocalDate.parse(normalized, DateTimeFormatter.ofPattern("yyyy-M-d"));
            if (normalized.matches("\\d{4}-\\d{1,2}")) return java.time.YearMonth.parse(normalized, DateTimeFormatter.ofPattern("yyyy-M")).atDay(1);
            if (normalized.matches("\\d{4}")) return java.time.Year.parse(normalized, DateTimeFormatter.ofPattern("yyyy")).atDay(1);
        } catch (DateTimeParseException ignored) { }
        return null;
    }

    public record LedgerRow(Integer rowNum, String categoryCode, String assetName, String assetCode, String barcode,
                            String brand, String sn, String location, Integer quantity, Integer status,
                            LocalDate purchaseDate, String remark, String useUserName, String supervisorName,
                            LocalDate joinDate, Boolean commitmentAccepted, LocalDate commitmentDate,
                            Map<String, Object> extFields, Map<String, Object> mappedFields,
                            List<String> defaultedFields, List<String> warnings, List<String> errors) {}
}
