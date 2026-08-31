package cn.iocoder.yudao.module.system.dal.mysql.notice;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.system.dal.dataobject.notice.NoticeDO;
import cn.iocoder.yudao.module.system.dal.dataobject.notice.NoticeReadDO;
import cn.iocoder.yudao.module.system.enums.notice.NoticePublishStatusEnum;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@Import(NoticeMapperTest.TenantInterceptorTestConfiguration.class)
class NoticeMapperTest extends BaseDbUnitTest {

    @Resource private NoticeMapper noticeMapper;
    @Resource private NoticeReadMapper readMapper;

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldIsolatePublishedNoticesAndReadStateByTenant() {
        TenantContextHolder.setTenantId(1L);
        NoticeDO tenantOne = publishedNotice("租户一公告");
        noticeMapper.insert(tenantOne);
        NoticeReadDO tenantOneRead = new NoticeReadDO();
        tenantOneRead.setNoticeId(tenantOne.getId());
        tenantOneRead.setUserId(7L);
        tenantOneRead.setReadTime(LocalDateTime.now());
        readMapper.insert(tenantOneRead);

        TenantContextHolder.setTenantId(2L);
        assertNull(noticeMapper.selectById(tenantOne.getId()));
        assertEquals(0, noticeMapper.selectPublishedPage(page()).getTotal());
        assertNull(readMapper.selectByNoticeIdAndUserId(tenantOne.getId(), 7L));
        NoticeDO tenantTwo = publishedNotice("租户二公告");
        noticeMapper.insert(tenantTwo);

        assertEquals(1, noticeMapper.selectPublishedPage(page()).getTotal());
        assertEquals(tenantTwo.getId(), noticeMapper.selectPublishedPage(page()).getList().get(0).getId());

        TenantContextHolder.setTenantId(1L);
        assertEquals(tenantOne.getId(), noticeMapper.selectPublishedPage(page()).getList().get(0).getId());
        assertNotNull(readMapper.selectByNoticeIdAndUserId(tenantOne.getId(), 7L));
    }

    private NoticeDO publishedNotice(String title) {
        NoticeDO notice = new NoticeDO();
        notice.setTitle(title);
        notice.setType(2);
        notice.setContent("<p>正文</p>");
        notice.setStatus(0);
        notice.setPublishStatus(NoticePublishStatusEnum.PUBLISHED.getStatus());
        notice.setPublishTime(LocalDateTime.now());
        return notice;
    }

    private PageParam page() {
        PageParam page = new PageParam();
        page.setPageNo(1);
        page.setPageSize(20);
        return page;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TenantInterceptorTestConfiguration {

        @Bean
        static BeanPostProcessor tenantMybatisPlusInterceptorPostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessBeforeInitialization(Object bean, String beanName) {
                    if (bean instanceof MybatisPlusInterceptor interceptor) {
                        MyBatisUtils.addInterceptor(interceptor, new TenantLineInnerInterceptor(
                                new TenantDatabaseInterceptor(new TenantProperties())), 0);
                    }
                    return bean;
                }
            };
        }

    }

}
