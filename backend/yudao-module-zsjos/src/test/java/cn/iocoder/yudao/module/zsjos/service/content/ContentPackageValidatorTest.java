package cn.iocoder.yudao.module.zsjos.service.content;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.content.ContentVersionDO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.CONTENT_REVIEW_PACKAGE_INCOMPLETE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentPackageValidatorTest {

    @Test
    void acceptsCompletePackageWithUploadedDeliverable() {
        ContentDO content = new ContentDO().setContentNo("CT-1").setTitle("标题");
        ContentVersionDO version = new ContentVersionDO().setScriptText("正文")
                .setCoverSnapshotJson("[{\"id\":1}]")
                .setDeliverableSnapshotJson("[{\"id\":2}]")
                .setLeadResourceUrl("https://example.com/lead")
                .setPlannedPublishAt(LocalDateTime.of(2026, 9, 8, 12, 0));

        assertDoesNotThrow(() -> ContentPackageValidator.validate(content, version));
    }

    @Test
    void reportsIncompletePackageBeforeReview() {
        ContentDO content = new ContentDO().setContentNo("CT-2");
        ContentVersionDO version = new ContentVersionDO().setCoverSnapshotJson("[]")
                .setDeliverableUrl("https://example.com/deliverable")
                .setLeadResourceUrl("http://example.com/lead");

        ServiceException error = assertThrows(ServiceException.class,
                () -> ContentPackageValidator.validate(content, version));

        assertEquals(CONTENT_REVIEW_PACKAGE_INCOMPLETE.getCode(), error.getCode());
    }
}
