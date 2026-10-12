package cn.iocoder.yudao.module.zsjos.service.sorting;

import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MediaStudentListRespVO;
import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MyStudentRespVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MediaStudentListSortTest {
    @Test
    void operatorTextDeduplicatesIdentitiesAndUsesTheDisplayedName() {
        var row = row(1, "陈");
        row.setServices(List.of(operator(10, null), operator(20, " 李 "), operator(10, " 张 "), operator(20, "李")));
        assertEquals("张、李", MediaStudentListSort.operators(row));
    }

    @Test
    void accountTextIncludesAllRowsAndSamePlatformDuplicateNumbers() {
        var row = row(1, "陈");
        row.setAccounts(List.of(account("A1", " 同名 ", "dy"), account("A2", "同名", "dy"),
                account("A3", "同名", "wx"), account("A4", " ", "wx")));
        assertEquals("同名 · A1、同名 · A2、同名、未命名账号 · A4", MediaStudentListSort.accounts(row));
    }

    @Test
    void chineseNamesAndSnapshotPlatformsSortWithEmptyValuesLastBothWays() {
        var first = row(1, "陈"); var second = row(2, "张"); var empty = row(3, null);
        var sort = MediaStudentListSort.create();
        assertTrue(sort.comparator("name", "ascend").compare(first, second) < 0);
        assertTrue(sort.comparator("name", "descend").compare(first, second) > 0);
        first.setAccounts(List.of(account("A1", "账号", "z")));
        second.setAccounts(List.of(account("A2", "账号", "a")));
        first.getAccounts().getFirst().setPlatformLabel("抖音旧标签");
        second.getAccounts().getFirst().setPlatformLabel("小红书旧标签");
        assertTrue(sort.comparator("platforms", "ascend").compare(first, second) < 0);
        for (String direction : List.of("ascend", "descend")) {
            assertTrue(sort.comparator("name", direction).compare(empty, first) > 0);
            assertTrue(sort.comparator("accounts", direction).compare(empty, first) > 0);
            assertTrue(sort.comparator("platforms", direction).compare(empty, first) > 0);
        }
        assertTrue(sort.comparator("operators", "ascend").compare(first, second) > 0);
    }

    private static MediaStudentListRespVO row(long id, String name) {
        var row = new MediaStudentListRespVO(); row.setPersonId(id); row.setName(name); return row;
    }

    private static MyStudentRespVO.ServiceVO operator(long id, String name) {
        var service = new MyStudentRespVO.ServiceVO(); service.setOperatorUserId(id); service.setOperatorUserName(name); return service;
    }

    private static MediaStudentListRespVO.AccountVO account(String number, String name, String platform) {
        var account = new MediaStudentListRespVO.AccountVO(); account.setAccountNo(number);
        account.setNickname(name); account.setPlatformValue(platform); return account;
    }
}
