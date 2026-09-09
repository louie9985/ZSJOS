package cn.iocoder.yudao.framework.mybatis.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.util.ClassUtils;

import java.beans.Introspector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mapper 的 Bean 名生成器：在接口名首字母小写的基础上，加上模块名前缀。
 *
 * 背景：MyBatis 的 {@link MapperScan} 默认以接口简单名的首字母小写作为 Bean 名（如 cn.iocoder.yudao.module.infra...
 * dal.mysql.file.FileMapper -> fileMapper）。这会导致各模块交叉注入时按名（by-name）命中外来 Mapper 的 Bean，
 * 例如 zsjos 模块里名为 fileMapper 的字段被 infra 模块的 FileMapper 顶替，启动报
 * "The bean 'fileMapper' could not be injected because it is a JDK dynamic proxy"。
 *
 * 该生成器将 Bean 名变成 "模块名前缀 + 接口简单名首字母小写"（如 infraFileMapper），
 * 使得每个 Mapper 的 Bean 名在全应用中唯一，从根上消除跨模块 by-name 碰撞。
 *
 * 注意：{@link MapperScan#nameGenerator()} 由 mybatis-spring 的 MapperScannerRegistrar 直接通过
 * {@link org.springframework.beans.BeanUtils#instantiateClass(Class)} 实例化，因此此处必须存在无参构造器，
 * 且不能依赖 Spring 容器的注入。
 *
 * @author 芋道源码
 */
public class MapperBeanNameGenerator implements BeanNameGenerator {

    /** 匹配 yudao 模块名，如 cn.iocoder.yudao.module.infra -> infra */
    private static final Pattern MODULE_PATTERN = Pattern.compile("cn\\.iocoder\\.yudao\\.module\\.([^.]+)");

    @Override
    public String generateBeanName(BeanDefinition definition, BeanDefinitionRegistry registry) {
        String beanClassName = definition.getBeanClassName();
        String baseName = ClassUtils.getShortName(beanClassName); // 如 FileMapper
        String moduleName = extractModule(beanClassName);
        // 模块名 + 接口名（保留原大小写，如 infraFileMapper）；非 yudao 业务模块包解析不出模块名时，
        // 退化为 MyBatis 默认的首字母小写行为（如 baseMapperX），避免无谓地改动现有行为
        return moduleName == null ? Introspector.decapitalize(baseName) : moduleName + baseName;
    }

    private String extractModule(String beanClassName) {
        if (beanClassName == null) {
            return null;
        }
        Matcher matcher = MODULE_PATTERN.matcher(beanClassName);
        // 模块名本身已是小写（如 infra），直接拼接即可
        return matcher.find() ? matcher.group(1) : null;
    }

}
