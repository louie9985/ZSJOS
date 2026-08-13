package cn.iocoder.yudao.module.zsjos.service.advancedfilter;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@Getter
@AllArgsConstructor
public class AdvancedFilterQuery {
    private final String whereSql;
    private final Map<String, Object> parameters;
}
