package cn.iocoder.yudao.module.zsjos.framework.mediascreen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MediaScreenAccessFilterTest {
    @Test void matchesIpv4AndCidr() {
        assertTrue(MediaScreenAccessFilter.matches("192.168.10.9", "192.168.10.0/24"));
        assertFalse(MediaScreenAccessFilter.matches("192.168.11.9", "192.168.10.0/24"));
        assertTrue(MediaScreenAccessFilter.matches("127.0.0.1", "127.0.0.1"));
    }
    @Test void matchesIpv6Cidr() {
        assertTrue(MediaScreenAccessFilter.matches("2001:db8::1", "2001:db8::/32"));
        assertFalse(MediaScreenAccessFilter.matches("2001:db9::1", "2001:db8::/32"));
    }
    @Test void rejectsInvalidRanges() {
        assertFalse(MediaScreenAccessFilter.matches("127.0.0.1", "bad-range"));
        assertFalse(MediaScreenAccessFilter.matches("127.0.0.1", "127.0.0.1/40"));
    }
}
