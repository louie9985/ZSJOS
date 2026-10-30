package cn.iocoder.yudao.module.zsjos.service.homemetric;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.zsjos.dal.mysql.homemetric.HomeMetricMapper;
import cn.iocoder.yudao.module.zsjos.framework.permission.ZsjosObjectPermissionProvider;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
@Component
public class HomeMetricPermissionProvider implements ZsjosObjectPermissionProvider {
    @Resource private PermissionApi permissions;
    @Resource private HomeMetricMapper mapper;
    @Resource private org.springframework.beans.factory.ObjectProvider<HomeMetricConfigService> configs;
    public String getBizType(){return "home-metric";}
    public boolean hasPermission(Long id,String action,Long actor){return actor!=null&&("view".equals(action)?configs.getObject().canView(id,actor):mapper.selectById(id)!=null&&permissions.hasAnyPermissions(actor,"zsjos:home-metric:"+action));}
    @Override public void check(Long id,String action,Long actor){if(!hasPermission(id,action,actor))throw cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception(cn.iocoder.yudao.module.zsjos.enums.ZsjosErrorCodeConstants.HOME_METRIC_DENIED);}
}
