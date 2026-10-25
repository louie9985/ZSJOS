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

    @Test void unscheduledIsSelectableWithoutPendingButInactiveOwnerIsExcluded() {
        var mapper = mock(DeliveryClassMapper.class);
        var service = spy(new DeliveryClassServiceImpl());
        ReflectionTestUtils.setField(service,"mapper",mapper);
        var pending = new DeliveryClassDO().setId(1L).setSystemClass(true).setClassKind("PENDING");
        var active = new DeliveryClassDO().setId(2L).setSystemClass(true).setClassKind("UNSCHEDULED").setHomeroomUserId(20L);
        var inactive = new DeliveryClassDO().setId(3L).setSystemClass(true).setClassKind("UNSCHEDULED").setHomeroomUserId(30L);
        when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(pending,active,inactive));
        doReturn(new cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO().setId(20L)).when(service).validateHomeroom(20L);
        doThrow(cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.DELIVERY_CLASS_USER_INVALID)).when(service).validateHomeroom(30L);
        assertEquals(List.of(2L),service.options(null,false).stream().map(row -> row.getId()).toList());
    }

    private List<Long> optionIds(Long categoryId, boolean includePending) throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), DeliveryClassDO.class);
        DeliveryClassMapper mapper = mock(DeliveryClassMapper.class);
        DeliveryClassServiceImpl service = new DeliveryClassServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:class_options;MODE=MySQL")) {
            connection.createStatement().execute("CREATE TABLE classes(id BIGINT, category_id BIGINT, system_class BOOLEAN, status VARCHAR, class_name VARCHAR, class_kind VARCHAR DEFAULT 'NORMAL')");
            connection.createStatement().execute("INSERT INTO classes(id,category_id,system_class,status,class_name) VALUES (1,NULL,TRUE,'SERVING','Pending'),(2,70,FALSE,'SERVING','A'),(3,80,FALSE,'SERVING','B'),(4,80,FALSE,'COMPLETED','C'),(5,NULL,TRUE,'COMPLETED','Old')");
            connection.createStatement().execute("UPDATE classes SET class_kind='PENDING' WHERE id=1");
            connection.createStatement().execute("INSERT INTO classes VALUES(6,NULL,TRUE,'SERVING','Exception','EXCEPTION')");
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
                            .setClassKind(result.getString("class_kind")).setSystemClass(result.getBoolean("system_class")));
                    return rows;
                }
            });
            return service.options(categoryId, includePending).stream().map(row -> row.getId()).toList();
        }
    }
}
