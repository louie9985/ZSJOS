package cn.iocoder.yudao.module.bpm.api.approvalcontent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 审批内容 Provider 注册表：按 businessKey 前缀把任务分发给对应的业务域。
 *
 * <p>Spring 会把所有模块里的 {@link BpmApprovalContentProvider} 实现注入进来
 * （zsjos、eam 等各自的 Provider 都在自己的模块里）。
 *
 * <p>匹配规则是<b>最长前缀优先</b>。这不是实现细节，而是正确性要求：
 * <ul>
 *   <li>{@code student-delivery-defer:} 与 {@code student-contact-extension:} 共用同一个
 *       流程定义 key {@code zsjos_student_contact_extension}，只能靠前缀区分；</li>
 *   <li>{@code media-rebind:12:v3} 是三段式，前缀匹配必须整体比中而非截断。</li>
 * </ul>
 *
 * <p>前缀重复会让应用**启动失败**而不是运行期选错——这类错误一旦漏到线上，
 * 表现是"审批中心显示了另一条单子的内容"，很难排查，所以宁可在启动时就拦下来。
 */
@Slf4j
@Component
public class BpmApprovalContentRegistry {

    private final List<BpmApprovalContentProvider> providers;

    public BpmApprovalContentRegistry(List<BpmApprovalContentProvider> providerList) {
        // 前缀长的排前面，保证最长前缀优先命中。
        this.providers = providerList.stream()
                .sorted(Comparator.comparingInt((BpmApprovalContentProvider p) -> p.businessKeyPrefix().length())
                        .reversed())
                .toList();
        long distinctPrefixes = this.providers.stream()
                .map(BpmApprovalContentProvider::businessKeyPrefix).distinct().count();
        if (distinctPrefixes != this.providers.size()) {
            throw new IllegalStateException("Duplicate approval content provider prefix");
        }
        log.info("[BpmApprovalContentRegistry][已接入 {} 个审批内容业务域：{}]", this.providers.size(),
                this.providers.stream().map(BpmApprovalContentProvider::bizType).toList());
    }

    /**
     * 找到能认领该 businessKey 的 Provider。
     *
     * @param businessKey 流程实例的业务标识；为 null 或空白时返回空
     */
    public Optional<BpmApprovalContentProvider> resolve(String businessKey) {
        if (businessKey == null || businessKey.isBlank()) {
            return Optional.empty();
        }
        return providers.stream()
                .filter(provider -> businessKey.startsWith(provider.businessKeyPrefix()))
                .findFirst();
    }

    /**
     * 已接入的业务域数量，用于启动自检与测试断言。
     */
    public int size() {
        return providers.size();
    }
}
