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
        String[] keys = {"name","id_card","gender","age","ethnicity","household_area","mobile","retraining","education_level",
                "school","graduation_major","graduation_time","registration_level","target_school","target_major",
                "employer","job","certificates","study_purpose","portrait","identity_images","diploma_images",
                "education_report","application_images","work_proof","social_security","labor_contract","prior_certificate",
                "certificate_lookup","commitment"};
        String[] labels = {"姓名","身份证号码","性别","年龄","民族","户籍所在地","手机号码","是否复训","现学历",
                "毕业院校","毕业专业","毕业时间","报名层次","拟报名院校","拟报名专业","工作单位","从事职业",
                "目前持有证书","拟报名学习的目的","证件照片","身份证正反面","毕业证","学历认证报备","报名申请表",
                "工作证明","社保流水","劳动合同","前置证书","前置证书的网查截图","承诺书"};
        List<Field> result = new ArrayList<>();
        for (int i=0; i<keys.length; i++) {
            Field f = new Field();
            f.setKey(keys[i]); f.setLabel(labels[i]); f.setEnabled(true);
            f.setRequired(Set.of("name","id_card","household_area","mobile","education_level","graduation_major",
                    "graduation_time","job","certificates","study_purpose").contains(keys[i]));
            f.setSensitive(Set.of("id_card","mobile").contains(keys[i]));
            f.setSort((i+1)*10); f.setNote("");
            f.setType(Set.of(2,4,7,8,12).contains(i) ? "dict" : i==5 ? "area" : i==18 ? "textarea" : i>=19 ? "images" : "text");
            f.setGroup(i<9 ? "basic" : i<15 ? "education" : i<19 ? "career" : "materials");
            if (i>=19) { f.setMaxImages(9); f.setMaxSizeMb(ATTACHMENT_MAX_SIZE_MB); }
            if (i==2) f.setDictType("system_user_sex");
            if (i==4) f.setDictType("zsjos_student_info_ethnicity");
            if (i==7) f.setDictType("zsjos_student_info_retraining");
            if (i==8) f.setDictType("zsjos_student_info_education_level");
            if (i==12) f.setDictType("zsjos_student_info_registration_level");
            result.add(f);
        }
        for (Field field : result) if ("images".equals(field.getType())) field.setAllowedExtensions(extensions(field.getKey()));
        for (String key : List.of("gift_shipping_address", "certificate_shipping_address")) {
            Field f = new Field(); f.setKey(key);
            f.setLabel("gift_shipping_address".equals(key) ? "礼品邮寄地址" : "证书邮寄地址");
            f.setType("textarea"); f.setGroup("basic"); f.setEnabled(true); f.setRequired(false);
            f.setSensitive(true); f.setSort("gift_shipping_address".equals(key) ? 91 : 92); f.setNote(""); result.add(f);
        }
        return result.stream().sorted(Comparator.comparing(Field::getSort)).toList();
    }

    public static List<String> extensions(String key) {
        if ("education_report".equals(key)) return List.of("pdf");
        if ("application_images".equals(key)) return List.of("jpg", "jpeg", "png", "pdf", "doc", "docx");
        if (Set.of("work_proof", "social_security", "labor_contract", "commitment").contains(key)) return List.of("jpg", "jpeg", "png", "pdf");
        return List.of("jpg", "jpeg", "png");
    }

    // Configurations are editable; issued form snapshots must never pass through this upgrade.
    public List<Field> upgradeConfig(List<Field> input) {
        List<Field> result = new ArrayList<>(input);
        for (Field preset : presets()) {
            var existing = result.stream().filter(f -> f.getKey().equals(preset.getKey())).findFirst();
            if (existing.isEmpty()) result.add(preset);
            else if ("images".equals(existing.get().getType())) { existing.get().setAllowedExtensions(preset.getAllowedExtensions()); existing.get().setMaxSizeMb(ATTACHMENT_MAX_SIZE_MB); }
        }
        return result.stream().sorted(Comparator.comparing(Field::getSort).thenComparing(Field::getKey)).toList();
    }

    public List<Field> parse(String json) { return JsonUtils.parseArray(json, Field.class); }

    public List<Field> withAttachmentPolicy(List<Field> input) {
        for (Field field : input) if ("images".equals(field.getType())) {
            field.setAllowedExtensions(extensions(field.getKey()));
            // The current upload policy applies to pending forms without rewriting issued snapshots.
            field.setMaxSizeMb(ATTACHMENT_MAX_SIZE_MB);
        }
        return input;
    }

    public List<Field> validateConfig(List<Field> input, boolean publishing) {
        if (input == null || (input.size()!=30 && input.size()!=32)) throw exception(CONFIG_INVALID);
        Map<String,Field> preset = new HashMap<>();
        presets().forEach(f -> preset.put(f.getKey(), f));
        Set<String> seen = new HashSet<>();
        for (Field f : input) {
            Field p = f == null ? null : preset.get(f.getKey());
            if (p == null || !seen.add(f.getKey()) || !p.getType().equals(f.getType())
                    || !p.getLabel().equals(f.getLabel()) || !Objects.equals(p.getSensitive(), f.getSensitive())
                    || f.getGroup()==null || !Set.of("basic","education","career","materials").contains(f.getGroup())
                    || f.getEnabled()==null || f.getRequired()==null || f.getSort()==null
                    || f.getSort()<0 || f.getSort()>10000 || f.getNote()!=null && f.getNote().length()>500)
                throw exception(CONFIG_INVALID);
            if ("images".equals(f.getType()) && (f.getMaxImages()==null || f.getMaxImages()<2 || f.getMaxImages()>9
                    || f.getMaxSizeMb()==null || f.getMaxSizeMb()<1 || f.getMaxSizeMb()>ATTACHMENT_MAX_SIZE_MB)) throw exception(CONFIG_INVALID);
            if ("images".equals(f.getType())) f.setMaxSizeMb(ATTACHMENT_MAX_SIZE_MB);
            if (!"dict".equals(f.getType())) f.setDictType(null);
            f.setAllowedExtensions(p.getAllowedExtensions());
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
        if (!"zsjos_student_info_ethnicity".equals(f.getDictType())) return options;
        // Keep the administrator-maintained order for all other ethnicities, but make the common Han choice immediately visible.
        return options.stream().sorted(Comparator.comparing((Option option) -> !"01".equals(option.getValue()))).toList();
    }

    // Public collection derives identity fields; archive maintenance keeps its separate historical snapshot rules.
    public List<StudentInfoFormValueDO> validateCollectionValues(List<Field> fields, Map<String,Object> input) {
        if (input == null) throw exception(FIELD_INVALID, "字段数量");
        Map<String,Object> values = new LinkedHashMap<>(input);
        var enabled = fields.stream().filter(Field::getEnabled).map(Field::getKey).collect(java.util.stream.Collectors.toSet());
        String card = input.get("id_card") instanceof String text ? text.trim().toUpperCase(Locale.ROOT) : "";
        if (enabled.contains("id_card") && !card.isEmpty()) {
            if (!card.matches("[0-9]{17}[0-9X]") || !IdcardUtil.isValidCard(card)) throw exception(FIELD_INVALID,"身份证号码");
            values.put("id_card", card);
            if (enabled.contains("household_area")) {
                String code = card.substring(0, 6);
                var node = areas.getArea(Integer.parseInt(code));
                if (node == null) {
                    // System may assign internal IDs separately from the administrative selection code.
                    Integer parent = 1;
                    for (String part : new LinkedHashSet<>(List.of(code.substring(0,2)+"0000", code.substring(0,4)+"00", code))) {
                        node = areas.getAreaByParentIdAndSelectionCode(parent, part);
                        if (node == null) node = areas.getAreaByParentIdAndSelectionCode(parent, code);
                        if (node == null || code.equals(node.getSelectionCode())) break;
                        parent = node.getId();
                    }
                }
                if (node == null || !code.equals(node.getSelectionCode() == null || node.getSelectionCode().isBlank()
                        ? String.valueOf(node.getId()) : node.getSelectionCode()))
                    throw exception(FIELD_INVALID,"身份证地址码未匹配到地区，请联系报名老师维护地区配置");
                List<Integer> path = new ArrayList<>(); Set<Integer> visited = new HashSet<>();
                while (node != null && path.size() < 5 && visited.add(node.getId())) {
                    path.addFirst(node.getId());
                    if (Objects.equals(node.getParentId(), 1)) break;
                    node = areas.getArea(node.getParentId());
                }
                if (node == null || !Objects.equals(node.getParentId(), 1))
                    throw exception(FIELD_INVALID,"身份证地区层级无效，请联系报名老师维护地区配置");
                values.put("household_area",path);
            }
        } else {
            for (String derived : List.of("age","gender","household_area")) if (enabled.contains(derived)) {
                Object raw = input.get(derived);
                if (raw != null && !(raw instanceof String text && text.isBlank()) && !(raw instanceof List<?> list && list.isEmpty()))
                    throw exception(FIELD_INVALID,"请填写有效身份证以自动识别年龄、性别和户籍所在地");
            }
        }
        return validateValues(fields, values);
    }

    public List<StudentInfoFormValueDO> validateValues(List<Field> fields, Map<String,Object> values) {
        if (values==null || values.size()>32) throw exception(FIELD_INVALID, "字段数量");
        Set<String> allowed = new HashSet<>();
        fields.stream().filter(Field::getEnabled).forEach(f -> allowed.add(f.getKey()));
        if (!allowed.containsAll(values.keySet())) throw exception(FIELD_INVALID, "未知或停用字段");
        values = new LinkedHashMap<>(values);
        if (values.get("id_card") instanceof String card && !card.isBlank()) {
            if (!card.matches("[0-9]{17}[0-9Xx]") || !IdcardUtil.isValidCard(card)) throw exception(FIELD_INVALID,"身份证号码");
            var birth=java.time.LocalDate.parse(card.substring(6,14),java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            var today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
            if (birth.isAfter(today)) throw exception(FIELD_INVALID,"身份证号码");
            if (allowed.contains("age")) values.put("age",String.valueOf(java.time.Period.between(birth,today).getYears()));
            if (allowed.contains("gender")) values.put("gender",Integer.parseInt(card.substring(16,17))%2==1 ? "1" : "2");
        }
        List<StudentInfoFormValueDO> result = new ArrayList<>();
        for (Field f : fields) {
            if (!f.getEnabled() || "images".equals(f.getType())) continue;
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
