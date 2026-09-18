package cn.iocoder.yudao.module.zsjos.service.bpm.content;

import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalDetailVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalFieldVO;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileInfoRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 审批详情卡的附件组装工具。
 *
 * <p>放在 zsjos 而不是 bpm 契约里：附件签名依赖 infra 的 {@link FileApi}，
 * 而 bpm 模块刻意不依赖 infra（契约层保持轻量）。各业务域本就在自己的模块里，
 * 引用这里即可；非 zsjos 的域（如 EAM）若也需要，自建等价小工具比给契约加依赖更划算。
 *
 * <p><b>为什么不把永久地址直接下发</b>：对象存储的地址一旦泄露，文件就长期可读。
 * 这里统一换成分级授权的预签名地址，有效期与业务侧的预览保持一致。
 */
@Component
public class ZsjosApprovalAttachmentSupport {

    /** 与各业务侧的预览有效期一致（10 分钟）。 */
    private static final int PREVIEW_SECONDS = 600;

    @Resource
    private FileApi fileApi;

    /**
     * 按 infra 文件编号解析可读地址。
     *
     * @param fileId   文件编号；为空时退回 fallback
     * @param fallback 业务侧存的历史地址快照；签名失败时用它兜底
     * @return 可读地址；都没有时返回 null（调用方应跳过该附件）
     */
    public String resolveUrl(Long fileId, String fallback) {
        if (fileId == null) {
            return fallback;
        }
        try {
            return fileApi.presignGetUrl(fileId, PREVIEW_SECONDS);
        } catch (RuntimeException ignored) {
            // 对象存储不可达或文件已被清理：退回历史快照地址，避免整个附件消失。
            return fallback;
        }
    }

    /**
     * 批量解析，key 为 infra 文件编号。列表页一次要签多个文件时用它，避免逐个往返。
     */
    public java.util.Map<Long, String> resolveUrls(java.util.Collection<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return java.util.Map.of();
        }
        List<Long> ids = fileIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return java.util.Map.of();
        }
        try {
            return fileApi.presignGetUrls(ids, PREVIEW_SECONDS);
        } catch (RuntimeException ignored) {
            return java.util.Map.of();
        }
    }

    /**
     * 按 infra 文件编号组装一个带元数据的审批附件。
     *
     * <p>业务侧只存了文件编号（处理结果附件就是这种形态），名字、MIME、大小都在 infra。
     * 拿不到文件信息时仍返回带 URL 的附件——能打开比显示全名重要。
     *
     * @return 附件；文件不存在且签名也失败时返回 null，调用方应跳过
     */
    public BpmApprovalFieldVO.Attachment resolveAttachment(Long fileId) {
        String url = resolveUrl(fileId, null);
        if (url == null) {
            return null;
        }
        String name = null;
        String contentType = null;
        Long size = null;
        try {
            FileInfoRespDTO file = fileApi.getFileInfo(fileId);            if (file != null) {
                name = file.getName();
                contentType = file.getType();
                size = file.getSize();
            }
        } catch (RuntimeException ignored) {
            // 文件记录不可读：仅影响展示名与预览方式，不影响能否打开。
        }
        return BpmApprovalFieldVO.attachment(name, url, contentType, size);
    }

    /**
     * 往详情卡追加一个附件分组。附件为空时什么都不做（不留空分组）。
     *
     * @param span 是否整行占满；附件网格通常需要整行
     */
    public void addGroup(BpmApprovalDetailVO card, String title,
                         List<BpmApprovalFieldVO.Attachment> attachments, boolean span) {
        BpmApprovalFieldVO field = BpmApprovalFieldVO.attachments(title, attachments);
        if (field == null) {
            return;
        }
        BpmApprovalDetailVO.Group group = new BpmApprovalDetailVO.Group();
        group.setTitle(title);
        group.setFields(new ArrayList<>(List.of(field)));
        group.setSpan(span);
        card.getGroups().add(group);
    }
}
