package cn.iocoder.yudao.module.system.controller.admin.mail;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.controller.admin.mail.vo.log.MailLogExcelVO;
import cn.iocoder.yudao.module.system.controller.admin.mail.vo.log.MailLogPageReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.mail.MailLogDO;
import cn.iocoder.yudao.module.system.service.mail.MailLogService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomLongId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailLogControllerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private MailLogController mailLogController;

    @Mock
    private MailLogService mailLogService;
    @Mock
    private HttpServletResponse response;

    @Test
    void exportMailLogExcelUsesPageFiltersAndExcludesSensitiveFields() throws Exception {
        MailLogPageReqVO reqVO = new MailLogPageReqVO();
        reqVO.setAccountId(randomLongId());
        MailLogDO mailLog = new MailLogDO().setId(randomLongId());
        when(mailLogService.getMailLogPage(reqVO)).thenReturn(new PageResult<>(List.of(mailLog), 1L));

        try (MockedStatic<ExcelUtils> excelUtils = mockStatic(ExcelUtils.class)) {
            mailLogController.exportMailLogExcel(reqVO, response);

            excelUtils.verify(() -> ExcelUtils.write(eq(response), eq("邮件日志.xls"), eq("数据"),
                    eq(MailLogExcelVO.class), anyList()));
        }
        ArgumentCaptor<MailLogPageReqVO> captor = ArgumentCaptor.forClass(MailLogPageReqVO.class);
        verify(mailLogService).getMailLogPage(captor.capture());
        assertEquals(PageParam.PAGE_SIZE_NONE, captor.getValue().getPageSize());
        assertEquals(reqVO.getAccountId(), captor.getValue().getAccountId());

        List<String> exportedFields = Arrays.stream(MailLogExcelVO.class.getDeclaredFields())
                .map(field -> field.getName()).toList();
        assertThat(exportedFields).doesNotContain("templateContent", "templateParams", "ccMails", "bccMails");
    }

}
