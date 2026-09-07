package cn.iocoder.yudao.module.system.service.user;

import cn.iocoder.yudao.module.system.controller.admin.user.vo.user.UserOnlineStatusRespVO;

import java.util.Collection;
import java.util.Set;

public interface AdminUserOnlineService {

    Set<Long> getRequiredOnlineUserIds();

    UserOnlineStatusRespVO getOnlineStatus(Collection<Long> requestedUserIds);

}
