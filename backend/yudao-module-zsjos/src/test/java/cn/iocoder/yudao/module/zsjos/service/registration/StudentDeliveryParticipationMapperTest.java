package cn.iocoder.yudao.module.zsjos.service.registration;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.QueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MyStudentPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.lead.PersonMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.registration.StudentServicePageSql;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Execute actual production query predicates; fixtures never touch the application database. */
class StudentDeliveryParticipationMapperTest {
    Connection db;
    @BeforeEach void setup() throws Exception {
        db=DriverManager.getConnection("jdbc:h2:mem:teaching"+UUID.randomUUID()+";MODE=MySQL","sa","");
        try(var s=db.createStatement()) {
            s.execute("CREATE TABLE zsjos_person(id BIGINT,tenant_id BIGINT,deleted BOOLEAN,in_service_period BOOLEAN)");
            s.execute("CREATE TABLE zsjos_service_relation(id BIGINT,person_id BIGINT,tenant_id BIGINT,deleted BOOLEAN,teaching_delivery INT,class_id BIGINT,status VARCHAR,acceptance_status VARCHAR,owner_user_id BIGINT,content_director_user_id BIGINT,career_planner_user_id BIGINT,operator_user_id BIGINT)");
            s.execute("CREATE TABLE zsjos_media_account(create_service_relation_id BIGINT,id BIGINT,student_person_id BIGINT,tenant_id BIGINT,deleted BOOLEAN,director_user_id BIGINT,owner_operator_user_id BIGINT)");
            s.execute("CREATE TABLE zsjos_positioning_card(service_relation_id BIGINT,student_person_id BIGINT,tenant_id BIGINT,deleted BOOLEAN)");
            s.execute("CREATE TABLE zsjos_student_positioning_interview(service_relation_id BIGINT,student_person_id BIGINT,tenant_id BIGINT,deleted BOOLEAN)");
            s.execute("INSERT INTO zsjos_person VALUES(1,1,false,true),(2,1,false,true),(3,1,false,true),(4,1,false,true),(5,1,false,true)");
            s.execute("INSERT INTO zsjos_service_relation VALUES"
                    +"(11,1,1,false,0,90,'active','accepted',7,7,NULL,8),"
                    +"(21,2,1,false,0,90,'active','accepted',7,7,NULL,8),"
                    +"(22,2,1,false,1,90,'active','accepted',7,7,NULL,8),"
                    +"(31,3,1,false,1,90,'active','accepted',NULL,NULL,NULL,NULL),"
                    +"(41,4,2,false,1,90,'active','accepted',7,7,NULL,8),"
                    +"(51,5,1,true,1,90,'active','accepted',7,7,NULL,8)");
        }
    }
    @AfterEach void close() throws Exception {db.close();}
    @SuppressWarnings("unchecked")
    PersonMapper mapper() {
        var m=mock(PersonMapper.class,CALLS_REAL_METHODS);
        doAnswer(call->execute(call.getArgument(0),call.getArgument(1)))
                .when(m).selectPage(any(MyStudentPageReqVO.class),any(QueryWrapperX.class));
        return m;
    }
    @Test void personalManagedAndFullLegacyPagesExcludeMediaOnlyBeforeCounting() {
        var m=mapper();var req=new MyStudentPageReqVO();req.setPageSize(1);
        assertEquals(1,m.selectMyStudentPage(req,7L,null).getTotal());
        assertEquals(2L,m.selectMyStudentPage(req,7L,null).getList().getFirst().getId());
        var managed=m.selectManagedStudentPage(req,Set.of(7L),null,90L,7L);
        assertEquals(2,managed.getTotal());assertEquals(2L,managed.getList().getFirst().getId());
        req.setPageNo(2);assertEquals(3L,m.selectManagedStudentPage(req,Set.of(7L),null,90L,7L).getList().getFirst().getId());
        req.setPageNo(1);req.setPageSize(20);
        assertEquals(List.of(2L,3L),ids(m.selectAllStudentPage(req,null)));
        assertEquals(List.of(2L,3L),ids(m.selectTenantReadStudentPage(req,null)));
        req.setReadScope("SELF");assertEquals(List.of(2L),ids(m.selectMyStudentPage(req,7L,null)));
    }
    @Test void mediaPagesKeepIndependentAndMixedStudentsInAllScopes() {
        var m=mapper();var req=new MyStudentPageReqVO();req.setPageSize(20);
        assertEquals(List.of(1L,2L),ids(m.selectMediaStudentPage(req,7L,null)));
        assertEquals(List.of(1L,2L),ids(m.selectMediaStudentPage(req,7L,null,true)));
        assertEquals(List.of(1L,2L),ids(m.selectAllMediaStudentPage(req,null)));
        assertEquals(List.of(1L,2L,3L),ids(m.selectTenantReadStudentPage(req,null,true)));
        req.setOperatorUserId(8L);
        assertEquals(List.of(1L,2L),ids(m.selectMediaStudentPage(req,7L,null)));
    }
    @Test void classCountExcludesIndependentServicesDespiteRetainedLegacyClassId() throws Exception {
        var select=DeliveryClassMapper.class.getMethod("countStudents",Long.class,Long.class).getAnnotation(Select.class);
        var sql=String.join(" ",select.value()).replace("#{tenantId}","1").replace("#{classId}","90").replace("b'0'","false");
        try(var s=db.createStatement();var result=s.executeQuery(sql)){result.next();assertEquals(2,result.getInt(1));}
    }
    @Test void classDeletionGuardStillCountsRetainedMediaReferences() throws Exception {
        var select=DeliveryClassMapper.class.getMethod("countAllRelations",Long.class,Long.class).getAnnotation(Select.class);
        var sql=String.join(" ",select.value()).replace("#{tenantId}","1").replace("#{classId}","90").replace("b'0'","false");
        try(var s=db.createStatement();var result=s.executeQuery(sql)){result.next();assertEquals(4,result.getInt(1));}
    }
    private List<Long> ids(PageResult<PersonDO> page) {return page.getList().stream().map(PersonDO::getId).toList();}
    private PageResult<PersonDO> execute(MyStudentPageReqVO req,QueryWrapperX<PersonDO> wrapper) throws Exception {
        var predicate=wrapper.getExpression().getNormal().getSqlSegment();
        var matcher=Pattern.compile("#\\{ew.paramNameValuePairs.([^}]+)}").matcher(predicate);
        var values=new ArrayList<Object>();while(matcher.find())values.add(wrapper.getParamNameValuePairs().get(matcher.group(1)));
        String from=" FROM zsjos_person WHERE tenant_id=1 AND deleted=false AND "+matcher.replaceAll("?").replace("b'0'","false");
        long count;try(var q=db.prepareStatement("SELECT COUNT(*)"+from)){bind(q,values);try(var rows=q.executeQuery()){rows.next();count=rows.getLong(1);}}
        var people=new ArrayList<PersonDO>();try(var q=db.prepareStatement("SELECT id"+from+" ORDER BY id LIMIT ? OFFSET ?")) {
            bind(q,values);q.setInt(values.size()+1,req.getPageSize());q.setInt(values.size()+2,(req.getPageNo()-1)*req.getPageSize());
            try(var rows=q.executeQuery()){while(rows.next()){var p=new PersonDO();p.setId(rows.getLong(1));people.add(p);}}
        }
        return new PageResult<>(people,count);
    }
    private void bind(PreparedStatement q,List<Object> values)throws Exception{for(int i=0;i<values.size();i++)q.setObject(i+1,values.get(i));}
}
