package cn.iocoder.yudao.module.zsjos.service.bpm.content;

import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalDetailVO;
import cn.iocoder.yudao.module.bpm.api.approvalcontent.vo.BpmApprovalFieldVO;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 附件组装：签名成功用签名地址，失败退回历史快照，都没有则整条附件丢弃。
 */
@ExtendWith(MockitoExtension.class)
class ZsjosApprovalAttachmentSupportTest {

    @Mock private FileApi fileApi;
    @InjectMocks private ZsjosApprovalAttachmentSupport support;

    @Test
    void signedUrlWins() {
        when(fileApi.presignGetUrl(1L, 600)).thenReturn("https://signed/1");

        assertEquals("https://signed/1", support.resolveUrl(1L, "https://snapshot/1"));
    }

    @Test
    void fallsBackToSnapshotWhenSigningFails() {
        // 对象存储不可达时，历史快照地址比"附件凭空消失"更好。
        when(fileApi.presignGetUrl(1L, 600)).thenThrow(new IllegalStateException("storage down"));

        assertEquals("https://snapshot/1", support.resolveUrl(1L, "https://snapshot/1"));
    }

    @Test
    void nullFileIdUsesSnapshotDirectly() {
        assertEquals("https://snapshot/1", support.resolveUrl(null, "https://snapshot/1"));
        assertNull(support.resolveUrl(null, null));
    }

    @Test
    void addGroupSkipsEmptyAttachments() {
        BpmApprovalDetailVO card = new BpmApprovalDetailVO();

        support.addGroup(card, "空附件", List.of(), true);

        assertTrue(card.getGroups().isEmpty(), "没有附件时不应留下空分组");
    }

    @Test
    void addGroupIsSpanWhenRequested() {
        BpmApprovalDetailVO card = new BpmApprovalDetailVO();

        support.addGroup(card, "素材文件",
                List.of(BpmApprovalFieldVO.attachment("a.png", "https://x/a.png", "image/png", 10L)), true);

        assertEquals(1, card.getGroups().size());
        assertEquals(Boolean.TRUE, card.getGroups().get(0).getSpan());
        assertEquals(1, card.getGroups().get(0).getFields().get(0).getAttachments().size());
    }

    @Test
    void batchResolveToleratesFailure() {
        when(fileApi.presignGetUrls(any(), eq(600))).thenThrow(new IllegalStateException("storage down"));

        assertTrue(support.resolveUrls(List.of(1L, 2L)).isEmpty());
    }

    @Test
    void imageDetectionUsesMimeType() {
        assertTrue(BpmApprovalFieldVO.isImage(
                BpmApprovalFieldVO.attachment("a.png", "u", "image/png", 1L)));
        // 大小写与带参数的类型也要认出来。
        assertTrue(BpmApprovalFieldVO.isImage(
                BpmApprovalFieldVO.attachment("a.jpg", "u", "IMAGE/JPEG", 1L)));
        assertTrue(!BpmApprovalFieldVO.isImage(
                BpmApprovalFieldVO.attachment("a.pdf", "u", "application/pdf", 1L)));
        // 缺 contentType 时不做图片预览，宁可给下载链接。
        assertTrue(!BpmApprovalFieldVO.isImage(BpmApprovalFieldVO.attachment("a", "u", null, 1L)));
        assertTrue(!BpmApprovalFieldVO.isImage(null));
    }

    @Test
    void attachmentsFactoryReturnsNullForEmptyInput() {
        assertNull(BpmApprovalFieldVO.attachments("素材", List.of()));
        assertNull(BpmApprovalFieldVO.attachments("素材", null));
    }
}
