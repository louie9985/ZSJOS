package cn.iocoder.yudao.module.zsjos.service.personalcalendar;
import cn.iocoder.yudao.module.zsjos.dal.mysql.personalcalendar.PersonalCalendarEventMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.personalcalendar.PersonalCalendarEventDO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class PersonalCalendarColorMapperTest {
 @Test void omittedColorCannotOverwriteAnInterveningWriteAndExplicitEmptyClears() throws Exception {
  var source=new DriverManagerDataSource("jdbc:h2:mem:color"+UUID.randomUUID()+";MODE=MySQL","sa","");
  try(var keepAlive=source.getConnection()) {
   var jdbc=new JdbcTemplate(source);
   jdbc.execute("CREATE TABLE zsjos_personal_calendar_event(id bigint primary key,owner_user_id bigint,title varchar(100),background_color varchar(7),deleted boolean default false,update_time timestamp,updater varchar(64))");
   jdbc.execute("INSERT INTO zsjos_personal_calendar_event(id,owner_user_id,title,background_color) VALUES(1,7,'before','#112233')");
   var config=new MybatisConfiguration();config.setEnvironment(new Environment("test",new JdbcTransactionFactory(),source));config.addMapper(PersonalCalendarEventMapper.class);
   var factory=new MybatisSqlSessionFactoryBuilder().build(config);
   try(var session=factory.openSession(true)) {
    var mapper=session.getMapper(PersonalCalendarEventMapper.class);
    var oldClient=new PersonalCalendarEventDO().setId(1L).setTitle("updated");
    jdbc.update("UPDATE zsjos_personal_calendar_event SET background_color='#445566' WHERE id=1");
    assertEquals(1,mapper.updateOwned(oldClient,7L,false));
    assertEquals("#445566",jdbc.queryForObject("SELECT background_color FROM zsjos_personal_calendar_event WHERE id=1",String.class));
    assertEquals(1,mapper.updateOwned(oldClient,7L,true));
    assertNull(jdbc.queryForObject("SELECT background_color FROM zsjos_personal_calendar_event WHERE id=1",String.class));
    assertEquals(0,mapper.updateOwned(oldClient,8L,true));
   }
  }
 }
}
