package cn.iocoder.yudao.framework.common.util.collection;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link CollectionUtils} 的单元测试
 */
public class CollectionUtilsTest {

    @Data
    @AllArgsConstructor
    private static class Dog {

        private Integer id;
        private String name;
        private String code;

    }

    @Test
    public void testDiffList() {
        // 准备参数
        Collection<Dog> oldList = Arrays.asList(
                new Dog(1, "花花", "hh"),
                new Dog(2, "旺财", "wc")
        );
        Collection<Dog> newList = Arrays.asList(
                new Dog(null, "花花2", "hh"),
                new Dog(null, "小白", "xb")
        );
        BiFunction<Dog, Dog, Boolean> sameFunc = (oldObj, newObj) -> {
            boolean same = oldObj.getCode().equals(newObj.getCode());
            // 如果相等的情况下，需要设置下 id，后续好更新
            if (same) {
                newObj.setId(oldObj.getId());
            }
            return same;
        };

        // 调用
        List<List<Dog>> result = CollectionUtils.diffList(oldList, newList, sameFunc);
        // 断言
        assertEquals(result.size(), 3);
        // 断言 create
        assertEquals(result.get(0).size(), 1);
        assertEquals(result.get(0).get(0), new Dog(null, "小白", "xb"));
        // 断言 update
        assertEquals(result.get(1).size(), 1);
        assertEquals(result.get(1).get(0), new Dog(1, "花花2", "hh"));
        // 断言 delete
        assertEquals(result.get(2).size(), 1);
        assertEquals(result.get(2).get(0), new Dog(2, "旺财", "wc"));
    }

    @Test
    public void testAggregateAndConvertSetBySupplier() {
        List<Integer> values = Arrays.asList(1, 2, 2, null, 3);

        assertEquals(8L, CollectionUtils.sum(values, value -> value == null ? 0 : value));
        assertEquals(3L, CollectionUtils.count(values, value -> value != null && value >= 2));
        assertEquals(3L, CollectionUtils.distinctCount(values, value -> value));
        Set<Integer> result = CollectionUtils.convertSetBySupplier(values, value -> value, TreeSet::new);
        assertEquals(new TreeSet<>(Arrays.asList(1, 2, 3)), result);
        assertEquals(new BigDecimal("3.50"), CollectionUtils.sumBigDecimal(
                Arrays.asList(new BigDecimal("1.25"), null, new BigDecimal("2.25")), value -> value));
    }

}
