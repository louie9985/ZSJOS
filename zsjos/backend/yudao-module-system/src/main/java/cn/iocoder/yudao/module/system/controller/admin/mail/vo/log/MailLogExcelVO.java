package cn.iocoder.yudao.module.system.controller.admin.mail.vo.log;

import cn.idev.excel.annotation.ExcelProperty;
import cn.iocoder.yudao.framework.excel.core.annotations.DictFormat;
import cn.iocoder.yudao.framework.excel.core.convert.DictConvert;
import cn.iocoder.yudao.framework.excel.core.convert.JsonConvert;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.system.enums.DictTypeConstants.MAIL_SEND_STATUS;

@Data
public class MailLogExcelVO {

    @ExcelProperty("日志编号")
    private Long id;

    @ExcelProperty(value = "收件邮箱", converter = JsonConvert.class)
    private List<String> toMails;

    @ExcelProperty("邮箱账号编号")
    private Long accountId;

    @ExcelProperty("模板编码")
    private String templateCode;

    @ExcelProperty("邮件标题")
    private String templateTitle;

    @ExcelProperty(value = "发送状态", converter = DictConvert.class)
    @DictFormat(MAIL_SEND_STATUS)
    private Integer sendStatus;

    @ExcelProperty("发送时间")
    private LocalDateTime sendTime;

    @ExcelProperty("消息 ID")
    private String sendMessageId;

    @ExcelProperty("异常摘要")
    private String sendException;

    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

}
