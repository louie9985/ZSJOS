package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.module.zsjos.controller.admin.material.vo.MaterialSaveReqVO;
import cn.iocoder.yudao.module.zsjos.service.material.MaterialSchemaService;
import cn.iocoder.yudao.module.zsjos.service.material.MaterialService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.DICT_ACCOUNT_STAGE;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.DICT_PERSONA_TYPE;
import static cn.iocoder.yudao.module.zsjos.enums.MaterialConstants.DICT_PROFESSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContentReviewMaterialServiceTest {

    @InjectMocks private ContentReviewMaterialService service;
    @Mock private MaterialService materialService;

    @Test
    void mapsFrozenAccountDictionarySnapshotsToTheirTargetFields() {
        Map<String, Object> content = Map.of("contentNo", "CT-1", "title", "标题");
        Map<String, Object> account = Map.of(
                "accountTypePrimaryValue", "TYPE-1", "accountTypePrimaryLabel", "账号类型旧标签",
                "trackPrimaryValue", "PRO-1", "trackPrimaryLabel", "专业旧标签",
                "sStage", "S1", "sStageLabel", "阶段旧标签");
        Map<String, String> mapping = Map.of(
                "account_types", "accountType",
                "professions", "profession",
                "account_stages", "accountStage");

        service.buildAndValidate("production_content", 10L, "hash", mapping, Map.of(),
                content, account, Set.of(), 7L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, MaterialSchemaService.DictionarySnapshotValue>> snapshots =
                ArgumentCaptor.forClass(Map.class);
        verify(materialService).prepareAutoCollection(eq("production_content"), eq(10L), eq("hash"),
                any(MaterialSaveReqVO.class), eq(Set.of()), eq(7L), snapshots.capture());
        assertEquals(new MaterialSchemaService.DictionarySnapshotValue(
                DICT_PERSONA_TYPE, "TYPE-1", "账号类型旧标签"), snapshots.getValue().get("account_types"));
        assertEquals(new MaterialSchemaService.DictionarySnapshotValue(
                DICT_PROFESSION, "PRO-1", "专业旧标签"), snapshots.getValue().get("professions"));
        assertEquals(new MaterialSchemaService.DictionarySnapshotValue(
                DICT_ACCOUNT_STAGE, "S1", "阶段旧标签"), snapshots.getValue().get("account_stages"));
    }
}
