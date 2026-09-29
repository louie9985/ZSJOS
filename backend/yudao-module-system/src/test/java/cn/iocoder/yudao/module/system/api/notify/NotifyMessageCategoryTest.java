package cn.iocoder.yudao.module.system.api.notify;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NotifyMessageCategory} 的单元测试。
 *
 * <p>重点验证分类互斥与「一条消息只落在一个分类」，这两点是本次把 SQL 过滤与 Java 判定
 * 合并到同一份目录定义后必须成立的不变量。
 */
public class NotifyMessageCategoryTest {

    @Test
    public void testResolve_byBizType() {
        assertEquals(NotifyMessageCategory.LEAD,
                NotifyMessageCategory.resolve("lead", null, null));
        assertEquals(NotifyMessageCategory.WITHDRAWAL,
                NotifyMessageCategory.resolve("withdrawal", null, null));
        assertEquals(NotifyMessageCategory.SYSTEM,
                NotifyMessageCategory.resolve("hrm_employee", null, null));
        assertEquals(NotifyMessageCategory.SYSTEM,
                NotifyMessageCategory.resolve(null, null, null));
    }

    /**
     * 客资申诉同时带 lead 的 bizType 与 appeal 的 sceneCode。互斥要求它只归入申诉，
     * 否则会在「客资」和「申诉」两个 tab 重复出现。
     */
    @Test
    public void testResolve_appealSceneOnLeadOutranksLead() {
        assertEquals(NotifyMessageCategory.APPEAL,
                NotifyMessageCategory.resolve("lead", "zsjos.lead.appeal_submitted", null));
        assertEquals(NotifyMessageCategory.APPEAL,
                NotifyMessageCategory.resolve("lead", "zsjos.lead.complaint_founded", null));
    }

    @Test
    public void testResolve_bySceneAndEventKey() {
        assertEquals(NotifyMessageCategory.LEAD,
                NotifyMessageCategory.resolve(null, "zsjos.registration.task_created", null));
        assertEquals(NotifyMessageCategory.WITHDRAWAL,
                NotifyMessageCategory.resolve(null, null, "withdrawal-submitted:12"));
        assertEquals(NotifyMessageCategory.APPEAL,
                NotifyMessageCategory.resolve(null, null, "lead-complaint-founded:7"));
    }

    @Test
    public void testCondition_allAndUnknownApplyNoFilter() {
        assertEquals("1=1", NotifyMessageCategory.condition(null));
        assertEquals("1=1", NotifyMessageCategory.condition(""));
        assertEquals("1=1", NotifyMessageCategory.condition("  "));
        assertEquals("1=1", NotifyMessageCategory.condition(NotifyMessageCategory.ALL));
        // 拼写错误按「忽略该筛选条件」处理，不静默缩成某个分类
        assertEquals("1=1", NotifyMessageCategory.condition("nope"));
    }

    @Test
    public void testCondition_matchesCatalogBizTypes() {
        assertTrue(NotifyMessageCategory.condition(NotifyMessageCategory.WITHDRAWAL)
                .contains("biz_type IN ('withdrawal')"));
        assertTrue(NotifyMessageCategory.condition(NotifyMessageCategory.LEAD)
                .contains("'production-ticket'"));
    }

    /** 高优先级分类必须被排除，SQL 与 resolve 的判定顺序才一致。 */
    @Test
    public void testCondition_excludesHigherPriorityCategories() {
        // 申诉是最高优先级，无需排除任何分类
        assertFalse(NotifyMessageCategory.condition(NotifyMessageCategory.APPEAL).contains("NOT ("));

        // 提现需排除申诉
        String withdrawal = NotifyMessageCategory.condition(NotifyMessageCategory.WITHDRAWAL);
        assertTrue(withdrawal.contains("NOT ("));
        assertTrue(withdrawal.contains("appeal"));
        assertFalse(withdrawal.contains("'lead'"));

        // 客资优先级最低，需排除申诉与提现
        String lead = NotifyMessageCategory.condition(NotifyMessageCategory.LEAD);
        assertTrue(lead.contains("appeal"));
        assertTrue(lead.contains("withdrawal"));
    }

    /** 空值取反会得到 NULL，必须用 COALESCE 兜住，否则历史遗留的空消息会被排除。 */
    @Test
    public void testCondition_wrapsNegationInCoalesce() {
        for (String category : new String[]{NotifyMessageCategory.WITHDRAWAL, NotifyMessageCategory.LEAD,
                NotifyMessageCategory.SYSTEM}) {
            assertTrue(NotifyMessageCategory.condition(category).contains("COALESCE("),
                    category + " 的否定分支必须使用 COALESCE");
        }
    }

    @Test
    public void testCondition_systemExcludesEveryNamedCategory() {
        String system = NotifyMessageCategory.condition(NotifyMessageCategory.SYSTEM);
        assertTrue(system.startsWith("NOT ("));
        assertTrue(system.contains("'lead'"));
        assertTrue(system.contains("'withdrawal'"));
        assertTrue(system.contains("appeal"));
    }

    @Test
    public void testLabels_orderAndNoRewardCategory() {
        assertEquals(java.util.List.of(NotifyMessageCategory.ALL, NotifyMessageCategory.APPEAL,
                        NotifyMessageCategory.WITHDRAWAL, NotifyMessageCategory.LEAD, NotifyMessageCategory.SYSTEM),
                java.util.List.copyOf(NotifyMessageCategory.labels().keySet()));
        // 收益分类没有任何通知场景生产者，本次已移除
        assertFalse(NotifyMessageCategory.labels().containsKey("reward"));
        assertEquals("全部", NotifyMessageCategory.labels().get(NotifyMessageCategory.ALL));
        assertEquals("系统", NotifyMessageCategory.labels().get(NotifyMessageCategory.SYSTEM));
    }
}
