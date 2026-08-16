package cn.iocoder.yudao.module.zsjos.service.order;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

final class SalesOrderCursorCodec {
    private SalesOrderCursorCodec() {}

    static String encode(LocalDateTime time, Long id, Long userId, String status, String keyword) {
        String raw = time + "|" + id + "|" + userId + "|" + String.valueOf(status) + "|" + String.valueOf(keyword);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static Cursor decode(String value, Long userId, String status, String keyword) {
        if (value == null || value.isBlank()) return null;
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 5 || !parts[2].equals(String.valueOf(userId))
                    || !parts[3].equals(String.valueOf(status)) || !parts[4].equals(String.valueOf(keyword))) {
                throw new IllegalArgumentException("cursor context mismatch");
            }
            return new Cursor(LocalDateTime.parse(parts[0]), Long.valueOf(parts[1]));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid sales order cursor", ex);
        }
    }

    record Cursor(LocalDateTime time, Long id) {}
}
