package cn.iocoder.yudao.module.eam.service.asset;

import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.module.eam.controller.admin.asset.vo.EamAssetRespVO;
import cn.iocoder.yudao.module.eam.service.category.EamCategoryFieldService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
public class EamAssetExportService {
    @Resource private EamCategoryFieldService categoryFieldService;

    public void write(HttpServletResponse response, List<EamAssetRespVO> assets) throws IOException {
        Map<String, String> customColumns = new LinkedHashMap<>();
        Set<Long> categories = new LinkedHashSet<>();
        assets.forEach(asset -> categories.add(asset.getCategoryId()));
        for (Long categoryId : categories) {
            categoryFieldService.getEffectiveFieldList(categoryId).stream()
                    .filter(field -> !Boolean.FALSE.equals(field.getAdminVisible()))
                    .forEach(field -> customColumns.putIfAbsent(field.getFieldKey(), field.getFieldName()));
        }
        List<List<String>> head = new ArrayList<>();
        for (String label : List.of("资产编号", "资产名称", "分类", "管理模式", "数量", "单位", "状态",
                "购入日期", "资产来源", "使用部门", "使用员工", "存放地点", "备注", "附件")) {
            head.add(List.of(label));
        }
        customColumns.forEach((key, label) -> head.add(List.of(key + ":" + label)));
        List<List<Object>> rows = new ArrayList<>();
        for (EamAssetRespVO asset : assets) {
            List<Object> row = new ArrayList<>(Arrays.asList(asset.getAssetCode(), asset.getName(),
                    asset.getCategoryName(), asset.getManagementMode(), asset.getQuantity(), asset.getUnit(),
                    asset.getStatus(), asset.getPurchaseDate() == null ? null : asset.getPurchaseDate().toString(),
                    asset.getSourceLabelSnapshot(), asset.getUseDeptName(), asset.getUseEmployeeNameSnapshot(),
                    asset.getLocation(), asset.getRemark(), asset.getFileUrls() == null ? null : String.join("\n", asset.getFileUrls())));
            Map<String, Object> values = asset.getExtFields() == null ? Map.of() : asset.getExtFields();
            Map<String, String> labels = asset.getExtFieldLabels() == null ? Map.of() : asset.getExtFieldLabels();
            for (String key : customColumns.keySet()) {
                // 历史字典选择使用保存时的标签；序列号等文本直接导出自定义字段值。
                Object value = labels.containsKey(key) ? labels.get(key) : values.get(key);
                row.add(value == null ? null : String.valueOf(value));
            }
            rows.add(row);
        }
        ExcelUtils.write(response, "资产台账.xlsx", "资产列表", head, rows);
    }
}
