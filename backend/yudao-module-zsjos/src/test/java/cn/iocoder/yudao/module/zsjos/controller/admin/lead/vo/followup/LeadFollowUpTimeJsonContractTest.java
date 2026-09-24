package cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.followup;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeadFollowUpTimeJsonContractTest {

    private static final long NEXT_FOLLOW_UP_AT = 1786258800000L;

    @Test
    void requestDeserializesNextFollowUpAtFromEpochMilliseconds() {
        String json = """
                {"method":"phone","result":"interested","leadCategory":"a",
                 "nextFollowUpAt":1786258800000,"images":[],"idempotencyKey":"request-1"}
                """;

        LeadFollowUpCreateReqVO request = JsonUtils.parseObject(json, LeadFollowUpCreateReqVO.class);

        assertEquals(LocalDateTime.ofInstant(Instant.ofEpochMilli(NEXT_FOLLOW_UP_AT),
                ZoneId.systemDefault()), request.getNextFollowUpAt());
    }

    @Test
    void optionalTimeAcceptsOmittedAndExplicitNullAtTheRequestBoundary() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            for (String next : new String[]{"", ",\"nextFollowUpAt\":null"}) {
                String json = "{\"method\":\"phone\",\"result\":\"interested\","
                        + "\"remark\":\"已联系\",\"idempotencyKey\":\"request-1\"" + next + "}";
                var request = JsonUtils.parseObject(json, LeadFollowUpCreateReqVO.class);
                org.junit.jupiter.api.Assertions.assertNull(request.getNextFollowUpAt());
                assertTrue(factory.getValidator().validate(request).isEmpty());
            }
        }
    }

    @Test
    void responseSerializesDateTimesAsEpochMilliseconds() {
        LeadFollowUpRespVO response = new LeadFollowUpRespVO();
        response.setOccurredAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(NEXT_FOLLOW_UP_AT),
                ZoneId.systemDefault()));

        String json = JsonUtils.toJsonString(response);

        assertTrue(json.contains("\"occurredAt\":" + NEXT_FOLLOW_UP_AT));
    }
}
