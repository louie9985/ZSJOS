package cn.iocoder.yudao.module.zsjos.service.sorting;

import cn.iocoder.yudao.module.zsjos.controller.admin.registration.vo.MediaStudentListRespVO;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Match the media inbox card's authorized operator and account display, including duplicate names. */
public final class MediaStudentListSort {
    private MediaStudentListSort() {}

    public static BusinessListSort<MediaStudentListRespVO> create() {
        return new BusinessListSort<MediaStudentListRespVO>(MediaStudentListRespVO::getPersonId)
                .field("name", MediaStudentListRespVO::getName)
                .field("personNo", MediaStudentListRespVO::getPersonNo)
                .field("operators", MediaStudentListSort::operators)
                .field("accounts", MediaStudentListSort::accounts)
                .field("platforms", row -> accountRows(row).stream()
                        .map(account -> BusinessListSort.first(trim(account.getPlatformLabel()), "平台未记录"))
                        .collect(Collectors.joining("、")))
                .field("activatedAt", MediaStudentListRespVO::getActivatedAt);
    }

    static String operators(MediaStudentListRespVO row) {
        var names = new LinkedHashMap<Long, String>();
        if (row.getServices() != null) row.getServices().forEach(service -> {
            if (service.getOperatorUserId() == null) return;
            var current = names.get(service.getOperatorUserId());
            if (current == null || "姓名未提供".equals(current)) names.put(service.getOperatorUserId(),
                    BusinessListSort.first(trim(service.getOperatorUserName()), "姓名未提供"));
        });
        return String.join("、", names.values());
    }

    static String accounts(MediaStudentListRespVO row) {
        var accounts = accountRows(row);
        return accounts.stream().map(account -> {
            var nickname = trim(account.getNickname());
            var name = BusinessListSort.first(nickname, "未命名账号");
            boolean duplicate = accounts.stream().filter(other ->
                    Objects.equals(trim(other.getNickname()), nickname)
                            && Objects.equals(other.getPlatformValue(), account.getPlatformValue())).count() > 1;
            return ((nickname == null || nickname.isEmpty() || duplicate) && account.getAccountNo() != null
                    && !account.getAccountNo().isEmpty()) ? name + " · " + account.getAccountNo() : name;
        }).collect(Collectors.joining("、"));
    }

    private static List<MediaStudentListRespVO.AccountVO> accountRows(MediaStudentListRespVO row) {
        return row.getAccounts() == null ? List.of() : row.getAccounts();
    }

    private static String trim(String value) { return value == null ? null : value.trim(); }
}
