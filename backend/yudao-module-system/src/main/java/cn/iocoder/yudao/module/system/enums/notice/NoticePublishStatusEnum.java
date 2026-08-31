package cn.iocoder.yudao.module.system.enums.notice;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum NoticePublishStatusEnum {
    DRAFT("DRAFT"),
    PUBLISHED("PUBLISHED"),
    OFFLINE("OFFLINE");

    private final String status;
}
