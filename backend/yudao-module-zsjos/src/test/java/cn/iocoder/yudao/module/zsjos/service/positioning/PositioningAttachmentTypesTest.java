package cn.iocoder.yudao.module.zsjos.service.positioning;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 定位访谈稿与定位卡附件允许文档、图片、音频、视频；这里锁定扩展名与内容不一致时不被放行。
 */
class PositioningAttachmentTypesTest {

    private static final byte[] PNG = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @Test
    void acceptsDocumentImageAudioAndVideo() {
        assertNotNull(PositioningAttachmentTypes.detectAllowed("访谈稿.pdf", "%PDF-1.7\n".getBytes(StandardCharsets.UTF_8)));
        assertNotNull(PositioningAttachmentTypes.detectAllowed("主页封面.png", PNG));
        assertNotNull(PositioningAttachmentTypes.detectAllowed("口播.mp3", id3()));
        assertNotNull(PositioningAttachmentTypes.detectAllowed("成片.mp4", mp4()));
    }

    @Test
    void acceptsPlainTextFormatsThatHaveNoMagicNumber() {
        assertNotNull(PositioningAttachmentTypes.detectAllowed("记录.txt", "访谈要点".getBytes(StandardCharsets.UTF_8)));
        assertNotNull(PositioningAttachmentTypes.detectAllowed("notes.md", "# 标题\n".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsUnknownExtensionAndEmptyPayload() {
        assertNull(PositioningAttachmentTypes.detectAllowed("脚本.sh", "#!/bin/sh".getBytes(StandardCharsets.UTF_8)));
        assertNull(PositioningAttachmentTypes.detectAllowed("无扩展名", PNG));
        assertNull(PositioningAttachmentTypes.detectAllowed("空文件.png", new byte[0]));
        assertNull(PositioningAttachmentTypes.detectAllowed(null, PNG));
    }

    @Test
    void rejectsContentThatDoesNotMatchTheClaimedExtension() {
        // 伪装成图片的可执行内容不得入库，否则会被原样回放。
        assertNull(PositioningAttachmentTypes.detectAllowed("伪装.png", "#!/bin/sh\nrm -rf /".getBytes(StandardCharsets.UTF_8)));
        assertNull(PositioningAttachmentTypes.detectAllowed("伪装.pdf", "#!/bin/sh\n".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void storageNameStripsPathSeparators() {
        assertEquals("a_b_c.png", PositioningAttachmentTypes.storageName("a/b\\c.png"));
        assertEquals("attachment", PositioningAttachmentTypes.storageName(null));
    }

    private static byte[] id3() {
        byte[] content = new byte[16];
        content[0] = 'I'; content[1] = 'D'; content[2] = '3';
        return content;
    }

    private static byte[] mp4() {
        byte[] content = new byte[16];
        content[0] = 0; content[1] = 0; content[2] = 0; content[3] = 0x18;
        content[4] = 'f'; content[5] = 't'; content[6] = 'y'; content[7] = 'p';
        return content;
    }
}
