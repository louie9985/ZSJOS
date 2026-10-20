package cn.iocoder.yudao.module.zsjos.service.deliveryclass;

import cn.iocoder.yudao.module.zsjos.dal.mysql.deliveryclass.DeliveryClassMapper;
import cn.iocoder.yudao.module.zsjos.controller.admin.deliveryclass.vo.DeliveryClassPageReqVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import net.sf.jsqlparser.expression.LongValue;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Read-only against local MySQL; compares real mapper pagination with frozen snapshot projection. */
@EnabledIfEnvironmentVariable(named="ZSJOS_CLASS_MYSQL_VERIFY",matches="true")
class DeliveryClassFilterMysqlTest {
    @Test void paginationProjectsDatesAndLegacyCalls() throws Exception {
        var data = new UnpooledDataSource("com.mysql.cj.jdbc.Driver",System.getenv("ZSJOS_CLASS_MYSQL_URL"),
                System.getenv("ZSJOS_CLASS_MYSQL_USER"),System.getenv("ZSJOS_CLASS_MYSQL_PASSWORD"));
        var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("class-filter-read-only",new JdbcTransactionFactory(),data));
        var plugin=new MybatisPlusInterceptor();plugin.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler(){
            public LongValue getTenantId(){return new LongValue(1);}
        }));plugin.addInnerInterceptor(new PaginationInnerInterceptor(com.baomidou.mybatisplus.annotation.DbType.MYSQL));
        config.addInterceptor(plugin);config.addMapper(DeliveryClassMapper.class);
        try(var session=new MybatisSqlSessionFactoryBuilder().build(config).openSession()){
            session.getConnection().setReadOnly(true);var mapper=session.getMapper(DeliveryClassMapper.class);
            var req=new DeliveryClassPageReqVO();req.setPageSize(3);
            var all=mapper.selectList(mapper.classQuery(req,Set.of(),null,true,true));
            if (!all.isEmpty()) {
                Long owner = all.stream().filter(row -> row.getHomeroomUserId() != null)
                        .map(row -> row.getHomeroomUserId()).findFirst().orElse(999999999L);
                var own = all.stream().filter(row -> owner.equals(row.getHomeroomUserId())
                        && !Boolean.TRUE.equals(row.getSystemClass())).map(row -> row.getId()).toList();
                assertEquals(own.size(), mapper.selectDeliveryClassPage(req, Set.of(), owner, false, false).getTotal());
                var pendingAndOwn = all.stream().filter(row -> Boolean.TRUE.equals(row.getSystemClass())
                        || owner.equals(row.getHomeroomUserId())).map(row -> row.getId()).toList();
                assertEquals(pendingAndOwn.size(), mapper.selectDeliveryClassPage(req, Set.of(), owner, false, true).getTotal());
            }
            assertEquals(all.size(),mapper.selectDeliveryClassPage(req,Set.of(),null,true,true).getTotal());
            req.setExcludeSystemClass(true);
            var formal=all.stream().filter(row->!Boolean.TRUE.equals(row.getSystemClass())).toList();
            assertEquals(formal.size(),mapper.selectDeliveryClassPage(req,Set.of(),null,true,true).getTotal());
            var projects=DeliveryClassProjects.load(mapper,formal);
            var categoryIds=projects.values().stream().flatMap(List::stream).map(p->p.id()).distinct().toList();
            List<Long> probes=new ArrayList<>(categoryIds);probes.add(999999999L);
            for(Long project:probes){
                req.setProjectCategoryId(project);
                long expected=formal.stream().filter(row->projects.getOrDefault(row.getId(),List.of()).stream().anyMatch(p->p.id().equals(project))).count();
                assertEquals(expected,mapper.selectDeliveryClassPage(req,Set.of(),null,true,true).getTotal());
            }
            req.setProjectCategoryId(null);
            for(var range:List.of(List.of("2026-10-01","2026-10-31"),List.of("2026-11-01","2026-11-01"),List.of("1900-01-01","2099-12-31"))){
                req.setExamDateStart(LocalDate.parse(range.get(0)));req.setExamDateEnd(LocalDate.parse(range.get(1)));
                var expected=formal.stream().filter(row->{var date=DeliveryClassDates.fromSnapshot(row.getExamScheduleSnapshot());
                    return date!=null&&!date.end().isBefore(req.getExamDateStart())&&!date.start().isAfter(req.getExamDateEnd());}).map(row->row.getId()).toList();
                var page=mapper.selectDeliveryClassPage(req,Set.of(),null,true,true);
                assertEquals(expected.size(),page.getTotal());assertEquals(expected.stream().limit(3).toList(),page.getList().stream().map(row->row.getId()).toList());
            }
            req.setExamDateStart(null);req.setExamDateEnd(null);
            assertEquals(0,mapper.selectDeliveryClassPage(req,Set.of(),999999999L,false,false).getTotal());
            assertEquals(0,mapper.selectDeliveryClassPage(req,Set.of(999999999L),null,false,false).getTotal());
        }
    }
}
