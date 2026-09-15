package cn.iocoder.yudao.module.zsjos.service.payment;

import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.payment.vo.ProductPaymentSubjectPageReqVO;
import cn.iocoder.yudao.module.zsjos.dal.mysql.payment.ProductPaymentSubjectMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.payment.*;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentSubjectQueryTest {
    @Test
    void productPaginationFiltersBeforePagingAndKeepsUnconfiguredProductsWithinTenant() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:payment" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE zsjos_product(id BIGINT PRIMARY KEY, name VARCHAR(50), sort INT, tenant_id BIGINT, deleted INT)");
        jdbc.execute("CREATE TABLE zsjos_payment_subject(id BIGINT PRIMARY KEY, subject_code VARCHAR(50), subject_name VARCHAR(50), tenant_id BIGINT, deleted INT)");
        jdbc.execute("CREATE TABLE zsjos_product_payment_subject(id BIGINT PRIMARY KEY, product_id BIGINT, payment_subject_id BIGINT, update_time TIMESTAMP, tenant_id BIGINT, deleted INT)");
        jdbc.execute("INSERT INTO zsjos_product VALUES(1,'Alpha',0,1,0),(2,'Beta',0,1,0),(3,'Other tenant',0,2,0),(4,'Deleted',0,1,1)");
        jdbc.execute("INSERT INTO zsjos_payment_subject VALUES(10,'A','Active',1,0),(20,'B','Other',2,0)");
        jdbc.execute("INSERT INTO zsjos_product_payment_subject VALUES(1,1,10,CURRENT_TIMESTAMP,1,0),(2,2,10,CURRENT_TIMESTAMP,1,1),(3,2,20,CURRENT_TIMESTAMP,2,0)");
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        config.addInterceptor(interceptor);
        config.addMapper(ProductPaymentSubjectMapper.class);
        try (var session = new MybatisSqlSessionFactoryBuilder().build(config).openSession()) {
            var mapper = session.getMapper(ProductPaymentSubjectMapper.class);
            var req = new ProductPaymentSubjectPageReqVO(); req.setPageSize(1);
            var page = mapper.selectProductPage(MyBatisUtils.buildPage(req), req, 1L);
            assertEquals(2L, page.getTotal());
            assertEquals(2L, page.getRecords().getFirst().getProductId());
            assertNull(page.getRecords().getFirst().getPaymentSubjectId());
            req.setPageNo(2);
            page = mapper.selectProductPage(MyBatisUtils.buildPage(req), req, 1L);
            assertEquals("Active", page.getRecords().getFirst().getSubjectName());
            req.setPageNo(1); req.setPaymentSubjectId(10L);
            page = mapper.selectProductPage(MyBatisUtils.buildPage(req), req, 1L);
            assertEquals(1L, page.getTotal()); assertEquals(1L, page.getRecords().getFirst().getProductId());
            req.setProductName("Beta");
            assertEquals(0L, mapper.selectProductPage(MyBatisUtils.buildPage(req), req, 1L).getTotal());
            req.setProductName(null); req.setPaymentSubjectId(null);
            page = mapper.selectProductPage(MyBatisUtils.buildPage(req), req, 2L);
            assertEquals(1L, page.getTotal()); assertEquals(3L, page.getRecords().getFirst().getProductId());
        }
    }

    @Test
    void repeatedAndDuplicateBatchConfigurationUpdatesExistingRelationWithoutSoftDelete() {
        var mapper = mock(ProductPaymentSubjectMapper.class);
        var subjects = mock(PaymentSubjectService.class);
        var service = new ProductPaymentSubjectServiceImpl();
        ReflectionTestUtils.setField(service, "productPaymentSubjectMapper", mapper);
        ReflectionTestUtils.setField(service, "paymentSubjectService", subjects);
        when(subjects.getPaymentSubject(9L)).thenReturn(new PaymentSubjectDO());
        var relation = ProductPaymentSubjectDO.builder().id(1L).productId(2L).paymentSubjectId(8L).build();
        when(mapper.selectByProductId(2L)).thenReturn(relation);
        service.configureProductPaymentSubject(2L, 9L);
        service.configureProductPaymentSubject(2L, 9L);
        service.batchConfigureProductPaymentSubject(List.of(2L, 2L), 9L);
        verify(mapper, times(3)).updateById(relation);
        verify(mapper, never()).deleteByProductId(anyLong());
        verify(mapper, never()).insert(any(ProductPaymentSubjectDO.class));
        assertEquals(9L, relation.getPaymentSubjectId());
    }
}
