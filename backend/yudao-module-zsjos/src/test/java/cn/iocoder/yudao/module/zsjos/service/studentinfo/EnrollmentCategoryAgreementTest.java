package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductMapper;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.EnrollmentAgreementMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnrollmentCategoryAgreementTest {
    @InjectMocks EnrollmentAgreementService service;
    @Mock EnrollmentAgreementMapper templates;
    @Mock ZsjosProductMapper products;
    @Mock cn.iocoder.yudao.module.zsjos.service.product.ZsjosProductService catalog;
    @Mock SalesOrderItemMapper items;
    @Mock SalesOrderMapper orders;

    private void product(long id, String name) {
        var category = new ZsjosProductDO(); category.setId(id); category.setName(name); category.setProductRef("spu"+id); category.setCategoryId(1L);
        when(products.selectById(id)).thenReturn(category);
    }

    @Test void bindsBySpuAcrossSkusAndRendersFrozenNameWithIndividualFee() {
        product(11, "末级甲");
        var template = new EnrollmentAgreementDO(); template.setPublishedVersion(2L);
        template.setPublishedBody("甲 {{课程名称}} {{实际费用}}"); when(templates.selectById(11L)).thenReturn(template);
        for (long itemId : new long[]{31L, 32L}) {
            var form = new StudentInfoFormDO(); form.setProductId(11L); form.setOrderItemId(itemId); form.setPersonId(4L);
            service.bind(form);
            var item = new SalesOrderItemDO(); item.setOrderId(3L); item.setPayableAmount(BigDecimal.valueOf(itemId));
            item.setProductSnapshot("{\"skuName\":\"规格" + itemId + "\"}"); when(items.selectById(itemId)).thenReturn(item);
            var order = new SalesOrderDO(); order.setPersonId(4L); order.setStudentName("示例"); order.setStudentMobile("示例");
            when(orders.selectById(3L)).thenReturn(order);
            assertEquals("甲 末级甲 " + itemId,service.render(form, java.time.LocalDate.of(2026, 11, 19)));
        }
        product(12, "末级乙");
        var other = new StudentInfoFormDO(); other.setProductId(12L);
        assertThrows(ServiceException.class, () -> service.bind(other));
        var draft = new EnrollmentAgreementDO(); draft.setPublishedVersion(0L); when(templates.selectById(12L)).thenReturn(draft);
        assertThrows(ServiceException.class, () -> service.bind(other));
        draft.setPublishedVersion(1L); draft.setPublishedBody("乙"); service.bind(other);
        assertEquals("乙",other.getAgreementTemplate()); assertEquals("末级乙",other.getAgreementProductName());
    }

    @Test void rejectsDisabledProductAndNeverPublishesStarterForAnUnconfiguredLeaf() {
        doThrow(new IllegalArgumentException("disabled")).when(catalog).validateEnabledProducts(java.util.List.of("off"));
        assertThrows(IllegalArgumentException.class, () -> service.detail("off"));
        var enabled = new ZsjosProductDO(); enabled.setId(11L);
        when(products.selectByProductRef("spu11")).thenReturn(enabled); when(templates.starterBody()).thenReturn("起草范本");
        assertEquals(new EnrollmentAgreementService.Template(0,0,"起草范本"), service.detail("spu11"));
        assertThrows(ServiceException.class, () -> service.save(new EnrollmentAgreementService.Change("spu11",0L,"起草范本"),true));
    }
}
