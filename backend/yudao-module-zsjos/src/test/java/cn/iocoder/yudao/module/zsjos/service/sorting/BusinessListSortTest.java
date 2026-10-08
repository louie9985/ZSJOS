package cn.iocoder.yudao.module.zsjos.service.sorting;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class BusinessListSortTest {
    record Row(Long id, String name, BigDecimal amount, LocalDateTime time) {}
    private final BusinessListSort<Row> sort = new BusinessListSort<Row>(Row::id)
            .field("name", Row::name).field("amount", Row::amount).field("time", Row::time);
    private BiFunction<Integer,Integer,PageResult<Row>> loader(List<Row> rows) {
        return (page, size) -> new PageResult<>(rows.subList(Math.min((page-1)*size,rows.size()), Math.min(page*size,rows.size())), (long) rows.size());
    }
    @Test void sortsAllBatchesBeforePagingAndUsesNumericValues() {
        var rows = IntStream.rangeClosed(1, 450).mapToObj(i -> new Row((long)i, "学员"+i, BigDecimal.valueOf(451-i), null)).toList();
        AtomicInteger calls = new AtomicInteger();
        var result = sort.page("amount", "ascend", 2, 20, (page,size) -> { calls.incrementAndGet(); assertEquals(200,size); return loader(rows).apply(page,size); });
        assertEquals(450L,result.getTotal()); assertEquals(3,calls.get());
        assertEquals(430L,result.getList().getFirst().id()); assertEquals(411L,result.getList().getLast().id());
    }
    @Test void chinesePinyinNullsLastAndStableIdentityInBothDirections() {
        var rows = List.of(new Row(1L,"张",null,null),new Row(2L,"李",null,null),new Row(3L,"陈",null,null),new Row(4L,"李",null,null),new Row(5L,"",null,null));
        assertEquals(List.of(3L,4L,2L,1L,5L), sort.page("name","ascend",1,20,loader(rows)).getList().stream().map(Row::id).toList());
        assertEquals(List.of(1L,4L,2L,3L,5L), sort.page("name","descend",1,20,loader(rows)).getList().stream().map(Row::id).toList());
    }
    @Test void cursorTraversesDuplicatesAndNullsWithoutRepeatingAcrossAllDirectionsAndTypes() {
        var rows = IntStream.rangeClosed(1, 45).mapToObj(i -> new Row((long)i,i%6==0?null:List.of("陈","李","张").get(i%3),i%6==0?null:BigDecimal.valueOf(i%4),i%6==0?null:LocalDateTime.of(2026,10,1,0,0).plusDays(i%4))).toList();
        for (String field : List.of("name","amount","time")) for (String direction : List.of("ascend","descend")) {
            List<Long> ids = new ArrayList<>(); String cursor = null;
            do {
                var page = sort.cursor(field,direction,cursor,"tenant=1/user=2/filter=3",7,loader(rows));
                ids.addAll(page.getList().stream().map(Row::id).toList()); cursor=page.getNextCursor();
                assertTrue(ids.size()<=45);
            } while(cursor!=null);
            assertEquals(sort.page(field,direction,1,100,loader(rows)).getList().stream().map(Row::id).toList(),ids);
        }
    }
    @Test void rejectsInvalidInputAndCursorContextBeforeLoading() {
        assertFalse(sort.requested(null,null));
        assertThrows(ServiceException.class,()->sort.requested("id desc; DROP TABLE x","ascend"));
        assertThrows(ServiceException.class,()->sort.requested("name",null));
        assertThrows(ServiceException.class,()->sort.requested(null,"descend"));
        var rows=List.of(new Row(1L,"陈",null,null),new Row(2L,"李",null,null));
        String token=sort.cursor("name","ascend",null,"tenant=1/user=2",1,loader(rows)).getNextCursor();
        assertThrows(ServiceException.class,()->sort.cursor("name","ascend",token,"tenant=2/user=2",1,(p,s)->{fail("must validate before querying");return null;}));
        assertThrows(ServiceException.class,()->sort.cursor("name","descend",token,"tenant=1/user=2",1,loader(rows)));
        assertThrows(ServiceException.class,()->sort.cursor("name","ascend","broken","tenant=1/user=2",1,loader(rows)));
    }
    @Test void underlyingFailureNeverReturnsPartialResults() {
        assertThrows(IllegalStateException.class,()->sort.page("name","ascend",1,10,(p,s)->{throw new IllegalStateException("source unavailable");}));
    }
    @Test void sqlFastPathsAreAllowlistedAndDoNotSortSnapshotAmountsAsCurrentOrderValues() {
        assertEquals("(c.amount) IS NULL ASC, c.amount ASC, c.id DESC",BusinessSortSql.cashback("amount","ascend"));
        assertTrue(BusinessSortSql.cashback("baseAmount","descend").contains("WHEN c.type='valid' THEN NULL"));
        assertTrue(BusinessSortSql.withdrawal("applicationAmount","descend").endsWith("application_amount DESC, id DESC"));
        assertNull(BusinessSortSql.order("totalAmount","ascend"));
        assertNull(BusinessSortSql.order("id; SELECT 1","ascend"));
        assertNull(BusinessSortSql.withdrawal("submittedAt","evil"));
    }
}
