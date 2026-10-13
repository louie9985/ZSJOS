package cn.iocoder.yudao.module.zsjos.service.contentreview;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ContentReviewAccountProfileTest {
    @Test void usesStoredLabelsAndCorrectProductFormRatherThanPositioningContentFormat() {
        var account = new MediaAccountDO().setDetailValuesJson(
                "{\"product_goal\":\"产品目标\",\"work_format\":\"产品形式\",\"content_format\":\"内容形式\",\"publish_rhythm\":\"weekly\"}")
                .setDetailSnapshotJson("[{\"key\":\"publish_rhythm\",\"displayValue\":\"每周三条（原标签）\"}]")
                .setPrimaryProblemCodeLabelSnapshot("诊断瓶颈");
        assertEquals(Map.of("productGoal","产品目标","productFormLabel","产品形式",
                "publishFrequency","每周三条（原标签）","bottleneckLabel","诊断瓶颈"),ContentReviewAccountProfile.read(account));
    }

    @Test void newSubmissionClearsStaleFieldsAndPreservesIdentityAndOtherContext() {
        var snapshot = new LinkedHashMap<String,Object>(Map.of("id",3L,"nickname","审批名称",
                "productGoal","旧目标","publishFrequency","旧节奏","bottleneckLabel","旧瓶颈",
                "primaryProblems",java.util.List.of("旧维护问题"),"profileBackfill",Map.of("source","CURRENT_ACCOUNT")));
        var account=new MediaAccountDO().setDetailValuesJson("{\"product_goal\":\"新目标\",\"publish_rhythm\":\"weekly\"}");
        ContentReviewAccountProfile.refresh(snapshot,account);
        assertEquals(Map.of("id",3L,"nickname","审批名称","productGoal","新目标"),snapshot);
    }

    @Test void nullJsonAndEmptySnapshotsDoNotInventLabels() {
        assertTrue(ContentReviewAccountProfile.read(new MediaAccountDO()).isEmpty());
        assertTrue(ContentReviewAccountProfile.read(new MediaAccountDO().setDetailValuesJson("null")
                .setDetailSnapshotJson("null")).isEmpty());
    }
}
