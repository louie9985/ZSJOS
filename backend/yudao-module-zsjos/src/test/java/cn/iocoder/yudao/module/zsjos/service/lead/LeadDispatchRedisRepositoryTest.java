package cn.iocoder.yudao.module.zsjos.service.lead;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.github.fppt.jedismock.RedisServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LeadDispatchRedisRepositoryTest {

    private RedisServer server;
    private RedissonConnectionFactory connectionFactory;
    private StringRedisTemplate redis;
    private LeadDispatchRedisRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        // 每个用例独享随机端口及数据，避免触碰运行中的派单池。
        server = RedisServer.newRedisServer().start();
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + server.getHost() + ":" + server.getBindPort());
        connectionFactory = new RedissonConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        repository = new LeadDispatchRedisRepository(redis);
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() throws Exception {
        TenantContextHolder.clear();
        if (connectionFactory != null) connectionFactory.destroy();
        if (server != null) server.stop();
    }

    @Test
    void firstOnlineWaitsBehindExistingSalesAndRotatesInArrivalOrder() {
        repository.heartbeat(101L, true);
        repository.heartbeat(102L, true);
        repository.heartbeat(103L, true);
        assertOrder(101L, 102L, 103L, 101L, 102L, 103L);
        assertEquals(3L, repository.poolSize());
    }

    @Test
    void reconnectAfterAcceptanceCannotOvertakeSixContinuouslyOnlineSales() {
        for (long saleId = 101; saleId <= 107; saleId++) repository.heartbeat(saleId, true);
        assertEquals(101L, repository.rotateNext());
        assertTrue(repository.tryReserve(1001L, 101L, 60));
        repository.release(1001L, 101L);
        for (long next = 102; next <= 107; next++) {
            repository.offline(101L);
            assertFalse(repository.isOnline(101L));
            repository.heartbeat(101L, true);
            for (long saleId = 102; saleId <= 107; saleId++) repository.heartbeat(saleId, true);
            assertEquals(next, repository.rotateNext());
            assertEquals(7L, repository.poolSize());
        }
        // 每次重连都重新排队；最后一次重连之后停止重连，等待一轮再获得机会。
        assertOrder(102L, 103L, 104L, 105L, 106L, 101L, 107L);
    }

    @Test
    void repeatedHeartbeatKeepsPositionAndUpdatesMode() {
        repository.heartbeat(101L, true);
        repository.heartbeat(102L, true);
        assertEquals(101L, repository.rotateNext());
        repository.heartbeat(101L, false);
        repository.heartbeat(102L, true);
        assertTrue(repository.isOnline(101L));
        assertFalse(repository.isAccepting(101L));
        assertOrder(102L, 101L);
        assertEquals(2L, repository.poolSize());
    }

    @Test
    void expiredPresenceRejoinsAtTailWithoutDuplicates() {
        repository.heartbeat(101L, true);
        repository.heartbeat(102L, true);
        repository.heartbeat(103L, true);
        // 模拟 TTL 到期后的键状态，不等待业务的 90 秒超时。
        redis.delete("zsjos:lead-dispatch:1:sale:presence:101");
        repository.heartbeat(101L, true);
        repository.heartbeat(101L, true);
        assertEquals(3L, repository.poolSize());
        assertOrder(102L, 103L, 101L, 102L);
    }

    @Test
    void offlineRemovesCandidateAndSingleSaleCanReconnect() {
        assertNull(repository.rotateNext());
        repository.heartbeat(101L, true);
        assertOrder(101L, 101L);
        repository.offline(101L);
        repository.offline(101L);
        assertEquals(0L, repository.poolSize());
        assertNull(repository.rotateNext());
        repository.heartbeat(101L, true);
        assertEquals(1L, repository.poolSize());
        assertEquals(101L, repository.rotateNext());
    }

    private void assertOrder(Long... expected) {
        assertEquals(List.of(expected), java.util.stream.IntStream.range(0, expected.length)
                .mapToObj(ignored -> repository.rotateNext()).toList());
    }
}
