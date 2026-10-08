package cn.iocoder.yudao.module.zsjos.dal.mysql.lead;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.QueryWrapperX;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MyStudentPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.lead.PersonDO;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Executes production page predicates and counts on isolated H2; no shared database is touched. */
class PersonMapperMediaOperatorTest {
    @Test
    @SuppressWarnings("unchecked")
    void operatorMatchesVisibleServicesBeforePaginationInEveryReadScope() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:operators" + UUID.randomUUID() + ";MODE=MySQL", "sa", "")) {
            try (var sql = connection.createStatement()) {
                sql.execute("CREATE TABLE zsjos_person(id bigint,tenant_id bigint,deleted boolean,in_service_period boolean)");
                sql.execute("CREATE TABLE zsjos_service_relation(id bigint,person_id bigint,tenant_id bigint,deleted boolean,class_id bigint,status varchar,acceptance_status varchar,owner_user_id bigint,content_director_user_id bigint,career_planner_user_id bigint,operator_user_id bigint)");
                sql.execute("CREATE TABLE zsjos_media_account(create_service_relation_id bigint,id bigint,student_person_id bigint,tenant_id bigint,deleted boolean,director_user_id bigint,owner_operator_user_id bigint)");
                sql.execute("CREATE TABLE zsjos_positioning_card(service_relation_id bigint,student_person_id bigint,tenant_id bigint,deleted boolean)");
                sql.execute("CREATE TABLE zsjos_student_positioning_interview(service_relation_id bigint,student_person_id bigint,tenant_id bigint,deleted boolean)");
                sql.execute("INSERT INTO zsjos_person VALUES(1,1,false,true),(2,1,false,true),(3,1,false,true),(4,1,false,true),(5,1,false,true),(6,1,false,true),(7,1,false,false),(8,2,false,true)");
                // Person 1 has duplicate matching services; person 3's target operator is on an invisible service.
                sql.execute("INSERT INTO zsjos_service_relation VALUES"
                        + "(11,1,1,false,1,'active','accepted',99,10,null,20),(12,1,1,false,1,'active','accepted',99,10,null,20),"
                        + "(21,2,1,false,1,'completed','accepted',10,10,null,20),"
                        + "(31,3,1,false,1,'active','accepted',99,10,null,21),(32,3,1,false,1,'active','accepted',99,99,null,20),"
                        + "(41,4,1,false,1,'active','accepted',99,10,null,21),(42,4,2,false,1,'active','accepted',99,10,null,20),"
                        + "(51,5,1,false,1,'active','accepted',99,10,null,21),(52,5,1,true,1,'active','accepted',99,10,null,20),"
                        + "(61,6,1,false,1,'active','accepted',10,99,null,20),"
                        + "(71,7,1,false,2,'active','accepted',99,10,null,20),(81,8,2,false,1,'active','accepted',99,10,null,20)");
                sql.execute("INSERT INTO zsjos_media_account VALUES(NULL,61,6,1,false,10,99)");
            }
            PersonMapper mapper = mock(PersonMapper.class, CALLS_REAL_METHODS);
            doAnswer(call -> execute(connection, call.getArgument(0), call.getArgument(1)))
                    .when(mapper).selectPage(any(MyStudentPageReqVO.class), any(QueryWrapperX.class));
            var req = new MyStudentPageReqVO(); req.setOperatorUserId(20L); req.setPageSize(1);
            req.setInServicePeriod(true);
            var first = mapper.selectMediaStudentPage(req, 10L, null);
            assertEquals(2L, first.getTotal()); assertIds(first, 1L); // no account needed for person 1
            req.setPageNo(2); assertIds(mapper.selectMediaStudentPage(req, 10L, null), 6L);
            req.setPageNo(1); req.setPageSize(20);
            assertIds(mapper.selectMediaStudentPage(req, 10L, List.of(6L)), 6L);
            assertIds(mapper.selectMediaStudentPage(req, 10L, List.of()));
            assertIds(mapper.selectMediaStudentPage(req, 10L, null, true), 1L, 2L, 6L);
            assertIds(mapper.selectAllMediaStudentPage(req, null), 1L, 2L, 3L, 6L);
            assertIds(mapper.selectTenantReadStudentPage(req, null, true), 1L, 2L, 3L, 6L);
            req.setServiceStatus("completed"); assertIds(mapper.selectAllMediaStudentPage(req, null), 2L);
            req.setServiceStatus(null); req.setInServicePeriod(null); req.setClassId(2L);
            assertIds(mapper.selectAllMediaStudentPage(req, null), 7L);
            req.setClassId(null); req.setOperatorUserId(null);
            assertIds(mapper.selectAllMediaStudentPage(req, null), 1L, 2L, 3L, 4L, 5L, 6L, 7L);
        }
    }

    private static PageResult<PersonDO> execute(Connection connection, MyStudentPageReqVO req,
                                                QueryWrapperX<PersonDO> wrapper) throws Exception {
        String predicate = wrapper.getExpression().getNormal().getSqlSegment();
        var matcher = Pattern.compile("#\\{ew.paramNameValuePairs.([^}]+)}").matcher(predicate);
        var values = new ArrayList<Object>();
        while (matcher.find()) values.add(wrapper.getParamNameValuePairs().get(matcher.group(1)));
        // Only adapt H2's unsupported bit literal; production tenant correlation stays intact.
        String where = " FROM zsjos_person WHERE tenant_id=1 AND deleted=false AND " + matcher.replaceAll("?").replace("b'0'", "false");
        long total;
        try (var count = connection.prepareStatement("SELECT COUNT(*)" + where)) {
            for (int i = 0; i < values.size(); i++) count.setObject(i + 1, values.get(i));
            try (var rows = count.executeQuery()) { rows.next(); total = rows.getLong(1); }
        }
        var result = new ArrayList<PersonDO>();
        try (var select = connection.prepareStatement("SELECT id" + where + " ORDER BY id LIMIT ? OFFSET ?")) {
            for (int i = 0; i < values.size(); i++) select.setObject(i + 1, values.get(i));
            select.setInt(values.size() + 1, req.getPageSize()); select.setInt(values.size() + 2, (req.getPageNo() - 1) * req.getPageSize());
            try (var rows = select.executeQuery()) {
                while (rows.next()) { var person = new PersonDO(); person.setId(rows.getLong(1)); result.add(person); }
            }
        }
        return new PageResult<>(result, total);
    }

    private static void assertIds(PageResult<PersonDO> result, Long... ids) {
        assertEquals(List.of(ids), result.getList().stream().map(PersonDO::getId).toList());
    }
}
