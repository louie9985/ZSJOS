package cn.iocoder.yudao.module.zsjos.service.order;

import cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ZsjosProductAttrSaveReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.appeal.LeadAppealSubmitReqVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.lead.vo.submission.LeadAttachmentReqVO;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CollectionElementValidationTest {
    @Test void validatesNestedCollectionsAndAttachmentElements() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var product = new ZsjosProductAttrSaveReqVO(); product.setSpuId(1L);
            var attr = new ZsjosProductAttrSaveReqVO.Attr(); attr.setAttrName("规格"); attr.setRequired(true); attr.setSort(0);
            var value = new ZsjosProductAttrSaveReqVO.Value(); value.setValue("x"); value.setSort(0);
            attr.setValues(List.of(value)); product.setAttrs(List.of(attr));
            assertTrue(validator.validate(product).stream().anyMatch(item -> item.getPropertyPath().toString().equals("attrs[0].values[0].label")));
            value.setLabel("选项"); assertTrue(validator.validate(product).isEmpty());
            var appeal = new LeadAppealSubmitReqVO(); appeal.setAttachments(List.of(new LeadAttachmentReqVO()));
            assertTrue(validator.validate(appeal).stream().anyMatch(item -> item.getPropertyPath().toString().equals("attachments[0].infraFileId")));
        }
    }
}
