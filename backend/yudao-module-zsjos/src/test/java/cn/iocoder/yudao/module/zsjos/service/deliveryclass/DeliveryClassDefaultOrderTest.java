package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.DeliveryClassPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.deliveryclass.DeliveryClassDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DeliveryClassDefaultOrderTest {
    @Test void recentExceptionClassesFollowAllNormalClassesAcrossPages() throws Exception {
        assertEquals(List.of(1L,7L), page(true,0,2));
        assertEquals(List.of(3L,2L), page(true,2,2));
        assertEquals(List.of(5L,4L), page(true,4,2));
    }

    @Test void personalScopeKeepsOwnExceptionLastAndExcludesOtherOwners() throws Exception {
        assertEquals(List.of(7L,3L,2L,5L,4L), page(false,0,10));
    }

    @Test void assignmentFilterKeepsOwnedDefaultsAndExcludesPendingArchivesAndOtherOwners() throws Exception {
        assertEquals(List.of(7L,3L,2L),page(false,0,10,true));
    }
    private List<Long> page(boolean managed, int offset, int limit) throws Exception {
        return page(managed,offset,limit,false);
    }
    private List<Long> page(boolean managed, int offset, int limit, boolean assignable) throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),""),DeliveryClassDO.class);
        var mapper=mock(DeliveryClassMapper.class,CALLS_REAL_METHODS);
        var request=new DeliveryClassPageReqVO();request.setStatus("SERVING");request.setAssignableOnly(assignable);
        var query=mapper.classQuery(request,Set.of(),7L,false,managed);
        String segment=query.getSqlSegment();
        var matcher=Pattern.compile("#\\{ew.paramNameValuePairs\\.(\\w+)}").matcher(segment);
        var arguments=new ArrayList<Object>();
        while(matcher.find()) arguments.add(query.getParamNameValuePairs().get(matcher.group(1)));
        segment=matcher.replaceAll("?");
        try(var connection=DriverManager.getConnection("jdbc:h2:mem:class_order_"+UUID.randomUUID()+";MODE=MySQL")) {
            connection.createStatement().execute("CREATE TABLE classes(id BIGINT, system_class INT, class_kind VARCHAR, create_time TIMESTAMP, homeroom_user_id BIGINT, status VARCHAR)");
            connection.createStatement().execute("INSERT INTO classes VALUES "
                + "(1,1,'PENDING','2020-01-01',NULL,'SERVING'),"
                + "(2,0,'NORMAL','2025-01-01',7,'SERVING'),(3,0,'NORMAL','2025-01-01',7,'SERVING'),"
                + "(4,1,'EXCEPTION','2027-01-01',7,'SERVING'),(5,1,'EXCEPTION','2028-01-01',7,'SERVING'),"
                + "(6,0,'NORMAL','2029-01-01',8,'SERVING'),(7,1,'UNSCHEDULED','2030-01-01',7,'SERVING')");
            try(var statement=connection.prepareStatement("SELECT id FROM classes WHERE "+segment+" LIMIT "+limit+" OFFSET "+offset)) {
                for(int i=0;i<arguments.size();i++) statement.setObject(i+1,arguments.get(i));
                var ids=new ArrayList<Long>();
                try(var result=statement.executeQuery()) { while(result.next()) ids.add(result.getLong(1)); }
                return ids;
            }
        }
    }
}
