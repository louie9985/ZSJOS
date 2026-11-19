package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.product.ZsjosProductCategoryDO;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.ZsjosProductCategoryMapper;
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
    @Mock ZsjosProductCategoryMapper categories;
    @Mock SalesOrderItemMapper items;
    @Mock SalesOrderMapper orders;

    private void category(long id, String name) {
        var category = new ZsjosProductCategoryDO(); category.setId(id); category.setName(name);
        when(categories.selectById(id)).thenReturn(category);
    }

    @Test void bindsByLeafAcrossSkusAndRendersFrozenNameWithIndividualFee() {
        category(11, "末级甲");
        var template = new EnrollmentAgreementDO(); template.setPublishedVersion(2L);
        template.setPublishedBody("甲 {{课程名称}} {{实际费用}}"); when(templates.selectById(11L)).thenReturn(template);
        for (long itemId : new long[]{31L, 32L}) {
            var form = new StudentInfoFormDO(); form.setCategoryPathSnapshot("[11,1]"); form.setOrderItemId(itemId); form.setPersonId(4L);
            service.bind(form);
            var item = new SalesOrderItemDO(); item.setOrderId(3L); item.setPayableAmount(BigDecimal.valueOf(itemId));
            item.setProductSnapshot("{\"skuName\":\"规格" + itemId + "\"}"); when(items.selectById(itemId)).thenReturn(item);
            var order = new SalesOrderDO(); order.setPersonId(4L); order.setStudentName("示例"); order.setStudentMobile("示例");
            when(orders.selectById(3L)).thenReturn(order);
            assertEquals("甲 末级甲 " + itemId,service.render(form, java.time.LocalDate.of(2026, 11, 19)));
        }
        category(12, "末级乙");
        var other = new StudentInfoFormDO(); other.setCategoryPathSnapshot("[12,1]");
        assertThrows(ServiceException.class, () -> service.bind(other));
        var draft = new EnrollmentAgreementDO(); draft.setPublishedVersion(0L); when(templates.selectById(12L)).thenReturn(draft);
        assertThrows(ServiceException.class, () -> service.bind(other));
        draft.setPublishedVersion(1L); draft.setPublishedBody("乙"); service.bind(other);
        assertEquals("乙",other.getAgreementTemplate()); assertEquals("末级乙",other.getAgreementCategoryName());
    }

    @Test void rejectsParentAndNeverPublishesStarterForAnUnconfiguredLeaf() {
        category(1, "父分类"); when(categories.selectCountByParentId(1L)).thenReturn(2L);
        assertThrows(ServiceException.class, () -> service.detail(1L));
        category(11, "叶子"); when(templates.starterBody()).thenReturn("起草范本");
        assertEquals(new EnrollmentAgreementService.Template(0,0,"起草范本"), service.detail(11L));
        assertThrows(ServiceException.class, () -> service.save(new EnrollmentAgreementService.Change(11L,0L,"起草范本"),true));
    }
}
