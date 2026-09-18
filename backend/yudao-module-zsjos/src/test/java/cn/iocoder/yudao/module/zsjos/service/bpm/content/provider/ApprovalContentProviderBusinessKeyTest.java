package cn.iocoder.yudao.module.zsjos.service.bpm.content.provider;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 各业务域 Provider 的 businessKey 解析。
 *
 * <p>这是最容易出错、也最难在运行时发现的一处：解析错了不会报错，
 * 只会静默展示到**另一条**业务单据上。因此每个域的实际 businessKey 格式都要有断言。
 */
class ApprovalContentProviderBusinessKeyTest {

    @Test
    void twoSegmentDomainsTakeTheId() {
        assertEquals("12", new WithdrawalContentProvider().parseBusinessId("withdrawal:12"));
        assertEquals("12", new ClassTransferContentProvider().parseBusinessId("class-transfer:12"));
        assertEquals("12", new LeadTransferContentProvider().parseBusinessId("lead-transfer:12"));
        assertEquals("12", new LeadAppealContentProvider().parseBusinessId("lead-appeal:12"));
        assertEquals("12", new MaterialContentProvider().parseBusinessId("material-version:12"));
        assertEquals("12", new ContentReviewContentProvider().parseBusinessId("content-review-batch:12"));
        assertEquals("12", new StudentDeliveryDeferContentProvider()
                .parseBusinessId("student-delivery-defer:12"));
        assertEquals("12", new StudentContactExtensionContentProvider()
                .parseBusinessId("student-contact-extension:12"));
    }

    @Test
    void mediaRebindTakesAccountIdNotVersion() {
        // media-rebind:{accountId}:v{version} —— 第三段是版本号，绝不能当 id。
        assertEquals("12", new MediaAccountRebindContentProvider().parseBusinessId("media-rebind:12:v3"));
        assertEquals("12", new MediaAccountRebindContentProvider().parseBusinessId("media-rebind:12:v1"));
    }

    @Test
    void feedbackTakesWorkOrderIdPlusRoundNotFeedbackId() {
        // feedback:{workOrderId}:round:{roundNo} —— 取的是 workOrderId，并带上轮次。
        // 带上轮次是必须的：同一工单多轮审批，只按 workOrderId 取数会让历史轮次的
        // 已办任务显示成最新一轮的内容，且不会报错。
        assertEquals("9:2", new FeedbackContentProvider().parseBusinessId("feedback:9:round:2"));
        assertEquals("9:1", new FeedbackContentProvider().parseBusinessId("feedback:9:round:1"));
        // 轮次段缺失或非数字时退化为只用 workOrderId，由 Provider 退回最新一轮。
        assertEquals("9", new FeedbackContentProvider().parseBusinessId("feedback:9"));
        assertEquals("9", new FeedbackContentProvider().parseBusinessId("feedback:9:round:abc"));
    }

    @Test
    void malformedKeysResolveToNull() {
        assertEquals(null, new WithdrawalContentProvider().parseBusinessId("withdrawal:abc"));
        assertEquals(null, new WithdrawalContentProvider().parseBusinessId("withdrawal:"));
        assertEquals(null, new WithdrawalContentProvider().parseBusinessId("other:12"));
        assertEquals(null, new MediaAccountRebindContentProvider().parseBusinessId("media-rebind:"));
        assertEquals(null, new FeedbackContentProvider().parseBusinessId("feedback:abc:round:1"));
    }

    @Test
    void providersAllDeclareDistinctPrefixes() {
        List<String> prefixes = List.of(
                new WithdrawalContentProvider().businessKeyPrefix(),
                new ClassTransferContentProvider().businessKeyPrefix(),
                new LeadTransferContentProvider().businessKeyPrefix(),
                new LeadAppealContentProvider().businessKeyPrefix(),
                new SalesOrderContentProvider().businessKeyPrefix(),
                new MaterialContentProvider().businessKeyPrefix(),
                new ContentReviewContentProvider().businessKeyPrefix(),
                new StudentDeliveryDeferContentProvider().businessKeyPrefix(),
                new StudentContactExtensionContentProvider().businessKeyPrefix(),
                new MediaAccountRebindContentProvider().businessKeyPrefix(),
                new FeedbackContentProvider().businessKeyPrefix());
        assertEquals(prefixes.size(), prefixes.stream().distinct().count(),
                "Provider 前缀必须全局唯一，否则注册表会拒绝启动");
        prefixes.forEach(prefix -> org.junit.jupiter.api.Assertions.assertTrue(
                prefix.endsWith(":"), prefix + " 必须以冒号结尾"));
    }
}
