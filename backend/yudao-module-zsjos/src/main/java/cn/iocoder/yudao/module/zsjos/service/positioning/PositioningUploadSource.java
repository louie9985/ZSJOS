package cn.iocoder.yudao.module.zsjos.service.positioning;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** A repeatable disk source keeps MIME detection and storage retries out of the Java heap. */
public final class PositioningUploadSource implements AutoCloseable {
    private final Path path;

    private PositioningUploadSource(Path path) { this.path = path; }

    public static PositioningUploadSource stage(InputStream input, long size) throws IOException {
        PositioningAttachmentLimits.requireWithinBudget(java.util.List.of(size));
        Path path = Files.createTempFile("zsjos-positioning-", ".upload");
        boolean complete = false;
        try {
            try (var output = Files.newOutputStream(path)) {
                byte[] buffer = new byte[64 * 1024];
                long written = 0;
                for (int count; (count = input.read(buffer)) != -1;) {
                    written += count;
                    if (written > size) throw new IOException("上传文件长度与声明不一致");
                    output.write(buffer, 0, count);
                }
                if (written != size) throw new IOException("上传文件长度与声明不一致");
            }
            complete = true;
            return new PositioningUploadSource(path);
        } finally {
            if (!complete) Files.deleteIfExists(path);
        }
    }

    public Path path() { return path; }

    @Override public void close() throws IOException { Files.deleteIfExists(path); }
}
