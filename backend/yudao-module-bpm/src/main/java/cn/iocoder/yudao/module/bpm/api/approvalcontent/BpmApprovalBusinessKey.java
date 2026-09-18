package cn.iocoder.yudao.module.bpm.api.approvalcontent;

/**
 * 审批内容的 businessKey 解析工具。
 *
 * <p>各业务域的 businessKey 分段数并不一致，且这个不一致是既成事实、短期内不会统一：
 * <ul>
 *   <li>两段：{@code withdrawal:12}、{@code class-transfer:12}</li>
 *   <li>三段：{@code media-rebind:12:v3}（第三段是版本号，不是 id）</li>
 *   <li>四段：{@code feedback:9:round:2}（第二段是 workOrderId，不是 feedbackId）</li>
 * </ul>
 *
 * <p>因此<b>不要</b>在 Provider 里写 {@code split(":")[1]} 这种取巧写法：
 * 上面两个例子里那样写都会取到错的字段，而且不会报错——只会静默展示到另一条业务单据上。
 * 本工具只提供"按前缀剥离 + 按位置取段"，分段语义仍由各 Provider 自己负责。
 */
public final class BpmApprovalBusinessKey {

    private BpmApprovalBusinessKey() {
    }

    /**
     * 剥离前缀后的剩余部分；前缀不匹配时返回 null。
     * 例：{@code strip("media-rebind:", "media-rebind:12:v3")} → {@code "12:v3"}
     */
    public static String strip(String prefix, String businessKey) {
        if (businessKey == null || !businessKey.startsWith(prefix)) {
            return null;
        }
        String rest = businessKey.substring(prefix.length());
        return rest.isBlank() ? null : rest;
    }

    /**
     * 取冒号分隔的第 index 段（0 起）；越界或空白返回 null。
     *
     * <p>用 {@code split(":", -1)} 保留空段，避免尾部空串被吞掉导致索引错位。
     */
    public static String segment(String value, int index) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split(":", -1);
        if (index < 0 || index >= parts.length) {
            return null;
        }
        String part = parts[index];
        return part.isBlank() ? null : part;
    }

    /**
     * 取第 0 段并校验为数字；非数字返回 null。多数两段式域的 parseBusinessId 直接用它。
     */
    public static String idSegment(String value) {
        return idSegment(value, 0);
    }

    /**
     * 取第 {@code index} 段并校验为数字；非数字返回 null。
     *
     * <p>给 id 不在首段的域用——例如 EAM 资产转移的
     * {@code asset-transfer:12:round:1}，id 在第 2 段。
     */
    public static String idSegment(String value, int index) {
        String part = segment(value, index);
        if (part == null) {
            return null;
        }
        try {
            Long.parseLong(part);
            return part;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
