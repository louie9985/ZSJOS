package cn.iocoder.yudao.module.zsjos.dal.mysql.account;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.account.MediaAccountDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MediaAccountCandidateScopeTest {
    @Test
    void executesCandidatePredicateAgainstSamePersonDifferentRelations() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "candidate-scope"), MediaAccountDO.class);
        MediaAccountMapper mapper = mock(MediaAccountMapper.class, CALLS_REAL_METHODS);
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:scope" + UUID.randomUUID() + ";MODE=MySQL", "sa", "")) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE zsjos_media_account(id bigint,tenant_id bigint,student_person_id bigint,create_service_relation_id bigint,owner_operator_user_id bigint,director_user_id bigint,update_time timestamp)");
                statement.execute("CREATE TABLE zsjos_service_relation(id bigint,tenant_id bigint,person_id bigint,operator_user_id bigint,content_director_user_id bigint,status varchar,acceptance_status varchar,deleted boolean)");
                statement.execute("INSERT INTO zsjos_service_relation VALUES(10,1,40,7,8,'active','accepted',false),(11,1,40,9,8,'active','accepted',false),(12,2,40,7,8,'active','accepted',false),(13,1,41,7,8,'active','accepted',false),(14,1,40,7,8,'active','pending',false)");
                statement.execute("INSERT INTO zsjos_media_account VALUES(1,1,40,10,99,99,NULL),(2,1,40,11,7,7,NULL),(3,1,40,12,7,7,NULL),(4,1,40,13,7,7,NULL),(5,1,40,14,7,7,NULL),(6,1,40,NULL,7,8,NULL),(7,2,40,NULL,7,8,NULL)");
            }
            doAnswer(invocation -> {
                AbstractWrapper<?, ?, ?> wrapper = invocation.getArgument(0);
                var matcher = Pattern.compile("#\\{ew.paramNameValuePairs.([^}]+)}").matcher(wrapper.getCustomSqlSegment());
                List<Object> values = new ArrayList<>();
                while (matcher.find()) values.add(wrapper.getParamNameValuePairs().get(matcher.group(1)));
                // H2 MySQL mode lacks bit literals; preserve the predicate using its boolean literal.
                String sql = matcher.replaceAll("?").replace("b'0'", "false");
                try (var statement = connection.prepareStatement("SELECT id FROM zsjos_media_account " + sql)) {
                    for (int i = 0; i < values.size(); i++) statement.setObject(i + 1, values.get(i));
                    try (var rows = statement.executeQuery()) {
                        List<MediaAccountDO> result = new ArrayList<>();
                        while (rows.next()) result.add(new MediaAccountDO().setId(rows.getLong(1)));
                        return result;
                    }
                }
            }).when(mapper).selectList(any(Wrapper.class));
            assertEquals(List.of(6L, 1L), mapper.selectMaterialRecommendationCandidates(null, 7L, 1L, false)
                    .stream().map(MediaAccountDO::getId).toList());
            assertEquals(List.of(6L, 5L, 4L, 3L, 2L, 1L), mapper.selectMaterialRecommendationCandidates(null, 7L, 1L, true)
                    .stream().map(MediaAccountDO::getId).toList());
        }
    }
}
