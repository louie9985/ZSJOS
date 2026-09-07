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
    @Test void rejectsExtraFieldsChangedTypesAndEmptyPublication() {
        var config=fields.presets();
        assertEquals(16, config.size());
        config.get(0).setType("text");
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
}
