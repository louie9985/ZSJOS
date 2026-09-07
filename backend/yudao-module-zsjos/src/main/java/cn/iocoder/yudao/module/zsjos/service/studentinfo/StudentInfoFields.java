package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.hutool.core.util.IdcardUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.ip.AreaApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormValueDO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.*;

@Component
public class StudentInfoFields {
    @Resource private DictDataApi dictionaries;
    @Resource private AreaApi areas;

    public List<Field> presets() {
        String[] keys = {"registration_category","skill_level_name","name","gender","age","id_card",
                "household_area","mobile","education_level","school","graduation_time","employer","job",
                "study_purpose","mailing_address","registration_teacher"};
        String[] labels = {"报名分类","技能等级名称","姓名","性别","年龄","身份证号码","户籍所在地","手机号",
                "现学历层次","毕业院校","毕业时间","工作单位","岗位","您报名的学习目的","邮寄地址","报名老师"};
        List<Field> result = new ArrayList<>();
        for (int i=0; i<keys.length; i++) {
            Field f = new Field();
            f.setKey(keys[i]); f.setLabel(labels[i]); f.setEnabled(true);
            f.setRequired(Set.of("name","mobile").contains(keys[i]));
            f.setSensitive(Set.of("id_card","mobile").contains(keys[i]));
            f.setSort((i+1)*10); f.setNote("");
            f.setType(Set.of(0,1,3,8).contains(i) ? "dict" : i==6 ? "area" : Set.of(13,14).contains(i) ? "textarea" : "text");
            if (i==3) f.setDictType("system_user_sex");
            result.add(f);
        }
        return result;
    }

    public List<Field> parse(String json) { return JsonUtils.parseArray(json, Field.class); }

    public List<Field> validateConfig(List<Field> input, boolean publishing) {
        if (input == null || input.size()!=16) throw exception(CONFIG_INVALID);
        Map<String,Field> preset = new HashMap<>();
        presets().forEach(f -> preset.put(f.getKey(), f));
        Set<String> seen = new HashSet<>();
        for (Field f : input) {
            Field p = f == null ? null : preset.get(f.getKey());
            if (p == null || !seen.add(f.getKey()) || !p.getType().equals(f.getType())
                    || !p.getLabel().equals(f.getLabel()) || !Objects.equals(p.getSensitive(), f.getSensitive())
                    || f.getEnabled()==null || f.getRequired()==null || f.getSort()==null
                    || f.getSort()<0 || f.getSort()>10000 || f.getNote()!=null && f.getNote().length()>500)
                throw exception(CONFIG_INVALID);
            if (!"dict".equals(f.getType())) f.setDictType(null);
            if ("gender".equals(f.getKey()) && !"system_user_sex".equals(f.getDictType())) throw exception(CONFIG_INVALID);
            if (publishing && f.getEnabled() && "dict".equals(f.getType())) options(f);
        }
        if (publishing && input.stream().noneMatch(Field::getEnabled)) throw exception(CONFIG_INVALID);
        return input.stream().sorted(Comparator.comparing(Field::getSort).thenComparing(Field::getKey)).toList();
    }

    public List<Option> options(Field f) {
        if (f.getDictType()==null || f.getDictType().isBlank()) throw exception(REFERENCE_INVALID, f.getLabel());
        var data = dictionaries.getDictDataList(f.getDictType());
        List<Option> options = data == null ? List.of() : data.stream()
                .filter(d -> Objects.equals(d.getStatus(), 0))
                .map(d -> new Option(d.getValue(), d.getLabel())).toList();
        if (options.isEmpty()) throw exception(REFERENCE_INVALID, f.getLabel());
        return options;
    }

    public List<StudentInfoFormValueDO> validateValues(List<Field> fields, Map<String,Object> values) {
        if (values==null || values.size()>16) throw exception(FIELD_INVALID, "字段数量");
        Set<String> allowed = new HashSet<>();
        fields.stream().filter(Field::getEnabled).forEach(f -> allowed.add(f.getKey()));
        if (!allowed.containsAll(values.keySet())) throw exception(FIELD_INVALID, "未知或停用字段");
        List<StudentInfoFormValueDO> result = new ArrayList<>();
        for (Field f : fields) {
            if (!f.getEnabled()) continue;
            Object raw = values.get(f.getKey());
            if (raw==null || raw instanceof String s && s.isBlank() || raw instanceof List<?> list && list.isEmpty()) {
                if (f.getRequired()) throw exception(FIELD_INVALID, f.getLabel());
                continue;
            }
            StudentInfoFormValueDO v = new StudentInfoFormValueDO();
            v.setFieldKey(f.getKey()); v.setFieldType(f.getType()); v.setSensitive(f.getSensitive());
            if ("area".equals(f.getType())) {
                if (!(raw instanceof List<?> path) || path.isEmpty() || path.size()>5)
                    throw exception(FIELD_INVALID, f.getLabel());
                List<String> codes = new ArrayList<>(), labels = new ArrayList<>();
                Integer parent = 1;
                cn.iocoder.yudao.module.system.api.ip.dto.AreaRespDTO node = null;
                for (Object part : path) {
                    if (!(part instanceof Number) || !part.toString().matches("[0-9]{1,9}"))
                        throw exception(FIELD_INVALID, f.getLabel());
                    node = areas.getArea(((Number)part).intValue());
                    if (node==null || !Objects.equals(node.getParentId(),parent) || !Objects.equals(node.getStatus(),0))
                        throw exception(REFERENCE_INVALID, f.getLabel());
                    codes.add(node.getId().toString()); labels.add(node.getName()); parent=node.getId();
                }
                if ((node.getType()==null || node.getType()<3) && !Boolean.TRUE.equals(node.getLeafSelectable()))
                    throw exception(REFERENCE_INVALID, f.getLabel());
                v.setAreaCodePath(String.join("/",codes)); v.setAreaLabelSnapshot(String.join(" / ",labels));
            } else {
                if (!(raw instanceof String text)) throw exception(FIELD_INVALID, f.getLabel());
                text=text.trim();
                int limit="textarea".equals(f.getType()) ? 2000 : 200;
                if (text.length()>limit) throw exception(FIELD_INVALID, f.getLabel());
                if ("mobile".equals(f.getKey()) && !text.matches("1[3-9][0-9]{9}")) throw exception(FIELD_INVALID,f.getLabel());
                if ("id_card".equals(f.getKey()) && !IdcardUtil.isValidCard(text)) throw exception(FIELD_INVALID,f.getLabel());
                if ("dict".equals(f.getType())) {
                    String code=text;
                    Option o=options(f).stream().filter(d -> code.equals(d.getValue())).findFirst()
                            .orElseThrow(() -> exception(REFERENCE_INVALID,f.getLabel()));
                    v.setDictType(f.getDictType()); v.setValueCode(code); v.setValueLabelSnapshot(o.getLabel());
                } else v.setValueText(text);
            }
            result.add(v);
        }
        return result;
    }
}
