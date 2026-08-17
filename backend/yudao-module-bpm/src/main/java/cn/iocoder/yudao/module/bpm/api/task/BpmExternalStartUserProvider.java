package cn.iocoder.yudao.module.bpm.api.task;

public interface BpmExternalStartUserProvider {
    Integer getUserType();
    String validateAndGetDisplayName(Long userId);
}
