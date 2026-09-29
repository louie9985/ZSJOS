package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyBusinessEvent;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifyRecipientDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.notify.NotifyBusinessOutboxDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NotifyOutboxEventCodecTest {
    @ParameterizedTest @ValueSource(strings = {"in_app", "wecom"})
    void fixedIdentitiesSurvivePersistenceAndWecomCheckpoint(String channel) {
        var event = NotifyBusinessEvent.builder().sceneCode("test.scene").recipientMode("FIXED")
                .fixedRecipients(List.of(NotifyRecipientDTO.admin(7L), NotifyRecipientDTO.partner(7L)))
                .payload(Map.of("calendar.title", "考试安排")).build();
        var row = row(NotifyOutboxEventCodec.encode(event, channel));
        if ("wecom".equals(channel)) {
            var checkpoint = JsonUtils.parseObject(row.getPayload(), WecomOutboxPayload.class);
            checkpoint.setRecipients(event.getFixedRecipients().stream().map(identity -> {
                var item = new WecomOutboxPayload.Recipient();
                item.setContext(cn.iocoder.yudao.module.system.api.notify.dto.NotifyDeliveryContext.builder()
                        .tenantId(10L).ruleId(20L).sceneCode("test.scene").sourceEventKey("test-key")
                        .userType(identity.getUserType()).userId(identity.getUserId()).build());
                return item;
            }).toList());
            row.setPayload(JsonUtils.toJsonString(checkpoint));
        }
        var decoded = NotifyOutboxEventCodec.decode(row);
        assertEquals("FIXED", decoded.event().getRecipientMode());
        assertEquals(event.getFixedRecipients(), decoded.event().getFixedRecipients());
        assertEquals(event.getPayload(), decoded.event().getPayload());
        assertEquals("wecom".equals(channel), decoded.wecom());
        assertEquals(10L, decoded.event().getTenantId());
        assertEquals(20L, decoded.event().getTargetRuleId());
    }

    @Test void legacyPayloadsRemainRuleBased() {
        for (String json : List.of("null", "{\"title\":\"旧通知\"}",
                "{\"deliveryFormat\":\"wecom-outbox-v1\",\"eventPayload\":{\"title\":\"旧通知\"}}")) {
            var event = NotifyOutboxEventCodec.decode(row(json)).event();
            assertEquals("RULE", event.getRecipientMode());
            assertTrue(event.validatedFixedRecipients().isEmpty());
        }
    }

    @Test void corruptedFixedEnvelopeNeverFallsBackToRuleRecipients() {
        assertThrows(IllegalArgumentException.class, () -> NotifyOutboxEventCodec.decode(row(
                "{\"deliveryFormat\":\"notify-fixed-event-v1\",\"eventPayload\":{}}")));
        assertThrows(IllegalArgumentException.class, () -> NotifyOutboxEventCodec.decode(row(
                "{\"deliveryFormat\":\"wecom-outbox-v1\",\"recipientMode\":\"FIXED\",\"fixedRecipients\":[]}")));
    }

    @Test void invalidTypesAndExternalCalendarIdentitiesAreRejected() {
        var event = NotifyBusinessEvent.builder().sceneCode("zsjos.calendar.exam").recipientMode("FIXED")
                .fixedRecipients(List.of(NotifyRecipientDTO.partner(7L))).build();
        assertThrows(IllegalArgumentException.class, event::validatedFixedRecipients);
        assertThrows(IllegalArgumentException.class, () -> event.toBuilder().sceneCode("test.scene")
                .fixedRecipients(List.of(new NotifyRecipientDTO(99, 7L))).build().validatedFixedRecipients());
        assertThrows(IllegalArgumentException.class, () -> event.toBuilder().recipientMode("OTHER").build().validatedFixedRecipients());
        assertThrows(IllegalArgumentException.class, () -> NotifyBusinessEvent.builder()
                .sceneCode("zsjos.calendar.course").build().validatedFixedRecipients());
        assertThrows(IllegalArgumentException.class, () -> NotifyOutboxEventCodec.decode(row(
                "{\"deliveryFormat\":\"notify-fixed-event-v99\"}")));
    }

    private NotifyBusinessOutboxDO row(String json) {
        var row = new NotifyBusinessOutboxDO().setTargetRuleId(20L)
                .setSceneCode("test.scene").setSourceEventKey("test-key").setPayload(json);
        row.setTenantId(10L);
        return row;
    }
}
