package cn.iocoder.yudao.module.zsjos.service.examcollection;

import cn.iocoder.yudao.module.zsjos.service.studentinfo.StudentInfoFields;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Field;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ExamCollectionFieldsTest {
    @Test void emptySelectionGeneratesOnlyThreeRequiredIdentityFields() {
        var result = ExamCollectionFields.snapshot(new StudentInfoFields().presets(), List.of());
        assertEquals(ExamCollectionFields.FIXED_KEYS, result.stream().map(Field::getKey).toList());
        assertTrue(result.stream().allMatch(f -> f.getRequired() && f.getEnabled()));
    }
    @Test void onlySelectedPublishedMaterialsAreIncludedAndRequired() {
        var published = new StudentInfoFields().presets();
        var result = ExamCollectionFields.snapshot(published, List.of("portrait", "certificate_shipping_address"));
        assertEquals(5, result.size());
        assertTrue(result.stream().allMatch(Field::getRequired));
        assertFalse(result.stream().anyMatch(f -> "identity_images".equals(f.getKey())));
        assertFalse(published.stream().filter(f -> "portrait".equals(f.getKey())).findFirst().orElseThrow().getRequired());
        result.getFirst().setLabel("changed snapshot");
        assertEquals("姓名", published.getFirst().getLabel());
    }
    @Test void rejectsGiftTextbookDisabledUnknownAndDuplicateSelections() {
        var published = new StudentInfoFields().presets();
        published.stream().filter(f -> "portrait".equals(f.getKey())).forEach(f -> f.setEnabled(false));
        for (var selection : List.of(List.of("gift_shipping_address"), List.of("textbook_shipping_address"),
                List.of("portrait"), List.of("unknown"), List.of("identity_images", "identity_images"))) {
            assertThrows(ServiceException.class, () -> ExamCollectionFields.snapshot(published, selection));
        }
    }
    @Test void uploadLimitsAreSnapshottedAndDefaultToTenMbOneFile() {
        var published = new StudentInfoFields().presets();
        var defaults = ExamCollectionFields.snapshot(published, List.of("portrait"), null).getLast();
        assertEquals(10, defaults.getMaxSizeMb());
        assertEquals(1, defaults.getMaxImages());
        var limit = new cn.iocoder.yudao.module.zsjos.controller.admin.examcollection.vo.ExamCollectionVO.UploadLimit();
        limit.setMaxSizeMb(20); limit.setMaxImages(3);
        var configured = ExamCollectionFields.snapshot(published, List.of("portrait"), java.util.Map.of("portrait", limit)).getLast();
        assertEquals(20, configured.getMaxSizeMb());
        assertEquals(3, configured.getMaxImages());
        assertThrows(ServiceException.class, () -> ExamCollectionFields.snapshot(published, List.of(), java.util.Map.of("portrait", limit)));
        limit.setMaxImages(0);
        assertThrows(ServiceException.class, () -> ExamCollectionFields.snapshot(published, List.of("portrait"), java.util.Map.of("portrait", limit)));
        var identity = ExamCollectionFields.snapshot(published, List.of("identity_images"), null).getLast();
        assertEquals(2, identity.getMaxImages());
        assertEquals(10, identity.getMaxSizeMb());
    }
}
