package cn.iocoder.yudao.module.system.dal.mysql.notice;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import cn.iocoder.yudao.framework.tenant.config.TenantProperties;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.db.TenantDatabaseInterceptor;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.system.dal.dataobject.notice.NoticeAttachmentDO;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Import(NoticeMapperTest.TenantInterceptorTestConfiguration.class)
class NoticeMapperTest extends BaseDbUnitTest {

    @Resource private NoticeMapper noticeMapper;
    @Resource private NoticeReadMapper readMapper;
    @Resource private NoticeRecipientMapper recipients;
    @Resource private NoticeReadStatisticsMapper statistics;
    @Resource private NoticeAttachmentMapper attachments;

    @Test
    void shouldScopeDeletedAttachmentLookupAndRestoreToTenantAndNotice() {
        TenantContextHolder.setTenantId(1L);
        NoticeDO notice = publishedNotice("附件隔离");
        noticeMapper.insert(notice);
        NoticeAttachmentDO attachment = new NoticeAttachmentDO();
        attachment.setNoticeId(notice.getId());
        attachment.setInfraFileId(101L);
        attachment.setFileName("制度.pdf");
        attachment.setFileSize(1024L);
        attachment.setSort(0);
        attachments.insert(attachment);
        attachments.deleteByNoticeIds(List.of(notice.getId()));
        NoticeAttachmentDO deleted = attachments.selectListIncludingDeletedByNoticeId(notice.getId()).get(0);
        assertTrue(deleted.getDeleted());

        TenantContextHolder.setTenantId(2L);
        assertTrue(attachments.selectListIncludingDeletedByNoticeId(notice.getId()).isEmpty());
        assertEquals(0, attachments.restoreDeleted(deleted));

        TenantContextHolder.setTenantId(1L);
        NoticeDO otherNotice = publishedNotice("另一公告");
        noticeMapper.insert(otherNotice);
        deleted.setNoticeId(otherNotice.getId());
        assertEquals(0, attachments.restoreDeleted(deleted));
        deleted.setNoticeId(notice.getId());
        assertEquals(1, attachments.restoreDeleted(deleted));
        assertEquals(0, attachments.restoreDeleted(deleted));
        assertEquals(attachment.getId(), attachments.selectListByNoticeId(notice.getId()).get(0).getId());
        assertTrue(attachments.selectListByNoticeId(otherNotice.getId()).isEmpty());
    }

    @Test void readingStatisticsWorkWithTenantInterceptorAndRejectOtherTenantRows() {
        TenantContextHolder.setTenantId(1L);
        NoticeDO notice = publishedNotice("统计隔离"); noticeMapper.insert(notice);
        var recipient = new cn.iocoder.yudao.module.system.dal.dataobject.notice.NoticeRecipientDO();
        recipient.setNoticeId(notice.getId()); recipient.setUserId(7L); recipient.setProfileSnapshotComplete(true);
        recipient.setUserNameSnapshot("发布姓名"); recipient.setDeptIdSnapshot(10L); recipient.setDeptNameSnapshot("发布部门");
        recipients.insert(recipient);
        var q = new cn.iocoder.yudao.module.system.controller.admin.notice.vo.NoticeReadPageReqVO(); q.setId(notice.getId());
        assertEquals(1L, statistics.count(1L, q));
        assertEquals("发布姓名", statistics.page(1L, q, 0).getFirst().getUserName());
        assertEquals("发布部门", statistics.departments(1L, q).getFirst().getName());
        q.setScope("EXTRA"); assertEquals(0L, statistics.count(1L, q));
        TenantContextHolder.setTenantId(2L); q.setScope("EXPECTED");
        assertNull(noticeMapper.selectById(notice.getId()));
        assertEquals(0L, statistics.count(2L, q));
        assertTrue(statistics.page(2L, q, 0).isEmpty());
        assertTrue(statistics.departments(2L, q).isEmpty());
    }

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

    @Test
    void shouldQueryPublishedCursorWithSnapshotTime() {
        TenantContextHolder.setTenantId(1L);
        NoticeDO notice = publishedNotice("游标公告");
        notice.setHighlightUntil(LocalDateTime.now().plusHours(1));
        noticeMapper.insert(notice);

        List<NoticeDO> rows = noticeMapper.selectPublishedCursor(7L, LocalDateTime.now(),
                null, null, null, 20, null, null, null, null, null);

        assertEquals(List.of(notice.getId()), rows.stream().map(NoticeDO::getId).toList());
    }

    @Test
    void sourceSearchMustKeepAudienceAndTenantBoundariesForBothPaginationModes() {
        TenantContextHolder.setTenantId(1L);
        NoticeDO visible = publishedNotice("公开标题"); visible.setSourceDeptName("考务部"); visible.setPublisherName("发布人员"); noticeMapper.insert(visible);
        NoticeDO hidden = publishedNotice("定向标题"); hidden.setSourceDeptName("考务部"); hidden.setPublisherName("发布人员"); hidden.setAudienceType("TARGET"); noticeMapper.insert(hidden);
        NoticeDO draft = publishedNotice("未发布"); draft.setSourceDeptName("考务部"); draft.setPublishStatus("DRAFT"); noticeMapper.insert(draft);
        TenantContextHolder.setTenantId(2L);
        NoticeDO other = publishedNotice("其他租户"); other.setSourceDeptName("考务部"); noticeMapper.insert(other);
        TenantContextHolder.setTenantId(1L);
        var query = new cn.iocoder.yudao.module.system.controller.admin.notice.vo.NoticeMyPageReqVO(); query.setKeyword("考务");
        assertEquals(List.of(visible.getId()), noticeMapper.selectPublishedPage(query, 7L).getList().stream().map(NoticeDO::getId).toList());
        assertEquals(List.of(visible.getId()), noticeMapper.selectPublishedCursor(7L, LocalDateTime.now(), null, null, null, 20, "考务", null, null, null, null).stream().map(NoticeDO::getId).toList());
        query.setKeyword("发布人员"); assertEquals(1L, noticeMapper.selectPublishedPage(query, 7L).getTotal());
        query.setKeyword("不存在"); assertEquals(0L, noticeMapper.selectPublishedPage(query, 7L).getTotal());
        var admin = new cn.iocoder.yudao.module.system.controller.admin.notice.vo.NoticePageReqVO(); admin.setTitle("考务");
        assertEquals(3L, noticeMapper.selectPage(admin).getTotal());
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
