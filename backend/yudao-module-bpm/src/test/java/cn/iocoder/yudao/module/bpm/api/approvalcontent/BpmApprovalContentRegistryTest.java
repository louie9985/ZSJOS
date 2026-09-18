package cn.iocoder.yudao.module.bpm.api.approvalcontent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BpmApprovalContentRegistryTest {

    @Test
    void longestPrefixWins() {
        // student-delivery-defer: 与 student-contact-extension: 共用流程 key，
        // 只能靠前缀区分；两者不是互相的前缀，但交付延期必须以自身前缀命中。
        BpmApprovalContentRegistry registry = new BpmApprovalContentRegistry(List.of(
                stub("student-contact-extension:"),
                stub("student-delivery-defer:"),
                stub("withdrawal:")));

        assertEquals("student-delivery-defer:", registry.resolve("student-delivery-defer:7").orElseThrow()
                .businessKeyPrefix());
        assertEquals("student-contact-extension:", registry.resolve("student-contact-extension:7").orElseThrow()
                .businessKeyPrefix());
        assertEquals("withdrawal:", registry.resolve("withdrawal:9").orElseThrow().businessKeyPrefix());
    }

    @Test
    void nestedPrefixPrefersTheLongerOne() {
        // "media:" 是 "media-rebind:" 的前缀，因此 media-rebind:x:v1 必须命中更长的那个。
        BpmApprovalContentRegistry registry = new BpmApprovalContentRegistry(List.of(
                stub("media:"), stub("media-rebind:")));

        assertEquals("media-rebind:", registry.resolve("media-rebind:3:v1").orElseThrow().businessKeyPrefix());
        assertEquals("media:", registry.resolve("media:3").orElseThrow().businessKeyPrefix());
    }

    @Test
    void shortPrefixDoesNotSwallowDifferentDomain() {
        BpmApprovalContentRegistry registry = new BpmApprovalContentRegistry(List.of(
                stub("lead:"), stub("lead-appeal:")));

        assertEquals("lead-appeal:", registry.resolve("lead-appeal:3").orElseThrow().businessKeyPrefix());
        // "lead-transfer:" 不是 "lead:" 的前缀延伸，这里没有注册它，因此不命中。
        assertTrue(registry.resolve("lead-transfer:3").isEmpty());
    }

    @Test
    void siblingDomainPrefixesAreDistinct() {
        // 两个延期域共用同一个流程定义 key，只能靠前缀区分；两者互不为前缀。
        BpmApprovalContentRegistry registry = new BpmApprovalContentRegistry(List.of(
                stub("student-contact-extension:"), stub("student-delivery-defer:"),
                stub("lead-appeal:"), stub("lead-transfer:")));

        assertEquals("lead-appeal:", registry.resolve("lead-appeal:3").orElseThrow().businessKeyPrefix());
        assertEquals("lead-transfer:", registry.resolve("lead-transfer:3").orElseThrow().businessKeyPrefix());
        assertEquals("student-contact-extension:",
                registry.resolve("student-contact-extension:3").orElseThrow().businessKeyPrefix());
        assertEquals("student-delivery-defer:",
                registry.resolve("student-delivery-defer:3").orElseThrow().businessKeyPrefix());
    }

    @Test
    void unknownAndBlankBusinessKeysResolveToEmpty() {
        BpmApprovalContentRegistry registry = new BpmApprovalContentRegistry(List.of(stub("withdrawal:")));

        assertTrue(registry.resolve("something-else:1").isEmpty());
        assertTrue(registry.resolve(null).isEmpty());
        assertTrue(registry.resolve("  ").isEmpty());
    }

    @Test
    void duplicatePrefixesFailFast() {
        assertThrows(IllegalStateException.class,
                () -> new BpmApprovalContentRegistry(List.of(stub("withdrawal:"), stub("withdrawal:"))));
    }

    private static BpmApprovalContentProvider stub(String prefix) {
        return new StubProvider(prefix);
    }

    /**
     * 只关心前缀匹配的桩实现；brief/detail 不参与注册表测试。
     */
    private static final class StubProvider implements BpmApprovalContentProvider {
        private final String prefix;

        private StubProvider(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public String bizType() {
            return prefix;
        }

        @Override
        public String businessKeyPrefix() {
            return prefix;
        }

        @Override
        public String parseBusinessId(String businessKey) {
            return businessKey.startsWith(prefix) ? businessKey.substring(prefix.length()) : null;
        }

        @Override
        public cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalBriefVO brief(
                String businessId, Long viewerId) {
            return null;
        }

        @Override
        public cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalDetailVO detail(
                String businessId, Long viewerId) {
            return null;
        }
    }
}
