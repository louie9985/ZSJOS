package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.StudentInfoFormDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.StudentInfoFormMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class StudentInfoCurrentArchiveTest {
    @Test void actualMapperSelectsOnlyLatestSubmissionAndNeverPromotesReuseOrDraft() throws Exception {
        var data=new UnpooledDataSource("org.h2.Driver","jdbc:h2:mem:archive_"+System.nanoTime()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("archive-test",new JdbcTransactionFactory(),data));config.addMapper(StudentInfoFormMapper.class);
        var factory=new MybatisSqlSessionFactoryBuilder().build(config);
        var columns=new ArrayList<String>();columns.add("id BIGINT PRIMARY KEY");
        for(var field:TableInfoHelper.getTableInfo(StudentInfoFormDO.class).getFieldList()) {
            String type=field.getColumn().equals("deleted")?"INT DEFAULT 0":field.getColumn().equals("submitted_at")?"TIMESTAMP":"VARCHAR(4000)";
            columns.add(field.getColumn()+" "+type);
        }
        try(var connection=data.getConnection();var statement=connection.createStatement()) {
            statement.execute("CREATE TABLE zsjos_student_info_form("+String.join(",",columns)+")");
            statement.execute("INSERT INTO zsjos_student_info_form(id,person_id,status,submit_source,submitted_at) VALUES (1,'3','SUBMITTED','public','2026-01-01 00:00:00'),(2,'3','SUBMITTED','reuse','2026-02-01 00:00:00'),(3,'3','DRAFT',NULL,'2026-03-01 00:00:00'),(4,'99','SUBMITTED','public','2026-04-01 00:00:00')");
            try(var session=factory.openSession(true)) {
                var mapper=session.getMapper(StudentInfoFormMapper.class);
                assertEquals(1L,mapper.currentArchive(3L).getId());assertNull(mapper.currentArchive(100L));
                statement.execute("INSERT INTO zsjos_student_info_form(id,person_id,status,submit_source,submitted_at) VALUES (5,'3','SUBMITTED','public','2026-05-01 00:00:00')");
                session.clearCache();assertEquals(5L,mapper.currentArchive(3L).getId());
                assertEquals(1L,mapper.selectById(1L).getId(),"The old order snapshot is retained, but is no longer the customer archive");
            }
        }
    }
}
