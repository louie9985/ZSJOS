package cn.iocoder.yudao.framework.mybatis.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapperBeanNameGeneratorTest {

    private final MapperBeanNameGenerator generator = new MapperBeanNameGenerator();

    /**
     * 核心行为：yudao 业务模块的 Mapper 生成 "模块名 + 接口名首字母小写" 的 Bean 名，
     * 使每个 Mapper 的 Bean 名在全应用中全局唯一，消除跨模块 @Resource by-name 碰撞。
     */
    @Test
    void generateBeanName_prefixesModuleName() {
        // infra 模块的 FileMapper -> infraFileMapper
        assertEquals("infraFileMapper", name("cn.iocoder.yudao.module.infra.dal.mysql.file.FileMapper"));
        // zsjos 模块的 ContentVersionFileMapper -> zsjosContentVersionFileMapper
        assertEquals("zsjosContentVersionFileMapper", name("cn.iocoder.yudao.module.zsjos.dal.mysql.content.ContentVersionFileMapper"));
    }

    /**
     * 非 yudao 业务模块包（如 framework）解析不出模块名时，退化为原始 Bean 名，仅加前缀不破坏现有行为。
     */
    @Test
    void generateBeanName_fallsBackToDecapitalizedForNonModulePackage() {
        assertEquals("baseMapperX", name("cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX"));
    }

    private String name(String className) {
        var definition = BeanDefinitionBuilder.genericBeanDefinition(className).getBeanDefinition();
        return generator.generateBeanName(definition, new DefaultListableBeanFactory());
    }

}
