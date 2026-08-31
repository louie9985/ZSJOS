package cn.iocoder.yudao.module.zsjos.framework.mediascreen;

import jakarta.validation.Valid;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = MediaScreenProperties.PREFIX)
public class MediaScreenProperties {

    static final String PREFIX = "yudao.media-screen";

    private boolean enabled = false;
    private List<String> trustedProxies = new ArrayList<>();
    @Valid private List<Client> clients = new ArrayList<>();
    @Valid private Cache cache = new Cache();
    @Valid private Limits limits = new Limits();
    @Valid private NewMedia newMedia = new NewMedia();
    @Valid private Snapshot snapshot = new Snapshot();

    @PostConstruct
    void validateAccessConfiguration() {
        if (!enabled) return;
        if (cache.refreshIntervalSeconds < 5) throw new IllegalStateException("media-screen refresh interval must be at least 5 seconds");
        if (clients.isEmpty()) throw new IllegalStateException("media-screen clients must not be empty when enabled");
        for (String cidr : trustedProxies) requireCidr(cidr);
        for (Client client : clients) {
            if (client.tenantId == null || client.tenantId <= 0) throw new IllegalStateException("media-screen tenant-id must be positive");
            if (client.cidrs.isEmpty()) throw new IllegalStateException("media-screen client CIDRs must not be empty");
            for (String cidr : client.cidrs) requireCidr(cidr);
        }
    }
    private static void requireCidr(String cidr) {
        String probe = cidr == null ? "" : cidr.split("/", 2)[0];
        if (!MediaScreenAccessFilter.matches(probe, cidr == null ? "" : cidr)) throw new IllegalStateException("invalid media-screen CIDR");
    }

    @Data
    public static class Client {
        private Long tenantId;
        private List<String> cidrs = new ArrayList<>();
    }
    @Data
    public static class Cache {
        @Min(1) private long statsTtlSeconds = 15;
        @Min(1) private long historyTtlSeconds = 60;
        @Min(1) private long maintenanceTtlSeconds = 5;
        @Min(1) private long refreshIntervalSeconds = 5;
        @Min(0) private long staleIfErrorSeconds = 60;
    }
    @Data
    public static class Limits {
        @Min(1) private int maxHistoryDays = 366;
    }

    @Data
    public static class NewMedia {
        private List<Long> departmentIds = new ArrayList<>();
    }

    @Data
    public static class Snapshot {
        @Min(0) @Max(23) private int hour = 4;
        @Min(0) @Max(59) private int minute = 0;
        @Min(60000) private long scanDelayMs = 300000;
    }
}
