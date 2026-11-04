package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.hutool.core.util.IdcardUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.ip.IdentityAreaApi;
import cn.iocoder.yudao.module.system.api.ip.dto.IdentityAreaRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormValueDO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.FIELD_INVALID;

@Service
public class StudentIdentityAreaService {
    @Resource private IdentityAreaApi identityAreas;
    @Resource private StudentInfoFields fields;

    public IdentityAreaRespDTO resolve(String code) {
        if (code == null || !code.matches("[0-9]{6}")) throw exception(FIELD_INVALID, "身份证地址码");
        try { return identityAreas.resolve(code); }
        catch (org.springframework.dao.DataAccessException unavailable) {
            var result = new IdentityAreaRespDTO(); result.setCode(code); result.setStatus("UNAVAILABLE"); return result;
        }
    }

    public List<StudentInfoFormValueDO> validate(List<StudentInfoVO.Field> definitions, Map<String,Object> input,
                                                StudentInfoVO.IdentityAreaInput area) {
        if (area == null) return fields.validateCollectionValues(definitions, input);
        var definition = definitions.stream().filter(f -> Boolean.TRUE.equals(f.getEnabled()) && "household_area".equals(f.getKey()))
                .findFirst().orElseThrow(() -> exception(FIELD_INVALID,"户籍所在地"));
        String card = input.get("id_card") instanceof String text ? text.trim().toUpperCase(Locale.ROOT) : "";
        if (!card.matches("[0-9]{17}[0-9X]") || !IdcardUtil.isValidCard(card)) throw exception(FIELD_INVALID,"身份证号码");
        String code = card.substring(0,6);
        if (!code.equals(area.getCode())) throw exception(FIELD_INVALID,"身份证已变更，请重新确认地区");
        var resolved = resolve(code);
        boolean matched = Set.of("CURRENT","HISTORICAL").contains(resolved.getStatus());
        var values = new LinkedHashMap<>(input); values.remove("household_area"); values.put("id_card",card);
        var result = new ArrayList<>(fields.validateCollectionValues(definitions.stream().filter(f -> !"household_area".equals(f.getKey())).toList(), values));
        StudentInfoFormValueDO value;
        if ("AUTO".equals(area.getMode())) {
            if (!matched) throw exception(FIELD_INVALID,"地区未识别，请手动填写");
            value = new StudentInfoFormValueDO(); value.setAreaLabelSnapshot(resolved.getLabel());
        } else {
            // A client cannot claim lookup failure to override a recognized address code.
            if (matched) throw exception(FIELD_INVALID,"地区资料已可识别，请重新确认自动识别结果");
            if ("MANUAL_SELECTION".equals(area.getMode())) {
                value = fields.validateValues(List.of(definition), Map.of("household_area",Objects.requireNonNullElse(area.getPath(),List.of()))).getFirst();
            } else if ("MANUAL_TEXT".equals(area.getMode())) {
                String text = area.getText() == null ? "" : area.getText().trim();
                if (text.length()<2 || text.length()>200 || text.chars().anyMatch(Character::isISOControl))
                    throw exception(FIELD_INVALID,"请填写完整地区名称（2至200字）");
                value = new StudentInfoFormValueDO(); value.setAreaLabelSnapshot(text);
            } else throw exception(FIELD_INVALID,"地区填写方式");
        }
        value.setFieldKey("household_area"); value.setFieldType("area"); value.setSensitive(definition.getSensitive());
        Map<String,Object> snapshot = new LinkedHashMap<>(); snapshot.put("code",code); snapshot.put("source",area.getMode());
        snapshot.put("status",resolved.getStatus()); snapshot.put("dataVersion",resolved.getDataVersion());
        if (!"AUTO".equals(area.getMode())) snapshot.put("input",area);
        value.setIdentityAreaSnapshot(JsonUtils.toJsonString(snapshot)); result.add(value); return result;
    }
}
