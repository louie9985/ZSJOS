package cn.iocoder.yudao.module.bpm.api.approvalcontent;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BpmApprovalFormatTest {

    @Test
    void amountKeepsTwoDecimals() {
        assertEquals("1280.00", BpmApprovalFormat.amount(new BigDecimal("1280")));
        assertEquals("0.50", BpmApprovalFormat.amount(new BigDecimal("0.5")));
        assertEquals("1280.46", BpmApprovalFormat.amount(new BigDecimal("1280.456")));
        assertNull(BpmApprovalFormat.amount(null));
    }

    @Test
    void dateTimeOmitsSeconds() {
        assertEquals("2026-09-17 14:05",
                BpmApprovalFormat.dateTime(LocalDateTime.of(2026, 9, 17, 14, 5, 33)));
        assertNull(BpmApprovalFormat.dateTime(null));
    }

    @Test
    void truncateKeepsShortTextIntact() {
        assertEquals("短文本", BpmApprovalFormat.truncate("短文本", 10));
        assertEquals("这是一段很长的…", BpmApprovalFormat.truncate("这是一段很长的申诉理由文本", 7));
        assertNull(BpmApprovalFormat.truncate(null, 5));
    }

    @Test
    void maskCardMatchesBusinessSide() {
        // 与 WithdrawalServiceImpl.mask 保持一致的展示，避免同一张卡在两处显示不同。
        assertEquals("6222 **** **** 1234", BpmApprovalFormat.maskCard("6222020202021234"));
        assertEquals("****", BpmApprovalFormat.maskCard("123"));
        assertEquals("****", BpmApprovalFormat.maskCard(null));
    }
}
