package cn.iocoder.yudao.module.zsjos.service.media;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class MediaCollaborationNotifyPublisherTest {
    @InjectMocks MediaCollaborationNotifyPublisher publisher;
    @Mock MediaWorkflowEventService events;
    @Test void excludesActorAndNullsAndDeduplicatesRecipients() {
        publisher.send("scene", "type", 1L, "business-no", 2L, "key", Arrays.asList(2L, 3L, 3L, null), Map.of());
        ArgumentCaptor<Map<String,Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(events).notify(eq("scene"),eq("type"),eq(1L),isNull(),eq(2L),eq("key"),payload.capture());
        assertEquals(Set.of(3L), payload.getValue().get("assigneeUserIds"));
        assertEquals("business-no", payload.getValue().get("bizNo"));
    }
    @Test void selfOnlyDoesNotCreateAnUndeliverableEvent() {
        publisher.send("scene", "type", 1L, "", 2L, "key", List.of(2L), Map.of());
        verifyNoInteractions(events);
    }
}
