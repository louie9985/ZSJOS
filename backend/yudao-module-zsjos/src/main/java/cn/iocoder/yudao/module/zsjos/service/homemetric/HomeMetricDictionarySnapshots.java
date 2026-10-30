package cn.iocoder.yudao.module.zsjos.service.homemetric;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.zsjos.service.advancedfilter.AdvancedFilterService;
import cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class HomeMetricDictionarySnapshots {
    @Resource private DictDataApi dictionaries;
    @Resource private AdvancedFilterService filters;
    @Resource private HomeMetricSources sources;
    public HomeMetricSpec capture(HomeMetricSpec spec,HomeMetricSpec previous,Long actor) {
        Map<String,String> snapshots=new LinkedHashMap<>();
        var catalog=sources.catalog(actor);
        for(var operand:spec.operands()) {
            var source=catalog.stream().filter(s->s.key().equals(operand.calculation().source())).findFirst().orElseThrow(() ->
                    cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                            cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.HOME_METRIC_DENIED));
            if(source.scene()==null || source.scene().isBlank()) continue;
            Map<String,String> dictFields=new HashMap<>();
            for(var field:filters.catalog(source.scene()).fields()) {
                String origin=field.declaredOptionSource()!=null?field.declaredOptionSource():field.optionSource();
                if(origin!=null&&origin.startsWith("dict:"))dictFields.put(field.fieldKey(),origin.substring(5));
            }
            collect(operand.filter(),operand.calculation().name(),dictFields,snapshots,previous==null||previous.dictionarySnapshots()==null?Map.of():previous.dictionarySnapshots());
        }
        return spec.withSnapshots(Map.copyOf(snapshots));
    }
    private void collect(AdvancedFilterGroupReqVO group,String operand,Map<String,String> fields,Map<String,String> snapshots,Map<String,String> previous) {
        if(group==null)return;
        if(group.getConditions()!=null)for(var condition:group.getConditions()) {
            String type=fields.get(condition.getFieldKey());if(type==null||condition.getValue()==null)continue;
            List<?> values=condition.getValue() instanceof Collection<?> c?new ArrayList<>(c):List.of(condition.getValue());
            dictionaries.validateDictDataList(type,values.stream().map(String::valueOf).toList());
            var entries=dictionaries.getDictDataList(type);
            for(Object value:values) {
                String code=String.valueOf(value),key=operand+":"+condition.getFieldKey()+":"+type+":"+code;
                String label=previous.get(key);
                if(label==null)label=entries.stream().filter(d->code.equals(d.getValue())&&Integer.valueOf(0).equals(d.getStatus())).findFirst().orElseThrow(() ->
                        cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(
                                cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.HOME_METRIC_INVALID)).getLabel();
                snapshots.put(key,label);
            }
        }
        if(group.getGroups()!=null)for(var child:group.getGroups())collect(child,operand,fields,snapshots,previous);
    }
}
