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
    void homepageUrlRejectsMissingSchemeWithoutNullPointer() {
        when(mapper.selectPublished()).thenReturn(new MediaAccountFieldConfigDO().setId(8L)
                .setStatus("published").setFieldsJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils
                        .toJsonString(List.of(field("homepage_url", "主页链接", "url", null, false)))));
        for (String value : List.of("123", "example.com/profile", "/profile", "//example.com", "https://", "https://bad host", "javascript:alert(1)", "ftp://example.com")) {
            var error = assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                    () -> service.validateAndSnapshot(Map.of("homepage_url", value)), value);
            assertEquals(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.MEDIA_ACCOUNT_FIELD_CONFIG_INVALID.getCode(), error.getCode());
        }
        for (String value : List.of("https://example.com/profile", "http://example.com", "HTTPS://example.com/profile"))
            assertEquals(value, service.validateAndSnapshot(Map.of("homepage_url", value)).values().get("homepage_url"));
    }

    @Test
    void appearanceTextPreservesNewlinesAndEnforcesLength() {
        when(mapper.selectPublished()).thenReturn(new MediaAccountFieldConfigDO().setId(8L)
                .setStatus("published").setFieldsJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils
                        .toJsonString(List.of(field("avatar", "头像设置", "textarea", null, true),
                                field("background", "背景设置", "textarea", null, true)))));
        String text = "中文说明\n" + "字".repeat(1995);
        assertEquals(2000, text.length());
        assertEquals(text, service.validateAndSnapshot(Map.of("avatar", text)).values().get("avatar"));
        assertEquals("背景\n说明", service.validateAndSnapshot(Map.of("background", "背景\n说明")).values().get("background"));
        assertThrows(RuntimeException.class, () -> service.validateAndSnapshot(Map.of("avatar", text + "字")));
        assertEquals(null, service.validateAndSnapshot(Map.of("avatar", "")).values().get("avatar"));
    }

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
    void rejectsUnknownButAllowsMissingRequiredFields() {
        MediaAccountFieldConfigRespVO.FieldVO nickname = field("nickname", "昵称", "text", null, true);
        when(mapper.selectPublished()).thenReturn(new MediaAccountFieldConfigDO().setId(8L)
                .setStatus("published").setFieldsJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils
                        .toJsonString(List.of(nickname))));

        assertThrows(RuntimeException.class, () -> service.validateAndSnapshot(Map.of("unknown", "value")));
        assertEquals(Map.of(), service.validateAndSnapshot(Map.of()).values());
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

    @Test
    void olderDraftCannotReplaceNewProfileConfiguration() {
        when(mapper.selectById(9L)).thenReturn(new MediaAccountFieldConfigDO().setId(9L).setVersionNo(2).setVersion(0).setStatus("draft"));
        when(mapper.selectPublished()).thenReturn(new MediaAccountFieldConfigDO().setId(10L).setVersionNo(3).setStatus("published"));
        assertThrows(RuntimeException.class, () -> service.publish(9L, 0));
        verify(mapper, org.mockito.Mockito.never()).publish(any(),any(),any());
    }

    @Test
    void invalidOwnerAndNonStringTextAreRejected() {
        var f=field("nickname","昵称","text",null,false);f.setOwnerType("ANYONE");
        when(mapper.selectPublished()).thenReturn(new MediaAccountFieldConfigDO().setId(8L).setFieldsJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(List.of(f))));
        assertThrows(RuntimeException.class, () -> service.validateAndSnapshot(Map.of()));
        f.setOwnerType("OPERATOR");
        when(mapper.selectPublished()).thenReturn(new MediaAccountFieldConfigDO().setId(8L).setFieldsJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(List.of(f))));
        assertThrows(RuntimeException.class, () -> service.validateAndSnapshot(Map.of("nickname",Map.of("nested","value"))));
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
