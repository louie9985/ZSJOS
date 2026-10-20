package cn.iocoder.yudao.module.zsjos.service.studentinfo;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.zsjos.controller.admin.studentinfo.vo.StudentInfoVO.Field;
import cn.iocoder.yudao.module.zsjos.dal.mysql.product.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.zsjos.enums.StudentInfoConstants.*;

@Component
public class StudentInfoProductRules {
    public static final Set<String> CONDITIONAL=Set.of("registration_level","target_school","target_major");
    @Resource private ZsjosProductMapper products;
    @Resource private ZsjosProductCategoryMapper categories;
    public List<Long> validate(List<Long> ids) {
        if (ids==null || ids.size()>100 || new HashSet<>(ids).size()!=ids.size()) throw exception(CONFIG_INVALID);
        for (Long id:ids) if (id==null || categories.selectById(id)==null) throw exception(CONFIG_INVALID);
        return ids;
    }
    public List<Long> path(Long productId) {
        var product=productId==null ? null : products.selectById(productId);
        if (product==null) throw exception(CONFIG_INVALID);
        List<Long> result=new ArrayList<>();
        Long id=product.getCategoryId();
        while (id!=null && id!=0) {
            if (result.contains(id) || result.size()>30) throw exception(CONFIG_INVALID);
            var category=categories.selectById(id);
            if (category==null) throw exception(CONFIG_INVALID);
            result.add(id); id=category.getParentId();
        }
        return result;
    }
    public List<Field> resolve(List<Field> definitions,List<Long> roots,List<Long> path) {
        boolean education=path.stream().anyMatch(roots::contains);
        var copy=JsonUtils.parseArray(JsonUtils.toJsonString(definitions),Field.class);
        return copy.stream().filter(Field::getEnabled).filter(f -> education || !CONDITIONAL.contains(f.getKey()))
                .peek(f -> { if (CONDITIONAL.contains(f.getKey())) f.setRequired(true); }).toList();
    }
}
