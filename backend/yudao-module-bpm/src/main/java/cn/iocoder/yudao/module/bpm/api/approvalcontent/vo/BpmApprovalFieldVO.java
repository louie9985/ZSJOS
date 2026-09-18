package cn.iocoder.yudao.module.bpm.api.approvalcontent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 审批内容展示字段：一个已中文化的"标签 + 值"。
 *
 * <p>各业务域的下游只认识这一层抽象——具体展示哪些字段、如何把状态码翻译成中文，
 * 全部由各业务域的 {@link cn.iocoder.yudao.module.bpm.api.approvalcontent.BpmApprovalContentProvider}
 * 决定。前端不做任何业务语义映射，因此新增流程不需要改前端。
 */
@Schema(description = "管理后台 - 审批内容字段")
@Data
public class BpmApprovalFieldVO {

    @Schema(description = "字段展示名（已中文化）", requiredMode = Schema.RequiredMode.REQUIRED, example = "申请金额")
    private String label;

    @Schema(description = "字段值（已格式化）", example = "1280.00")
    private String value;

    @Schema(description = "栅格跨列数，1 或 2（长文本用 2）", example = "1")
    private Integer span;

    /**
     * 附件列表。非空时前端渲染为可点击的预览/下载项，忽略 {@link #value}。
     *
     * <p>附件常常就是审批对象本身（素材、内容审核、反馈），所以它不是装饰性字段。
     */
    @Schema(description = "附件列表")
    private List<Attachment> attachments;

    @Schema(description = "审批内容附件")
    @Data
    public static class Attachment {

        @Schema(description = "文件名", example = "合同扫描件.pdf")
        private String name;

        /**
         * 可读地址。后端下发前应换成分级授权的预签名地址，不要直接把永久地址暴露出去。
         */
        @Schema(description = "访问地址")
        private String url;

        @Schema(description = "MIME 类型，用于前端决定用图片预览还是下载", example = "image/png")
        private String contentType;

        @Schema(description = "文件大小（字节）")
        private Long size;
    }

    public static BpmApprovalFieldVO of(String label, Object value) {
        BpmApprovalFieldVO field = new BpmApprovalFieldVO();
        field.setLabel(label);
        field.setValue(value == null ? null : String.valueOf(value));
        return field;
    }

    public static BpmApprovalFieldVO wide(String label, Object value) {
        BpmApprovalFieldVO field = of(label, value);
        field.setSpan(2);
        return field;
    }

    /**
     * 附件字段。整行展示，因为缩略图/文件名列表通常放不下半行。
     *
     * @param attachments 已解析出可读地址的附件；为空时返回 null，调用方应跳过该字段
     */
    public static BpmApprovalFieldVO attachments(String label, List<Attachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        BpmApprovalFieldVO field = new BpmApprovalFieldVO();
        field.setLabel(label);
        field.setSpan(2);
        field.setAttachments(new ArrayList<>(attachments));
        return field;
    }

    public static Attachment attachment(String name, String url, String contentType, Long size) {
        Attachment attachment = new Attachment();
        attachment.setName(name);
        attachment.setUrl(url);
        attachment.setContentType(contentType);
        attachment.setSize(size);
        return attachment;
    }

    /**
     * 是否可用图片方式内联预览。仅凭 MIME 判断，前端仍应处理加载失败。
     */
    public static boolean isImage(Attachment attachment) {
        return attachment != null && attachment.getContentType() != null
                && attachment.getContentType().toLowerCase().startsWith("image/");
    }
}

