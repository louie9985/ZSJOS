package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.ip.AreaApi;
import cn.iocoder.yudao.module.system.api.ip.dto.AreaRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Field;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentInfoFieldsTest {
    @InjectMocks StudentInfoFields fields;
    @Mock DictDataApi dictionaries;
    @Mock AreaApi areas;
    private Field field(String key) { return fields.presets().stream().filter(f -> key.equals(f.getKey())).findFirst().orElseThrow(); }
    @Test void presetsBindOwningBusinessDictionaries() {
        assertEquals("zsjos_student_info_ethnicity",field("ethnicity").getDictType());
        assertEquals("zsjos_student_info_retraining",field("retraining").getDictType());
        assertEquals("zsjos_student_info_education_level",field("education_level").getDictType());
        assertEquals("zsjos_student_info_registration_level",field("registration_level").getDictType());
        assertEquals("system_user_sex",field("gender").getDictType());
    }
    @Test void rejectsExtraFieldsChangedTypesAndEmptyPublication() {
        var config=fields.presets();
        assertEquals(32, config.size());
        config.get(0).setType("dict");
        assertThrows(ServiceException.class, () -> fields.validateConfig(config,false));
        var disabled=fields.presets(); disabled.forEach(f -> f.setEnabled(false));
        assertThrows(ServiceException.class, () -> fields.validateConfig(disabled,true));
        assertThrows(ServiceException.class, () -> fields.validateValues(List.of(field("name")),Map.of("internal_id","1")));
    }
    @Test void rejectsMissingRequiredWrongTypesAndInvalidIdentity() {
        assertThrows(ServiceException.class, () -> fields.validateValues(List.of(field("name")),Map.of()));
        assertThrows(ServiceException.class, () -> fields.validateValues(List.of(field("name")),Map.of("name",42)));
        assertThrows(ServiceException.class, () -> fields.validateValues(List.of(field("mobile")),Map.of("mobile","123")));
        assertThrows(ServiceException.class, () -> fields.validateValues(List.of(field("id_card")),Map.of("id_card","000000000000000000")));
        assertTrue(fields.validateValues(List.of(field("school")),Map.of()).isEmpty());
    }
    @Test void snapshotsDictionaryAndRejectsDisabledCode() {
        var entry=new DictDataRespDTO(); entry.setValue("code"); entry.setLabel("原标签"); entry.setStatus(0);
        when(dictionaries.getDictDataList("system_user_sex")).thenReturn(List.of(entry));
        var answer=fields.validateValues(List.of(field("gender")),Map.of("gender","code")).getFirst();
        entry.setLabel("新标签");
        assertEquals("原标签",answer.getValueLabelSnapshot());
        assertEquals("code",answer.getValueCode());
        assertEquals("system_user_sex",answer.getDictType());
        entry.setStatus(1);
        assertThrows(ServiceException.class, () -> fields.validateValues(List.of(field("gender")),Map.of("gender","code")));
    }

    @Test void putsHanEthnicityFirstWhilePreservingConfiguredOrderForOthers() {
        var manchu = new DictDataRespDTO(); manchu.setValue("08"); manchu.setLabel("满族"); manchu.setStatus(0);
        var han = new DictDataRespDTO(); han.setValue("01"); han.setLabel("汉族"); han.setStatus(0);
        var hui = new DictDataRespDTO(); hui.setValue("03"); hui.setLabel("回族"); hui.setStatus(0);
        when(dictionaries.getDictDataList("zsjos_student_info_ethnicity")).thenReturn(List.of(manchu, han, hui));
        var options = fields.options(field("ethnicity"));
        assertEquals(List.of("01", "08", "03"), options.stream().map(option -> option.getValue()).toList());
    }
    @Test void areaRequiresEnabledParentChainAndSnapshotsLabels() {
        var province=area(10,1,2,"测试省"); province.setLeafSelectable(true);
        when(areas.getArea(10)).thenReturn(province);
        var answer=fields.validateValues(List.of(field("household_area")),Map.of("household_area",List.of(10))).getFirst();
        province.setName("新名称");
        assertEquals("测试省",answer.getAreaLabelSnapshot());
        assertEquals("10",answer.getAreaCodePath());
        province.setParentId(99);
        assertThrows(ServiceException.class, () -> fields.validateValues(List.of(field("household_area")),Map.of("household_area",List.of(10))));
        assertThrows(ServiceException.class, () -> fields.validateValues(List.of(field("household_area")),Map.of("household_area",List.of("10"))));
    }
    private AreaRespDTO area(int id,int parent,int type,String name) {
        var dto=new AreaRespDTO(); dto.setId(id); dto.setParentId(parent); dto.setType(type); dto.setName(name); dto.setStatus(0); return dto;
    }

    @Test void collectionOverridesClientIdentityValuesAndSnapshotsAuthoritativeArea() {
        var province=area(110000,1,1,"测试省"); var city=area(110100,110000,2,"测试市"); var county=area(110101,110100,3,"测试区");
        when(areas.getArea(110000)).thenReturn(province); when(areas.getArea(110100)).thenReturn(city); when(areas.getArea(110101)).thenReturn(county);
        var sex=new DictDataRespDTO(); sex.setValue("1"); sex.setLabel("字典男性标签"); sex.setStatus(0);
        when(dictionaries.getDictDataList("system_user_sex")).thenReturn(List.of(sex));
        var result=fields.validateCollectionValues(List.of(field("id_card"),field("age"),field("gender"),field("household_area")),
                Map.of("id_card",identity("11010120001019001"),"age","999","gender","2","household_area",List.of(999)));
        assertEquals("110000/110100/110101",result.stream().filter(v->"household_area".equals(v.getFieldKey())).findFirst().orElseThrow().getAreaCodePath());
        assertEquals("测试省 / 测试市 / 测试区",result.stream().filter(v->"household_area".equals(v.getFieldKey())).findFirst().orElseThrow().getAreaLabelSnapshot());
        assertEquals("字典男性标签",result.stream().filter(v->"gender".equals(v.getFieldKey())).findFirst().orElseThrow().getValueLabelSnapshot());
        assertEquals(String.valueOf(java.time.Period.between(java.time.LocalDate.of(2000,10,19),java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))).getYears()),
                result.stream().filter(v->"age".equals(v.getFieldKey())).findFirst().orElseThrow().getValueText());
    }
    @Test void collectionRejectsUnknownAndDisabledRegionsAndManualValuesWithoutIdentity() {
        var definitions=List.of(field("id_card"),field("household_area"));
        assertThrows(ServiceException.class,()->fields.validateCollectionValues(definitions,Map.of("id_card",identity("11010120000101001"))));
        var disabled=area(110101,1,3,"停用区"); disabled.setStatus(1); when(areas.getArea(110101)).thenReturn(disabled);
        assertThrows(ServiceException.class,()->fields.validateCollectionValues(definitions,Map.of("id_card",identity("11010120000101001"))));
        assertThrows(ServiceException.class,()->fields.validateCollectionValues(List.of(field("age")),Map.of("age","28")));
    }
    @Test void collectionResolvesAdministrativeCodeSeparatelyFromInternalIds() {
        var province=area(10,1,1,"测试省"); province.setSelectionCode("110000");
        var city=area(20,10,2,"测试市"); city.setSelectionCode("110100");
        var county=area(30,20,3,"测试区"); county.setSelectionCode("110101");
        when(areas.getArea(110101)).thenReturn(null);
        when(areas.getAreaByParentIdAndSelectionCode(1,"110000")).thenReturn(province);
        when(areas.getAreaByParentIdAndSelectionCode(10,"110100")).thenReturn(city);
        when(areas.getAreaByParentIdAndSelectionCode(20,"110101")).thenReturn(county);
        when(areas.getArea(10)).thenReturn(province); when(areas.getArea(20)).thenReturn(city); when(areas.getArea(30)).thenReturn(county);
        var result=fields.validateCollectionValues(List.of(field("id_card"),field("household_area")),Map.of("id_card",identity("11010120000101001")));
        assertEquals("10/20/30",result.getLast().getAreaCodePath());
    }
    private String identity(String prefix) {
        int[] weights={7,9,10,5,8,4,2,1,6,3,7,9,10,5,8,4,2}; int sum=0;
        for(int i=0;i<weights.length;i++) sum+=(prefix.charAt(i)-'0')*weights[i];
        return prefix+"10X98765432".charAt(sum%11);
    }
}
