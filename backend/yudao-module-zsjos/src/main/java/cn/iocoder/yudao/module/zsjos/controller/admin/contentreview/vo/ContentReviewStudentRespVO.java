package cn.iocoder.yudao.module.zsjos.controller.admin.contentreview.vo;

import java.util.List;

/** Deliberately omits sales, finance, invitation and unrelated course/relationship fields. */
public record ContentReviewStudentRespVO(Long personId, String personNo, String name, String mobile,
        String wechatId, List<Account> accounts) {
    public record Account(Long id, String accountNo, String nickname, String platformLabel, Long serviceRelationId) { }
}
