package cn.iocoder.yudao.module.system.api.ip;

import cn.iocoder.yudao.module.system.api.ip.dto.IdentityAreaRespDTO;

public interface IdentityAreaApi {
    IdentityAreaRespDTO resolve(String code);
}
