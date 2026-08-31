package cn.iocoder.yudao.module.zsjos.service.account;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountFieldConfigRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountDetailSnapshotVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.account.vo.MediaAccountFieldConfigSaveReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountFieldConfigDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.account.MediaAccountFieldConfigMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class MediaAccountFieldConfigServiceTest {
    @InjectMocks private MediaAccountFieldConfigService service;
    @Mock private MediaAccountFieldConfigMapper mapper;
    @Mock private DictDataApi dictDataApi;

    @Test
    void snapshotsLabelsAndDictionaryLabelsFromPublishedVersion() {
        MediaAccountFieldConfigRespVO.FieldVO nickname = field("nickname", "昵称", "text", null, true);
        MediaAccountFieldConfigRespVO.FieldVO level = field("level", "账号等级", "select", "account_level", true);
        when(mapper.selectPublished()).thenReturn(new MediaAccountFieldConfigDO().setId(8L)
                .setStatus("published").setFieldsJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils
                        .toJsonString(List.of(nickname, level))));
        DictDataRespDTO dictionary = new DictDataRespDTO();
        dictionary.setDictType("account_level"); dictionary.setValue("a"); dictionary.setLabel("A级");
        when(dictDataApi.getDictDataList("account_level")).thenReturn(List.of(dictionary));

        MediaAccountFieldConfigService.DetailSnapshot snapshot = service.validateAndSnapshot(
                Map.of("nickname", "中世健课堂", "level", "a"));

        assertEquals(8L, snapshot.configVersionId());
        assertEquals("中世健课堂", snapshot.values().get("nickname"));
        assertEquals("A级", snapshot.snapshots().get(1).getDisplayValue());
        verify(dictDataApi).validateDictDataList("account_level", List.of("a"));
    }

    @Test
    void rejectsUnknownAndMissingRequiredFields() {
        MediaAccountFieldConfigRespVO.FieldVO nickname = field("nickname", "昵称", "text", null, true);
        when(mapper.selectPublished()).thenReturn(new MediaAccountFieldConfigDO().setId(8L)
                .setStatus("published").setFieldsJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils
                        .toJsonString(List.of(nickname))));

        assertThrows(RuntimeException.class, () -> service.validateAndSnapshot(Map.of("unknown", "value")));
        assertThrows(RuntimeException.class, () -> service.validateAndSnapshot(Map.of()));
    }

    @Test
    void preservesUnchangedDictionarySnapshotWithoutCurrentDictionaryLookup() {
        MediaAccountFieldConfigRespVO.FieldVO level = field("level", "账号等级", "select", "account_level", true);
        when(mapper.selectPublished()).thenReturn(new MediaAccountFieldConfigDO().setId(9L)
                .setStatus("published").setFieldsJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils
                        .toJsonString(List.of(level))));
        MediaAccountDetailSnapshotVO previous = new MediaAccountDetailSnapshotVO();
        previous.setKey("level"); previous.setLabel("账号等级"); previous.setType("select");
        previous.setDictType("account_level"); previous.setValue("a"); previous.setDisplayValue("提交时A级");

        MediaAccountFieldConfigService.DetailSnapshot snapshot = service.validateAndSnapshot(
                Map.of("level", "a"), List.of(previous));

        assertEquals("提交时A级", snapshot.snapshots().getFirst().getDisplayValue());
        verifyNoInteractions(dictDataApi);
    }

    @Test
    void copyPublishedCreatesTheNextDraftVersion() {
        MediaAccountFieldConfigDO published = new MediaAccountFieldConfigDO().setId(8L).setVersionNo(3)
                .setVersion(5).setStatus("published").setFieldsJson("[]");
        when(mapper.selectById(8L)).thenReturn(published);
        when(mapper.insert(any(MediaAccountFieldConfigDO.class))).thenAnswer(invocation -> {
            invocation.<MediaAccountFieldConfigDO>getArgument(0).setId(9L);
            return 1;
        });

        assertEquals(9L, service.copyDraft(8L, 5));
        verify(mapper).insert(org.mockito.ArgumentMatchers.argThat((MediaAccountFieldConfigDO draft) ->
                draft.getVersionNo() == 4 && "draft".equals(draft.getStatus()) && draft.getVersion() == 0));
    }

    @Test
    void staleDraftUpdateAndPublishReturnVersionConflict() {
        MediaAccountFieldConfigSaveReqVO request = new MediaAccountFieldConfigSaveReqVO();
        request.setId(9L); request.setVersion(1);
        request.setFields(List.of(field("nickname", "昵称", "text", null, true)));
        when(mapper.updateDraft(any(), any(), any())).thenReturn(0);
        assertThrows(RuntimeException.class, () -> service.updateDraft(request));

        MediaAccountFieldConfigDO draft = new MediaAccountFieldConfigDO().setId(9L).setVersionNo(4)
                .setVersion(2).setStatus("draft").setFieldsJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils
                        .toJsonString(request.getFields()));
        when(mapper.selectById(9L)).thenReturn(draft);
        when(mapper.publish(org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.eq(1), any()))
                .thenReturn(0);
        assertThrows(RuntimeException.class, () -> service.publish(9L, 1));
    }

    private MediaAccountFieldConfigRespVO.FieldVO field(String key, String label, String type,
                                                         String dictType, boolean required) {
        MediaAccountFieldConfigRespVO.FieldVO field = new MediaAccountFieldConfigRespVO.FieldVO();
        field.setKey(key); field.setLabel(label); field.setType(type); field.setDictType(dictType);
        field.setRequired(required); field.setEnabled(true); field.setSearchable(false);
        field.setSort("nickname".equals(key) ? 10 : 20);
        return field;
    }
}
