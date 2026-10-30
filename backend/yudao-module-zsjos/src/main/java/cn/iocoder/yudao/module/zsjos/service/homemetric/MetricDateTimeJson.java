package cn.iocoder.yudao.module.zsjos.service.homemetric;

import java.time.*;
import tools.jackson.core.*;
import tools.jackson.databind.*;
import tools.jackson.databind.ser.std.StdScalarSerializer;

/** Metric intervals are Beijing business time, independent of the deployment JVM timezone. */
public final class MetricDateTimeJson {
    private MetricDateTimeJson() {}
    public static final class Serializer extends StdScalarSerializer<LocalDateTime> {
        public Serializer() { super(LocalDateTime.class); }
        @Override public void serialize(LocalDateTime value,JsonGenerator generator,SerializationContext context) throws JacksonException {
            generator.writeNumber(value.atZone(MetricPeriod.ZONE).toInstant().toEpochMilli());
        }
    }
    public static final class Deserializer extends ValueDeserializer<LocalDateTime> {
        @Override public LocalDateTime deserialize(JsonParser parser,DeserializationContext context) throws JacksonException {
            if(parser.currentToken()==JsonToken.VALUE_STRING) {
                String value=parser.getValueAsString();
                try { return LocalDateTime.parse(value); }
                catch(java.time.format.DateTimeParseException ignored) {
                    return OffsetDateTime.parse(value).atZoneSameInstant(MetricPeriod.ZONE).toLocalDateTime();
                }
            }
            if(parser.currentToken()!=JsonToken.VALUE_NUMBER_INT)
                throw new IllegalArgumentException("指标时间必须为 ISO 时间或整数毫秒时间戳");
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(parser.getLongValue()),MetricPeriod.ZONE);
        }
    }
}
