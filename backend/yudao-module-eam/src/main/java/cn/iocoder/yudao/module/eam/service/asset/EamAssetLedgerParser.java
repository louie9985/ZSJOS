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

/**
 * Parses only the in-service asset sheet. The workbook's process/audit columns are
 * normalized into business fields or separate history records; raw column names are
 * deliberately not exposed as asset custom fields.
 */
@Component
public class EamAssetLedgerParser {

    public static final String SHEET_NAME = "在岗资产初始申报表";
    private static final int CREDENTIAL_COLUMN = 36;

    public List<LedgerRow> parse(byte[] content) {
        try {
            List<Map<Integer, String>> rows = FastExcelFactory.read(new ByteArrayInputStream(content))
                    .headRowNumber(0).sheet(SHEET_NAME).doReadSync();
            if (rows.size() < 3 || !"资产大类".equals(cell(rows.get(1), 4))
                    || !"资产标签".equals(cell(rows.get(1), 48))) {
                throw exception(ASSET_IMPORT_FILE_INVALID, "第二行表头与中世健资产台账不匹配");
            }
            List<LedgerRow> result = new ArrayList<>();
            for (int index = 2; index < rows.size(); index++) {
                Map<Integer, String> values = rows.get(index);
                if (!isBlankRow(values)) result.add(parseRow(index + 1, values));
            }
            if (result.isEmpty()) throw exception(ASSET_IMPORT_FILE_INVALID, "工作表没有有效资产行");
            return result;
        } catch (RuntimeException ex) {
            if (ex instanceof ServiceException) throw ex;
            throw exception(ASSET_IMPORT_FILE_INVALID, "缺少指定工作表或文件无法读取");
        }
    }

    private LedgerRow parseRow(int rowNum, Map<Integer, String> values) {
        String rootName = normalizeRootName(cell(values, 4));
        CategorySelection category = selectCategory(rowNum, values, rootName);
        List<String> warnings = new ArrayList<>();
        List<String> defaultedFields = new ArrayList<>();

        String quantityText = cell(values, 16);
        int quantity = parseQuantity(quantityText, warnings);
        if (StrUtil.isBlank(quantityText)) defaultedFields.add("数量为空，默认按 1 导入");
        String statusText = cell(values, 43);
        int status = parseStatus(statusText, warnings);
        if (StrUtil.isBlank(statusText)) defaultedFields.add("使用状态为空，默认按闲置导入");

        String purchaseDateText = cell(values, 25);
        LocalDate purchaseDate = parseDate(purchaseDateText);
        if (StrUtil.isNotBlank(purchaseDateText) && purchaseDate == null) warnings.add("采购日期无法转换，已留空");
        String joinDateText = cell(values, 2);
        LocalDate joinDate = parseDate(joinDateText);
        if (StrUtil.isNotBlank(joinDateText) && joinDate == null) warnings.add("入司日期无法转换，已留空");
        String commitmentText = cell(values, 45);
        Boolean commitmentAccepted = parseCommitment(commitmentText, warnings);
        if (StrUtil.isBlank(commitmentText)) defaultedFields.add("使用人承诺为空，导入为待补");
        LocalDate commitmentDate = parseDate(cell(values, 46));

        Map<String, Object> ext = new LinkedHashMap<>();
        put(ext, "device_color", cell(values, 20));
        put(ext, "cpu", cell(values, 21));
        put(ext, "ram", cell(values, 22));
        put(ext, "disk", cell(values, 23));
        put(ext, "display_spec", cell(values, 24));
        put(ext, "login_id", cell(values, 26));
        put(ext, "bound_mobile", cell(values, 27));
        put(ext, "bound_social", cell(values, 28));
        put(ext, "membership_level", cell(values, 29));
        put(ext, "account_balance", cell(values, 30));
        put(ext, "package_expiry", cell(values, 31));
        put(ext, "payment_method", cell(values, 32));
        put(ext, "auto_renewal", cell(values, 33));
        put(ext, "password_custody", cell(values, 34));
        put(ext, "wechat_account", cell(values, 35));
        // Column 36 is the credential column and is intentionally never read.
        put(ext, "real_name_person", cell(values, 37));
        put(ext, "contact_count", cell(values, 38));
        put(ext, "power_adapter", cell(values, 40));
        put(ext, "cables", cell(values, 41));
        put(ext, "original_box", cell(values, 42));

        Map<String, Object> mappedFields = new LinkedHashMap<>();
        put(mappedFields, "使用人", cell(values, 0));
        put(mappedFields, "上级", cell(values, 1));
        put(mappedFields, "入司日期", joinDateText);
        put(mappedFields, "工位/存放地点", cell(values, 3));
        put(mappedFields, "资产大类", rootName);
        put(mappedFields, "资产子类", category.leafName());
        put(mappedFields, "资产名称", category.assetName());
        put(mappedFields, "数量", quantityText);
        put(mappedFields, "品牌型号", cell(values, 18));
        put(mappedFields, "序列号", cell(values, 19));
        put(mappedFields, "采购日期", purchaseDateText);
        put(mappedFields, "使用状态", statusText);
        put(mappedFields, "备注", cell(values, 44));
        put(mappedFields, "资产标签", cell(values, 48));
        return new LedgerRow(rowNum, rootName, category.leafName(), category.assetName(),
                cell(values, 48), "", cell(values, 18), cell(values, 19), cell(values, 3),
                quantity, status, purchaseDate, cell(values, 44), cell(values, 0), cell(values, 1), joinDate,
                commitmentAccepted, commitmentDate, ext, mappedFields, cell(values, 47), cell(values, 49),
                cell(values, 53), defaultedFields, warnings);
    }

    private CategorySelection selectCategory(int rowNum, Map<Integer, String> row, String rootName) {
        int mainColumn;
        int otherColumn;
        switch (rootName) {
            case "IT硬件设备" -> { mainColumn = 8; otherColumn = 9; }
            case "数字资产" -> { mainColumn = 6; otherColumn = 7; }
            case "办公家具" -> { mainColumn = 10; otherColumn = 11; }
            case "专业书籍及教具" -> { mainColumn = 12; otherColumn = 13; }
            case "办公用品和耗材" -> { mainColumn = 14; otherColumn = 15; }
            case "其他" -> {
                String name = cell(row, 5);
                return new CategorySelection("其他资产", StrUtil.blankToDefault(name, "其他资产"), name);
            }
            default -> throw exception(ASSET_IMPORT_FILE_INVALID, "第 " + rowNum + " 行资产大类无法识别");
        }
        String main = cell(row, mainColumn);
        String other = cell(row, otherColumn);
        if (StrUtil.isBlank(main)) throw exception(ASSET_IMPORT_FILE_INVALID, "第 " + rowNum + " 行资产子分类为空");
        return new CategorySelection(main, main.startsWith("其他") && StrUtil.isNotBlank(other) ? other : main, other);
    }

    private static String normalizeRootName(String rootName) {
        return switch (rootName) {
            case "办公家具及周边" -> "办公家具";
            case "专业书籍或教具" -> "专业书籍及教具";
            default -> rootName;
        };
    }

    private static int parseQuantity(String value, List<String> warnings) {
        if (StrUtil.isBlank(value)) return 1;
        try {
            int quantity = new java.math.BigDecimal(value).intValueExact();
            if (quantity > 0) return quantity;
        } catch (Exception ignored) { }
        warnings.add("数量无效，已按 1 导入");
        return 1;
    }

    private static int parseStatus(String value, List<String> warnings) {
        if (StrUtil.isBlank(value)) return EamAssetStatusEnum.IDLE.getStatus();
        return switch (value) {
            case "正常使用中" -> EamAssetStatusEnum.IN_USE.getStatus();
            case "闲置备用", "闲置在库" -> EamAssetStatusEnum.IDLE.getStatus();
            case "维修中" -> EamAssetStatusEnum.REPAIRING.getStatus();
            case "待报废" -> EamAssetStatusEnum.PENDING_SCRAP.getStatus();
            default -> { warnings.add("使用状态无法识别，已按闲置导入"); yield EamAssetStatusEnum.IDLE.getStatus(); }
        };
    }

    private static Boolean parseCommitment(String value, List<String> warnings) {
        if (StrUtil.isBlank(value)) return null;
        if ("✅".equals(value) || "是".equals(value) || "已确认".equals(value)) return true;
        if ("□".equals(value) || "否".equals(value) || "未确认".equals(value)) return false;
        warnings.add("使用人承诺值无法识别，已标记待补");
        return null;
    }

    static LocalDate parseDate(String value) {
        if (StrUtil.isBlank(value)) return null;
        String normalized = value.trim().replace('年', '-').replace('月', '-').replace('日', ' ')
                .replace('.', '-').replace('/', '-').trim();
        if (normalized.endsWith("-")) normalized = normalized.substring(0, normalized.length() - 1);
        try {
            if (normalized.matches("\\d{4}-\\d{1,2}-\\d{1,2}"))
                return LocalDate.parse(normalized, DateTimeFormatter.ofPattern("yyyy-M-d"));
            if (normalized.matches("\\d{4}-\\d{1,2}"))
                return java.time.YearMonth.parse(normalized, DateTimeFormatter.ofPattern("yyyy-M")).atDay(1);
            if (normalized.matches("\\d{4}"))
                return java.time.Year.parse(normalized, DateTimeFormatter.ofPattern("yyyy")).atDay(1);
        } catch (DateTimeParseException ignored) { return null; }
        return null;
    }

    private static boolean isBlankRow(Map<Integer, String> row) {
        return StrUtil.isBlank(cell(row, 4)) && StrUtil.isBlank(cell(row, 48)) && StrUtil.isBlank(cell(row, 0));
    }

    private static String cell(Map<Integer, String> row, int index) {
        if (index == CREDENTIAL_COLUMN) return "";
        return StrUtil.trim(row.get(index));
    }

    private static void put(Map<String, Object> map, String key, String value) {
        if (StrUtil.isNotBlank(value)) map.put(key, value);
    }

    private record CategorySelection(String leafName, String assetName, String otherDescription) {}

    public record LedgerRow(Integer rowNum, String rootCategoryName, String leafCategoryName, String assetName,
                            String assetCode, String barcode, String brand, String sn, String location,
                            Integer quantity, Integer status, LocalDate purchaseDate, String remark,
                            String useUserName, String supervisorName, LocalDate joinDate,
                            Boolean commitmentAccepted, LocalDate commitmentDate,
                            Map<String, Object> extFields, Map<String, Object> mappedFields,
                            String verificationResult, String verifierName, String handoverRecord,
                            List<String> defaultedFields, List<String> warnings) {}
}
