package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.order.*;
import cn.iocoder.yudao.module.zsjos.dal.dataobject.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.order.*;
import cn.iocoder.yudao.module.zsjos.dal.mysql.studentinfo.*;
import cn.iocoder.yudao.module.zsjos.service.order.SalesOrderObjectPermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TextbookAddressServiceTest {
    @Mock SalesOrderMapper orders;
    @Mock SalesOrderItemMapper items;
    @Mock SalesOrderObjectPermissionService permissions;
    @Mock StudentInfoTaskMapper tasks;
    @Mock StudentInfoFormMapper forms;
    @Mock StudentArchiveRevisionMapper revisions;
    @Mock StudentInfoFormValueMapper answers;
    @InjectMocks TextbookAddressService service;

    @Test void groupsBySubmittedItemWithinOrderAndSeparatesPending() {
        var order = new SalesOrderDO();
        order.setId(11L);
        order.setPersonId(22L);
        when(orders.selectManagementPage(any(), any(), isNull())).thenReturn(new PageResult<>(List.of(order), 1L));
        var products = new ArrayList<SalesOrderItemDO>();
        for (long id = 1; id <= 6; id++) {
            var product = new SalesOrderItemDO();
            product.setId(id);
            products.add(product);
            var task = new StudentInfoTaskDO();
            task.setOrderId(11L);
            task.setPersonId(22L);
            task.setFormId(id);
            when(tasks.byItem(id)).thenReturn(task);
            var form = new StudentInfoFormDO();
            form.setId(id);
            form.setOrderItemId(id);
            form.setPersonId(22L);
            form.setStatus(id == 6 ? "DRAFT" : "SUBMITTED");
            when(forms.selectById(id)).thenReturn(form);
            if (id < 6) when(answers.byForm(id)).thenReturn(List.of(
                    value("mobile", id == 4 ? "B" : "A"),
                    value("textbook_shipping_address", id == 5 ? " " : id == 3 ? "地址乙" : "地址甲")));
        }
        when(items.selectListByOrderId(11L)).thenReturn(products);
        var result = service.page(1, 20, "", 9L);
        assertEquals(1L, result.getTotal());
        assertEquals(List.of(2, 1, 1, 2), result.getList().stream().map(row -> row.products().size()).toList());
        assertNull(result.getList().getLast().destination());
        verify(answers, never()).byForm(6L);
    }

    private StudentInfoFormValueDO value(String key, String text) {
        var value = new StudentInfoFormValueDO();
        value.setFieldKey(key);
        value.setValueText(text);
        return value;
    }
}
