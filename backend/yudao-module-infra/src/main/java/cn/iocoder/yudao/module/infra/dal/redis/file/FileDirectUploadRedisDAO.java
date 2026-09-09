package cn.iocoder.yudao.module.infra.dal.redis.file;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
public class FileDirectUploadRedisDAO {

    private static final String KEY_PREFIX = "infra:file:direct-upload:";
    private static final Duration SESSION_TTL = Duration.ofHours(25);

    private final StringRedisTemplate redisTemplate;

    public FileDirectUploadRedisDAO(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public FileDirectUploadSession get(String tokenDigest) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + tokenDigest);
        return json == null ? null : JsonUtils.parseObject(json, FileDirectUploadSession.class);
    }

    public void set(String tokenDigest, FileDirectUploadSession session) {
        redisTemplate.opsForValue().set(KEY_PREFIX + tokenDigest, JsonUtils.toJsonString(session), SESSION_TTL);
    }
}
