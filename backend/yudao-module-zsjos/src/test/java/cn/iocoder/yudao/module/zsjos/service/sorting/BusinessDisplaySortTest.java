package cn.iocoder.yudao.module.zsjos.service.sorting;

import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MyStudentRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.order.vo.SalesOrderListItemRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.cashback.vo.*;
import cn.iocoder.yudao.module.zsjos.controller.admin.withdrawal.vo.WithdrawalRespVO;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BusinessDisplaySortTest {
    @Test void studentCoursesCompareCompleteDisplaySequenceAndClassUsesDisplayedFirstClass() {
        var a=new MyStudentRespVO();a.setPersonId(1L);
        var b=new MyStudentRespVO();b.setPersonId(2L);
        var first=new MyStudentRespVO.ServiceVO();first.setCourseName("陈");first.setClassName("张班");
        var second=new MyStudentRespVO.ServiceVO();second.setCourseName("张");second.setClassName("陈班");
        a.setServices(List.of(first,second));b.setServices(List.of(second,first));
        assertTrue(StudentListSort.create().comparator("courses","ascend").compare(a,b)<0);
        assertTrue(StudentListSort.create().comparator("className","ascend").compare(a,b)>0);
    }
    @Test void orderStatusesCompareChineseLabelsInsteadOfProtocolCodes() {
        var a=new SalesOrderListItemRespVO();a.setId(1L);a.setStatus("effective");
        var b=new SalesOrderListItemRespVO();b.setId(2L);b.setStatus("pending_approval");
        assertTrue(OrderListSort.create().comparator("status","ascend").compare(a,b)>0);
        a.setTaskDefinitionKey("financeReview");b.setTaskDefinitionKey("registrationReview");
        assertTrue(OrderListSort.create().comparator("taskDefinitionKey","ascend").compare(a,b)>0);
    }
    @Test void cashbackUsesAuthorizedSourceOnlyAndValidBaseIsNotApplicable() {
        var a=new CashbackRespVO();a.setId(1L);a.setType("valid");a.setBaseAmount(BigDecimal.ONE);a.setLeadId(999L);
        var b=new CashbackRespVO();b.setId(2L);b.setType("deal");b.setBaseAmount(BigDecimal.TEN);
        var source=new FinanceSourceRespVO();source.setStudentName("陈");b.setSource(source);
        assertTrue(FinanceListSort.cashback(null).comparator("customer","ascend").compare(a,b)>0);
        assertTrue(FinanceListSort.cashback(null).comparator("baseAmount","descend").compare(a,b)>0);
    }
    @Test void withdrawalUsesVisibleMaskedCardAndHistoricalMissingNameIsEmpty() {
        var a=new WithdrawalRespVO();a.setId(1L);a.setMaskedCardNumber("****9999");a.setApplicantName("历史申请人信息缺失");
        var b=new WithdrawalRespVO();b.setId(2L);b.setMaskedCardNumber("****1111");b.setApplicantName("陈");
        assertTrue(FinanceListSort.withdrawal().comparator("cardNumber","ascend").compare(a,b)>0);
        assertTrue(FinanceListSort.withdrawal().comparator("applicantName","descend").compare(a,b)>0);
    }
}
