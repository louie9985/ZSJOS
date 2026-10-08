package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderSubmitReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.gift.GiftConfigDO;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SalesOrderGiftSnapshotTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 8, 12, 0);
    private final SalesOrderGiftSnapshot historical = new SalesOrderGiftSnapshot("old", "原礼品", List.of("原分类", "原礼品"), "2026-01-01T00:00:00");

    @Test void readsBothStorageFormatsWithoutInventingLabels() {
        for (String json : List.of(JsonUtils.toJsonString(List.of(historical)), JsonUtils.toJsonString(List.of(JsonUtils.toJsonString(historical))))) {
            assertEquals(List.of(historical), SalesOrderGiftSnapshot.read(json));
        }
        var missing = SalesOrderGiftSnapshot.read("[{\"code\":\"legacy\"}]").getFirst();
        assertNull(missing.name()); assertNull(missing.path()); assertNull(missing.snapshotAt());
    }

    @Test void unchangedSelectionNeverConsultsCurrentCatalogAndKeepsTimestamp() {
        var result = SalesOrderGiftSnapshot.resolve(List.of("old"), JsonUtils.toJsonString(List.of(historical)),
                () -> { throw new AssertionError("must not query renamed/deleted catalog"); }, now);
        assertEquals(List.of(historical), result);
    }

    @Test void addsCurrentSnapshotRetainsOldAndRemovesExplicitly() {
        GiftConfigDO gift = new GiftConfigDO(); gift.setId(3L); gift.setCode("new"); gift.setName("新礼品"); gift.setStatus(0); gift.setParentId(0L);
        String old = JsonUtils.toJsonString(List.of(historical));
        var result = SalesOrderGiftSnapshot.resolve(List.of("old", "new", "new"), old, () -> List.of(gift), now);
        assertEquals(2, result.size()); assertEquals(historical, result.getFirst());
        assertEquals(now.toString(), result.get(1).snapshotAt());
        assertTrue(JsonUtils.getObjectMapper().readTree(JsonUtils.toJsonString(result)).get(0).isObject());
        assertEquals(List.of(), SalesOrderGiftSnapshot.resolve(List.of(), old, () -> List.of(gift), now));
        assertEquals(List.of(), SalesOrderGiftSnapshot.resolve(null, old, () -> List.of(gift), now));
        gift.setStatus(1);
        assertThrows(ServiceException.class, () -> SalesOrderGiftSnapshot.resolve(List.of("new"), old, () -> List.of(gift), now));
    }

    @Test void malformedHistoryCannotBeSilentlyClearedAndBadCodesAreRejected() {
        for (String json : List.of("broken", "{}", "null", "[\"plain code\"]", "[{\"code\":\"x\",\"name\":1}]", "[{\"code\":\"x\"},{\"code\":\"x\"}]")) {
            assertThrows(ServiceException.class, () -> SalesOrderGiftSnapshot.resolve(List.of(), json, List::of, now));
        }
        assertThrows(ServiceException.class, () -> SalesOrderGiftSnapshot.resolve(List.of(" "), null, List::of, now));
        assertThrows(ServiceException.class, () -> SalesOrderGiftSnapshot.resolve(List.of("missing"), null, List::of, now));
    }

    @Test void requestAcceptsOnlyArrayAndDoesNotCoerceSerializedArray() {
        assertThrows(RuntimeException.class, () -> JsonUtils.getObjectMapper().readValue("{\"giftItems\":\"[]\"}", SalesOrderSubmitReqVO.class));
        assertEquals(List.of("old"), JsonUtils.getObjectMapper().readValue("{\"giftItems\":[\"old\"]}", SalesOrderSubmitReqVO.class).getGiftItems());
    }
}
