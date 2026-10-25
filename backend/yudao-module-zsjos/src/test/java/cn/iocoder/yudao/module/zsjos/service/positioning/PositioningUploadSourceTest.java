package cn.iocoder.yudao.module.zsjos.service.positioning;

import cn.iocoder.yudao.module.infra.framework.file.core.client.local.LocalFileClient;
import cn.iocoder.yudao.module.infra.framework.file.core.client.local.LocalFileClientConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

public class PositioningUploadSourceTest {
    @TempDir Path temporary;

    @Test void sourceIsRepeatableAndDeletedAfterUse() throws Exception {
        Path source;
        try (var upload = PositioningUploadSource.stage(new ByteArrayInputStream(new byte[]{1,2,3}), 3)) {
            source = upload.path();
            assertArrayEquals(new byte[]{1,2,3}, Files.readAllBytes(source));
            assertEquals(3, Files.size(source));
        }
        assertFalse(Files.exists(source));
    }

    @Test void incompleteOrOversizedSourceDoesNotMasqueradeAsDeclaredSize() {
        assertThrows(java.io.IOException.class, () -> PositioningUploadSource.stage(new ByteArrayInputStream(new byte[]{1}), 2));
        assertThrows(java.io.IOException.class, () -> PositioningUploadSource.stage(new ByteArrayInputStream(new byte[]{1,2}), 1));
    }

    @Test void sevenHundredMiBStreamsWithOnlyNinetySixMiBHeap() throws Exception {
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx96m", "-cp", System.getProperty("java.class.path"), LargeUploadProbe.class.getName(), temporary.toString())
                .redirectErrorStream(true).start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("bounded-heap upload probe timed out");
        }
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        assertTrue(output.contains("PASS_700_MIB_UNDER_96_MIB"), output);
    }

    public static class LargeUploadProbe {
        public static void main(String[] args) throws Exception {
            Path root = Path.of(args[0]), source = root.resolve("synthetic.png");
            long size = 700L * 1024 * 1024;
            byte[] header = {(byte)137,80,78,71,13,10,26,10};
            try (var file = new RandomAccessFile(source.toFile(), "rw")) { file.setLength(size); file.write(header); }
            var config = new LocalFileClientConfig();
            config.setBasePath(root.resolve("stored").toString()); config.setDomain("https://files.invalid");
            var client = new LocalFileClient(1L, config);
            Path staged;
            try (var input = Files.newInputStream(source); var upload = PositioningUploadSource.stage(input, size)) {
                staged = upload.path();
                if (!"image/png".equals(PositioningAttachmentTypes.detectAllowed("synthetic.png", staged))) throw new AssertionError("MIME mismatch");
                client.uploadFile(staged, "safe/synthetic.png", "image/png");
                if (Files.size(root.resolve("stored/safe/synthetic.png")) != size) throw new AssertionError("truncated upload");
            }
            if (Files.exists(staged)) throw new AssertionError("staging file leaked");
            System.out.println("PASS_700_MIB_UNDER_96_MIB");
        }
    }
}
