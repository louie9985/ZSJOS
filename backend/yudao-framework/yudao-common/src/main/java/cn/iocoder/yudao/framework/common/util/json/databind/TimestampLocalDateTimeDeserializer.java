package cn.iocoder.yudao.framework.common.util.json.databind;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * 基于时间戳的 LocalDateTime 反序列化器
 *
 * @author 老五
 */
public class TimestampLocalDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {

    public static final TimestampLocalDateTimeDeserializer INSTANCE = new TimestampLocalDateTimeDeserializer();

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        // APIs commonly send ISO strings while legacy clients send epoch milliseconds.
        // Do not call getValueAsLong() for strings: Jackson converts an ISO value to 0,
        // which silently turns a valid appointment into 1970-01-01.
        if (p.currentToken() == tools.jackson.core.JsonToken.VALUE_STRING) {
            String value = p.getValueAsString();
            if (value == null || value.isBlank()) return null;
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException ignored) {
                return OffsetDateTime.parse(value).toLocalDateTime();
            }
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(p.getValueAsLong()), ZoneId.systemDefault());
    }

}
