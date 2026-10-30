package cn.iocoder.yudao.module.zsjos.service.homemetric;

import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO;
import java.util.List;

public record HomeMetricSpec(String code, String name, String description, String unit, int sort,
        String viewPermission, List<Long> userIds, List<Long> deptIds, String action,
        String formula, boolean percentage, int decimals, List<Operand> operands, java.util.Map<String,String> dictionarySnapshots) {
    public HomeMetricSpec(String code,String name,String description,String unit,int sort,String viewPermission,List<Long> userIds,List<Long> deptIds,String action,String formula,boolean percentage,int decimals,List<Operand> operands) {
        this(code,name,description,unit,sort,viewPermission,userIds,deptIds,action,formula,percentage,decimals,operands,java.util.Map.of());
    }
    public HomeMetricSpec withSnapshots(java.util.Map<String,String> snapshots) {return new HomeMetricSpec(code,name,description,unit,sort,viewPermission,userIds,deptIds,action,formula,percentage,decimals,operands,snapshots);}
    public record Operand(MetricEvaluation.Operand calculation, AdvancedFilterGroupReqVO filter, String timeField) {}
    public MetricEvaluation.Definition definition() {
        return new MetricEvaluation.Definition(MetricFormulaParser.parse(formula),
                operands.stream().map(Operand::calculation).toList(), percentage, decimals);
    }
}
