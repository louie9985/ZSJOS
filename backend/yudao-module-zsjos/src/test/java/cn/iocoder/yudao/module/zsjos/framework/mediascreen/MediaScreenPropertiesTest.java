package cn.iocoder.yudao.module.zsjos.framework.mediascreen;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaScreenPropertiesTest {

    @Test
    void bindsDocumentedYudaoPrefix() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "yudao.media-screen.enabled", "true",
                "yudao.media-screen.clients[0].tenant-id", "7",
                "yudao.media-screen.clients[0].cidrs[0]", "10.20.0.0/16",
                "yudao.media-screen.trusted-proxies[0]", "127.0.0.1",
                "yudao.media-screen.cache.stats-ttl-seconds", "30",
                "yudao.media-screen.new-media.department-ids[0]", "101",
                "yudao.media-screen.snapshot.hour", "4",
                "yudao.media-screen.snapshot.minute", "15"));

        MediaScreenProperties properties = new Binder(source)
                .bind(MediaScreenProperties.PREFIX, Bindable.of(MediaScreenProperties.class))
                .orElseThrow(() -> new IllegalStateException("media-screen properties were not bound"));

        assertTrue(properties.isEnabled());
        assertEquals(7L, properties.getClients().get(0).getTenantId());
        assertEquals("10.20.0.0/16", properties.getClients().get(0).getCidrs().get(0));
        assertEquals("127.0.0.1", properties.getTrustedProxies().get(0));
        assertEquals(30, properties.getCache().getStatsTtlSeconds());
        assertEquals(101L, properties.getNewMedia().getDepartmentIds().get(0));
        assertEquals(4, properties.getSnapshot().getHour());
        assertEquals(15, properties.getSnapshot().getMinute());
    }

    @Test
    void localProfileEnablesApprovedTenantAndIpv4Cidr() throws IOException {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        MutablePropertySources sources = new MutablePropertySources();
        loader.load("application-local", new FileSystemResource(
                        "../yudao-server/src/main/resources/application-local.yaml"))
                .forEach(sources::addLast);
        loader.load("application", new FileSystemResource(
                        "../yudao-server/src/main/resources/application.yaml"))
                .forEach(sources::addLast);

        MediaScreenProperties properties = new Binder(ConfigurationPropertySources.from(sources))
                .bind(MediaScreenProperties.PREFIX, Bindable.of(MediaScreenProperties.class))
                .orElseThrow(() -> new IllegalStateException("media-screen properties were not bound"));

        assertTrue(properties.isEnabled());
        assertEquals(1L, properties.getClients().get(0).getTenantId());
        assertEquals("0.0.0.0/0", properties.getClients().get(0).getCidrs().get(0));
        properties.validateAccessConfiguration();
    }

    @Test
    void enabledConfigurationRequiresAtLeastOneClient() {
        MediaScreenProperties properties = new MediaScreenProperties();
        properties.setEnabled(true);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                properties::validateAccessConfiguration);

        assertEquals("media-screen clients must not be empty when enabled", error.getMessage());
    }

    @Test
    void enabledClientRequiresAtLeastOneCidr() {
        MediaScreenProperties.Client client = new MediaScreenProperties.Client();
        client.setTenantId(1L);
        MediaScreenProperties properties = new MediaScreenProperties();
        properties.setEnabled(true);
        properties.setClients(java.util.List.of(client));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                properties::validateAccessConfiguration);

        assertEquals("media-screen client CIDRs must not be empty", error.getMessage());
    }
}
