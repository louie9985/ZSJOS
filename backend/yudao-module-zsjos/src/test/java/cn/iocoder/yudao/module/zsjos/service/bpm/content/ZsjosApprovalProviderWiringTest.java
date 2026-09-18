package cn.iocoder.yudao.module.zsjos.service.bpm.content;

import cn.iocoder.yudao.module.zsjos.service.bpm.content.provider.ClassTransferContentProvider;
import cn.iocoder.yudao.module.zsjos.service.bpm.content.provider.ContentReviewContentProvider;
import cn.iocoder.yudao.module.zsjos.service.bpm.content.provider.FeedbackContentProvider;
import cn.iocoder.yudao.module.zsjos.service.bpm.content.provider.LeadAppealContentProvider;
import cn.iocoder.yudao.module.zsjos.service.bpm.content.provider.LeadTransferContentProvider;
import cn.iocoder.yudao.module.zsjos.service.bpm.content.provider.MaterialContentProvider;
import cn.iocoder.yudao.module.zsjos.service.bpm.content.provider.MediaAccountRebindContentProvider;
import cn.iocoder.yudao.module.zsjos.service.bpm.content.provider.SalesOrderContentProvider;
import cn.iocoder.yudao.module.zsjos.service.bpm.content.provider.StudentContactExtensionContentProvider;
import cn.iocoder.yudao.module.zsjos.service.bpm.content.provider.StudentDeliveryDeferContentProvider;
import cn.iocoder.yudao.module.zsjos.service.bpm.content.provider.WithdrawalContentProvider;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalContentProvider;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalContentRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 启动自检：用**全部已接入的业务域**构造注册表。
 *
 * <p>注册表在前缀重复时会抛异常让容器起不来，而真实前缀分散在 11 个 Provider 里，
 * 靠人工核对极易漏。这个测试把"上线后才发现起不来"提前到编译期。
 *
 * <p>新增业务域时，务必将新 Provider 加进 {@link #allProviders()}，
 * 否则本测试覆盖不到。
 */
class ZsjosApprovalProviderWiringTest {

    /** 与 Spring 注入的 provider 列表一一对应；新增业务域时同步补充。 */
    private static List<BpmApprovalContentProvider> allProviders() {
        return List.of(
                new WithdrawalContentProvider(),
                new LeadAppealContentProvider(),
                new SalesOrderContentProvider(),
                new MaterialContentProvider(),
                new ContentReviewContentProvider(),
                new ClassTransferContentProvider(),
                new LeadTransferContentProvider(),
                new StudentDeliveryDeferContentProvider(),
                new StudentContactExtensionContentProvider(),
                new MediaAccountRebindContentProvider(),
                new FeedbackContentProvider());
    }

    @Test
    void registryAcceptsAllRealProvidersWithoutPrefixCollision() {
        // 构造函数在前缀重复时抛 IllegalStateException。
        BpmApprovalContentRegistry registry = new BpmApprovalContentRegistry(allProviders());
        assertEquals(allProviders().size(), registry.size());
    }

    @Test
    void everyDomainRoutesToItsOwnProvider() {
        BpmApprovalContentRegistry registry = new BpmApprovalContentRegistry(allProviders());

        assertEquals("withdrawal", resolve(registry, "withdrawal:1"));
        assertEquals("lead_appeal", resolve(registry, "lead-appeal:1"));
        assertEquals("sales_order", resolve(registry, "sales-order:1"));
        assertEquals("material", resolve(registry, "material-version:1"));
        assertEquals("content_review", resolve(registry, "content-review-batch:1"));
        assertEquals("class_transfer", resolve(registry, "class-transfer:1"));
        assertEquals("lead_transfer", resolve(registry, "lead-transfer:1"));
        assertEquals("student_delivery_defer", resolve(registry, "student-delivery-defer:1"));
        assertEquals("student_contact_extension", resolve(registry, "student-contact-extension:1"));
        assertEquals("media_rebind", resolve(registry, "media-rebind:1:v2"));
        assertEquals("feedback", resolve(registry, "feedback:1:round:1"));
    }

    @Test
    void twoDomainsSharingAProcessKeyStillSplitByPrefix() {
        // zsjos_student_contact_extension 被交付延期与联系延期共用；
        // businessKey 前缀是唯一区分手段，必须落到各自的 Provider。
        BpmApprovalContentRegistry registry = new BpmApprovalContentRegistry(allProviders());

        assertEquals("student_delivery_defer", resolve(registry, "student-delivery-defer:7"));
        assertEquals("student_contact_extension", resolve(registry, "student-contact-extension:7"));
    }

    @Test
    void unregisteredBusinessKeysFallBackToGenericDisplay() {
        BpmApprovalContentRegistry registry = new BpmApprovalContentRegistry(allProviders());

        // 定位卡/IP 流程当前没有发起方，未接入；应解析为空而不是误配到别的域。
        assertTrue(registry.resolve("media-positioning-ip:1").isEmpty());
        assertTrue(registry.resolve("unknown:1").isEmpty());
    }

    private static String resolve(BpmApprovalContentRegistry registry, String businessKey) {
        return registry.resolve(businessKey)
                .orElseThrow(() -> new AssertionError("未解析到 Provider: " + businessKey))
                .bizType();
    }
}
