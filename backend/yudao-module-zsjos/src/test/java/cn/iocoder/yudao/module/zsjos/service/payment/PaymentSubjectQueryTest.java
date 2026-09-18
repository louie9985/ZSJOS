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
import org.junit.jupiter.api.AfterEach;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductDO;
import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentSubjectQueryTest {
    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void productPaginationFiltersBeforePagingAndKeepsUnconfiguredProductsWithinTenant() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:payment" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE zsjos_product(id BIGINT PRIMARY KEY, name VARCHAR(50), sort INT, tenant_id BIGINT, deleted INT, status INT)");
        jdbc.execute("CREATE TABLE zsjos_payment_subject(id BIGINT PRIMARY KEY, subject_code VARCHAR(50), subject_name VARCHAR(50), tenant_id BIGINT, deleted INT)");
        jdbc.execute("CREATE TABLE zsjos_product_payment_subject(id BIGINT PRIMARY KEY, product_id BIGINT, payment_subject_id BIGINT, update_time TIMESTAMP, tenant_id BIGINT, deleted INT)");
        jdbc.execute("INSERT INTO zsjos_product VALUES(1,'Alpha',0,1,0,0),(2,'Beta',0,1,0,0),(3,'Other tenant',0,2,0,0),(4,'Deleted',0,1,1,0),(5,'Disabled configured',0,1,0,1),(6,'Disabled unconfigured',0,1,0,1)");
        jdbc.execute("INSERT INTO zsjos_payment_subject VALUES(10,'A','Active',1,0),(20,'B','Other',2,0)");
        jdbc.execute("INSERT INTO zsjos_product_payment_subject VALUES(1,1,10,CURRENT_TIMESTAMP,1,0),(2,2,10,CURRENT_TIMESTAMP,1,1),(3,2,20,CURRENT_TIMESTAMP,2,0),(4,5,10,CURRENT_TIMESTAMP,1,0)");
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
            req.setProductName("Disabled"); req.setPaymentSubjectId(null);
            assertEquals(0L, mapper.selectProductPage(MyBatisUtils.buildPage(req), req, 1L).getTotal());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM zsjos_product_payment_subject WHERE product_id = 5 AND deleted = 0", Integer.class));
            jdbc.update("UPDATE zsjos_product SET status = 0 WHERE id = 5");
            session.clearCache();
            var restored = mapper.selectProductPage(MyBatisUtils.buildPage(req), req, 1L);
            assertEquals(1L, restored.getTotal());
            assertEquals(10L, restored.getRecords().getFirst().getPaymentSubjectId());
            req.setProductName(null); req.setPaymentSubjectId(null);
            page = mapper.selectProductPage(MyBatisUtils.buildPage(req), req, 2L);
            assertEquals(1L, page.getTotal()); assertEquals(3L, page.getRecords().getFirst().getProductId());
        }
    }

    @Test
    void repeatedAndDuplicateBatchConfigurationUpdatesExistingRelationWithoutSoftDelete() {
        var mapper = mock(ProductPaymentSubjectMapper.class);
        TenantContextHolder.setTenantId(1L);
        var products = mock(ZsjosProductMapper.class);
        var enabled = new ZsjosProductDO(); enabled.setId(2L); enabled.setStatus(0);
        when(products.selectByIdForUpdate(2L, 1L)).thenReturn(enabled);
        var subjects = mock(PaymentSubjectService.class);
        var service = new ProductPaymentSubjectServiceImpl();
        ReflectionTestUtils.setField(service, "productPaymentSubjectMapper", mapper);
        ReflectionTestUtils.setField(service, "paymentSubjectService", subjects);
        ReflectionTestUtils.setField(service, "productMapper", products);
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
    @Test
    void rejectsDisabledAndMissingCoursesBeforeAnyWriteAndPreservesHistory() {
        TenantContextHolder.setTenantId(1L);
        var mapper = mock(ProductPaymentSubjectMapper.class);
        var products = mock(ZsjosProductMapper.class);
        var subjects = mock(PaymentSubjectService.class);
        var service = new ProductPaymentSubjectServiceImpl();
        ReflectionTestUtils.setField(service, "productPaymentSubjectMapper", mapper);
        ReflectionTestUtils.setField(service, "productMapper", products);
        ReflectionTestUtils.setField(service, "paymentSubjectService", subjects);
        when(subjects.getPaymentSubject(9L)).thenReturn(new PaymentSubjectDO());
        var enabled = new ZsjosProductDO(); enabled.setStatus(0);
        var disabled = new ZsjosProductDO(); disabled.setStatus(1);
        when(products.selectByIdForUpdate(1L, 1L)).thenReturn(enabled);
        when(products.selectByIdForUpdate(2L, 1L)).thenReturn(disabled);
        assertEquals(PRODUCT_NOT_ENABLE.getCode(), assertThrows(ServiceException.class,
                () -> service.configureProductPaymentSubject(2L, 9L)).getCode());
        assertEquals(PRODUCT_NOT_EXISTS.getCode(), assertThrows(ServiceException.class,
                () -> service.configureProductPaymentSubject(3L, 9L)).getCode());
        assertEquals(PRODUCT_NOT_ENABLE.getCode(), assertThrows(ServiceException.class,
                () -> service.batchConfigureProductPaymentSubject(List.of(1L, 2L), 9L)).getCode());
        assertEquals(PRODUCT_NOT_EXISTS.getCode(), assertThrows(ServiceException.class,
                () -> service.batchConfigureProductPaymentSubject(List.of(1L, 3L), 9L)).getCode());
        verifyNoInteractions(mapper);
        when(mapper.selectByProductId(2L)).thenReturn(ProductPaymentSubjectDO.builder()
                .productId(2L).paymentSubjectId(8L).build());
        assertEquals(8L, service.getPaymentSubjectIdByProductId(2L));
    }

    @Test
    void enabledCourseCanCreateConfiguration() {
        TenantContextHolder.setTenantId(1L);
        var mapper = mock(ProductPaymentSubjectMapper.class);
        var products = mock(ZsjosProductMapper.class);
        var subjects = mock(PaymentSubjectService.class);
        var service = new ProductPaymentSubjectServiceImpl();
        ReflectionTestUtils.setField(service, "productPaymentSubjectMapper", mapper);
        ReflectionTestUtils.setField(service, "productMapper", products);
        ReflectionTestUtils.setField(service, "paymentSubjectService", subjects);
        var enabled = new ZsjosProductDO(); enabled.setStatus(0);
        when(products.selectByIdForUpdate(1L, 1L)).thenReturn(enabled);
        when(subjects.getPaymentSubject(9L)).thenReturn(new PaymentSubjectDO());
        service.configureProductPaymentSubject(1L, 9L);
        verify(mapper).insert(argThat((ProductPaymentSubjectDO row) ->
                row.getProductId() == 1L && row.getPaymentSubjectId() == 9L));
    }

}
