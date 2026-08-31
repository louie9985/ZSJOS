package cn.iocoder.yudao.module.zsjos.controller.admin.workplan.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class WorkPlanExportData {
    private List<List<String>> headers;
    private List<List<Object>> rows;
}
