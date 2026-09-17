package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeliveryClassOptionsTest {
    @Test
    void allServingCategoriesAndPendingAreReturnedWithoutCategory() throws Exception {
        assertEquals(List.of(1L, 2L, 3L), optionIds(null, true));
    }

    @Test
    void callersCanStillExplicitlyFilterCategoryOrExcludePending() throws Exception {
        assertEquals(List.of(1L, 3L), optionIds(80L, true));
        assertEquals(List.of(2L, 3L), optionIds(null, false));
    }

    private List<Long> optionIds(Long categoryId, boolean includePending) throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), DeliveryClassDO.class);
        DeliveryClassMapper mapper = mock(DeliveryClassMapper.class);
        DeliveryClassServiceImpl service = new DeliveryClassServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:class_options;MODE=MySQL")) {
            connection.createStatement().execute("CREATE TABLE classes(id BIGINT, category_id BIGINT, system_class BOOLEAN, status VARCHAR, class_name VARCHAR)");
            connection.createStatement().execute("INSERT INTO classes VALUES (1,NULL,TRUE,'SERVING','Pending'),(2,70,FALSE,'SERVING','A'),(3,80,FALSE,'SERVING','B'),(4,80,FALSE,'COMPLETED','C'),(5,NULL,TRUE,'COMPLETED','Old')");
            // Execute the generated predicate, so OR grouping and absent optional parameters are exercised.
            when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenAnswer(invocation -> {
                LambdaQueryWrapperX<DeliveryClassDO> query = invocation.getArgument(0);
                String segment = query.getSqlSegment();
                var matcher = Pattern.compile("#\\{ew.paramNameValuePairs\\.([^}]+)}").matcher(segment);
                List<Object> parameters = new ArrayList<>();
                while (matcher.find()) parameters.add(query.getParamNameValuePairs().get(matcher.group(1)));
                try (var statement = connection.prepareStatement("SELECT * FROM classes WHERE " + matcher.replaceAll("?"))) {
                    for (int i = 0; i < parameters.size(); i++) statement.setObject(i + 1, parameters.get(i));
                    var result = statement.executeQuery();
                    List<DeliveryClassDO> rows = new ArrayList<>();
                    while (result.next()) rows.add(new DeliveryClassDO().setId(result.getLong("id"))
                            .setSystemClass(result.getBoolean("system_class")));
                    return rows;
                }
            });
            return service.options(categoryId, includePending).stream().map(row -> row.getId()).toList();
        }
    }
}
