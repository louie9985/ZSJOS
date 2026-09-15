package cn.iocoder.yudao.module.bpm.framework.flowable.core.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 锁住流程变量过滤对集合可变性的要求。
 *
 * <p>{@link FlowableUtils#filterProcessInstanceFormVariable} 会直接 remove 传入的 Map，
 * 因此调用方必须自行保证可变。业务侧曾用 {@code Map.of(...)} 传入启动变量，导致流程发起时
 * 抛 {@link UnsupportedOperationException}；发起入口现在统一拷贝一份可变副本。
 */
class FlowableUtilsVariableMutabilityTest {

    @Test
    void filterRejectsImmutableVariableMap() {
        Map<String, Object> immutable = Map.of("PROCESS_STATUS", 1, "bizId", 7L);

        assertThrows(UnsupportedOperationException.class,
                () -> FlowableUtils.filterProcessInstanceFormVariable(immutable));
    }

    @Test
    void filterRemovesSystemStatusFromMutableCopyOfImmutableInput() {
        Map<String, Object> immutable = Map.of("PROCESS_STATUS", 1, "bizId", 7L);

        // 发起入口对调用方传入的变量所做的处理：拷贝成可变 Map 后再过滤。
        Map<String, Object> variables = new HashMap<>(immutable);
        FlowableUtils.filterProcessInstanceFormVariable(variables);

        assertFalse(variables.containsKey("PROCESS_STATUS"));
    }
}
