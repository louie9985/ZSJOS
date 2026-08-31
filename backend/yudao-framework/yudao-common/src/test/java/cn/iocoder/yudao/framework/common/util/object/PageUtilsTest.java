package cn.iocoder.yudao.framework.common.util.object;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageUtilsTest {

    @Test
    void testBuildPageResult() {
        PageParam pageParam = new PageParam().setPageNo(2).setPageSize(2);

        PageResult<Integer> result = PageUtils.buildPageResult(List.of(1, 2, 3, 4, 5), pageParam);

        assertEquals(List.of(3, 4), result.getList());
        assertEquals(5L, result.getTotal());
    }

}
