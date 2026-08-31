package cn.iocoder.yudao.module.zsjos.service.director;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.system.api.ip.AreaApi;
import cn.iocoder.yudao.module.system.api.ip.dto.AreaRespDTO;
import cn.iocoder.yudao.module.zsjos.controller.admin.director.vo.DirectorFormTemplateVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.director.DirectorFormTemplateDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.director.DirectorFormTemplateVersionDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.director.DirectorFormTemplateMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.director.DirectorFormTemplateVersionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DirectorFormTemplateServiceTest {

    @InjectMocks private DirectorFormTemplateService service;
    @Mock private DirectorFormTemplateMapper templateMapper;
    @Mock private DirectorFormTemplateVersionMapper versionMapper;
    @Mock private DictDataApi dictDataApi;
    @Mock private AreaApi areaApi;

    @Test
    void existingDraftKeepsArchivedVersionAndHistoricalDictionaryLabel() {
        prepareVersion("archived");
        Map<String, Object> previous = Map.of("choice", Map.of(
                "value", "old", "labelSnapshot", "提交时标签", "dictType", "zsjos_test"));

        DirectorFormTemplateVO.Snapshot snapshot = service.validateAndSnapshotVersion(
                DirectorFormTemplateService.SCENE_POSITIONING, 11L, Map.of("choice", "old"), false, previous);

        assertEquals(11L, snapshot.getTemplateVersionId());
        assertEquals("提交时标签", ((Map<?, ?>) snapshot.getDictSnapshots().get("choice")).get("labelSnapshot"));
        verifyNoInteractions(dictDataApi);
    }

    @Test
    void changedDraftSelectionMustUseAnEnabledCurrentDictionaryItem() {
        prepareVersion("published");
        DictDataRespDTO disabled = new DictDataRespDTO();
        disabled.setValue("disabled");
        disabled.setLabel("已停用");
        disabled.setStatus(1);
        when(dictDataApi.getDictDataList("zsjos_test")).thenReturn(List.of(disabled));

        assertThrows(ServiceException.class, () -> service.validateAndSnapshotVersion(
                DirectorFormTemplateService.SCENE_POSITIONING, 11L,
                Map.of("choice", "disabled"), false, Map.of()));
    }

    @Test
    void regionUsesSystemAreaAndStoresAuthoritativeLabelSnapshot() {
        prepareRegionVersion();
        AreaRespDTO area = new AreaRespDTO();
        area.setId(110000); area.setName("北京市"); area.setStatus(0);
        when(areaApi.getArea(110000)).thenReturn(area);

        DirectorFormTemplateVO.Snapshot snapshot = service.validateAndSnapshotVersion(
                DirectorFormTemplateService.SCENE_INTERVIEW, 12L,
                Map.of("region", Map.of("code", 110000, "labelSnapshot", "客户端伪造")), false, Map.of());

        assertEquals(Map.of("code", 110000, "labelSnapshot", "北京市"), snapshot.getValues().get("region"));
    }

    @Test
    void historicalRawRegionCanRemainInDraftButCannotBeSubmitted() {
        prepareRegionVersion();

        DirectorFormTemplateVO.Snapshot draft = service.validateAndSnapshotVersion(
                DirectorFormTemplateService.SCENE_INTERVIEW, 12L, Map.of("region", "历史地区文本"), false, Map.of());

        assertEquals("历史地区文本", draft.getValues().get("region"));
        assertThrows(ServiceException.class, () -> service.validateAndSnapshotVersion(
                DirectorFormTemplateService.SCENE_INTERVIEW, 12L, Map.of("region", "历史地区文本"), true, Map.of()));
        verifyNoInteractions(areaApi);
    }

    @Test
    void disabledSystemAreaIsRejected() {
        prepareRegionVersion();
        AreaRespDTO area = new AreaRespDTO();
        area.setId(110000); area.setName("北京市"); area.setStatus(1);
        when(areaApi.getArea(110000)).thenReturn(area);

        assertThrows(ServiceException.class, () -> service.validateAndSnapshotVersion(
                DirectorFormTemplateService.SCENE_INTERVIEW, 12L,
                Map.of("region", Map.of("code", 110000)), false, Map.of()));
    }

    @Test
    void incompleteValuesAreAllowedInDraftButRejectedOnSubmit() {
        DirectorFormTemplateVO.Field choices = new DirectorFormTemplateVO.Field();
        choices.setKey("choices"); choices.setTitle("选择"); choices.setType("checkbox_group");
        choices.setEnabled(true); choices.setRequired(true); choices.setSystemField(false); choices.setSort(1);
        choices.setDictType("zsjos_test"); choices.setMultiple(true); choices.setMinSelections(3);
        choices.setMaxLength(null);
        DirectorFormTemplateVO.Field description = new DirectorFormTemplateVO.Field();
        description.setKey("description"); description.setTitle("说明"); description.setType("text");
        description.setEnabled(true); description.setRequired(true); description.setSystemField(false);
        description.setSort(2); description.setMaxLength(3);
        prepareFieldsVersion(List.of(choices, description));
        DictDataRespDTO option = new DictDataRespDTO();
        option.setValue("one"); option.setLabel("一项"); option.setStatus(0);
        when(dictDataApi.getDictDataList("zsjos_test")).thenReturn(List.of(option));
        Map<String, Object> values = Map.of("choices", List.of("one"), "description", "unfinished");

        DirectorFormTemplateVO.Snapshot draft = service.validateAndSnapshotVersion(
                DirectorFormTemplateService.SCENE_POSITIONING, 13L, values, false, Map.of());

        assertEquals(values, draft.getValues());
        assertThrows(ServiceException.class, () -> service.validateAndSnapshotVersion(
                DirectorFormTemplateService.SCENE_POSITIONING, 13L, values, true, Map.of()));
    }

    @Test
    void emptyRequiredValuesAreAllowedOnlyInDraft() {
        DirectorFormTemplateVO.Field field = textField("requiredText", true);
        prepareFieldsVersion(List.of(field));

        DirectorFormTemplateVO.Snapshot draft = service.validateAndSnapshotVersion(
                DirectorFormTemplateService.SCENE_POSITIONING, 13L, Map.of(), false, Map.of());

        assertEquals(Map.of(), draft.getValues());
        assertThrows(ServiceException.class, () -> service.validateAndSnapshotVersion(
                DirectorFormTemplateService.SCENE_POSITIONING, 13L, Map.of(), true, Map.of()));
    }

    @Test
    void draftStillRejectsValuesWithTheWrongFieldType() {
        prepareFieldsVersion(List.of(textField("description", false)));

        assertThrows(ServiceException.class, () -> service.validateAndSnapshotVersion(
                DirectorFormTemplateService.SCENE_POSITIONING, 13L,
                Map.of("description", 123), false, Map.of()));
    }

    private void prepareVersion(String status) {
        DirectorFormTemplateVO.Field field = new DirectorFormTemplateVO.Field();
        field.setKey("choice");
        field.setTitle("选择");
        field.setType("radio");
        field.setEnabled(true);
        field.setRequired(true);
        field.setSystemField(false);
        field.setSort(1);
        field.setDictType("zsjos_test");
        field.setMultiple(false);
        DirectorFormTemplateVersionDO version = new DirectorFormTemplateVersionDO().setId(11L)
                .setTemplateId(10L).setVersionNo(1).setStatus(status)
                .setFieldsJson(JsonUtils.toJsonString(List.of(field)));
        when(versionMapper.selectById(11L)).thenReturn(version);
        when(templateMapper.selectById(10L)).thenReturn(new DirectorFormTemplateDO().setId(10L)
                .setScene(DirectorFormTemplateService.SCENE_POSITIONING));
    }

    private void prepareRegionVersion() {
        DirectorFormTemplateVO.Field field = new DirectorFormTemplateVO.Field();
        field.setKey("region");
        field.setTitle("地域");
        field.setType("region");
        field.setEnabled(true);
        field.setRequired(true);
        field.setSystemField(true);
        field.setSort(1);
        DirectorFormTemplateVersionDO version = new DirectorFormTemplateVersionDO().setId(12L)
                .setTemplateId(20L).setVersionNo(1).setStatus("published")
                .setFieldsJson(JsonUtils.toJsonString(List.of(field)));
        when(versionMapper.selectById(12L)).thenReturn(version);
        when(templateMapper.selectById(20L)).thenReturn(new DirectorFormTemplateDO().setId(20L)
                .setScene(DirectorFormTemplateService.SCENE_INTERVIEW));
    }

    private void prepareFieldsVersion(List<DirectorFormTemplateVO.Field> fields) {
        DirectorFormTemplateVersionDO version = new DirectorFormTemplateVersionDO().setId(13L)
                .setTemplateId(30L).setVersionNo(1).setStatus("published")
                .setFieldsJson(JsonUtils.toJsonString(fields));
        when(versionMapper.selectById(13L)).thenReturn(version);
        when(templateMapper.selectById(30L)).thenReturn(new DirectorFormTemplateDO().setId(30L)
                .setScene(DirectorFormTemplateService.SCENE_POSITIONING));
    }

    private DirectorFormTemplateVO.Field textField(String key, boolean required) {
        DirectorFormTemplateVO.Field field = new DirectorFormTemplateVO.Field();
        field.setKey(key); field.setTitle("说明"); field.setType("text"); field.setEnabled(true);
        field.setRequired(required); field.setSystemField(false); field.setSort(1);
        return field;
    }
}
