package cn.iocoder.yudao.module.zsjos.service.feedback;

import cn.iocoder.yudao.module.infra.api.file.FileApi;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class FeedbackFileUrls {

    private FeedbackFileUrls() {
    }

    static Map<Long, String> resolve(FileApi fileApi, Collection<Long> ids) {
        List<Long> distinctIds = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) return Map.of();
        try {
            return fileApi.presignGetUrls(distinctIds, null);
        } catch (RuntimeException ignored) {
            // Infra batches fail atomically; retry individually so one missing file does not hide all others.
            Map<Long, String> urls = new LinkedHashMap<>();
            for (Long id : distinctIds) {
                try {
                    urls.put(id, fileApi.presignGetUrl(id, null));
                } catch (RuntimeException unavailable) {
                    urls.put(id, null);
                }
            }
            return urls;
        }
    }
}
