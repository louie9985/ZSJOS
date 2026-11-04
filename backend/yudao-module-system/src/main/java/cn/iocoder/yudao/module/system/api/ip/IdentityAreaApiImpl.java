package cn.iocoder.yudao.module.system.api.ip;

import cn.iocoder.yudao.module.system.api.ip.dto.IdentityAreaRespDTO;
import cn.iocoder.yudao.module.system.service.ip.IdentityAreaService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class IdentityAreaApiImpl implements IdentityAreaApi {
    @Resource private IdentityAreaService service;
    @Override public IdentityAreaRespDTO resolve(String code) { return service.resolve(code); }
}
