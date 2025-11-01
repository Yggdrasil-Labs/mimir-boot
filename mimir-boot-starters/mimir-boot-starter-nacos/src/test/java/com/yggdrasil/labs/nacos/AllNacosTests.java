package com.yggdrasil.labs.nacos;

import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Nacos 配置加密模块测试套件
 *
 * <p>统一运行所有单元测试</p>
 *
 * <p>使用方法：</p>
 * <ul>
 * <li>在 IDEA 中右键此文件，选择 Run 'AllNacosTests' 即可运行所有测试</li>
 * <li>或在命令行执行：mvn test -Dtest=AllNacosTests</li>
 * </ul>
 *
 * <p>包含的测试类：</p>
 * <ul>
 * <li>ConfigCryptoUtilsTest - 配置加解密工具类测试</li>
 * <li>ConfigDecryptProcessorTest - 配置解密处理器测试</li>
 * <li>NacosEncryptUtilTest - Nacos 加密工具类测试</li>
 * <li>NacosEncryptPropertiesTest - 配置属性测试</li>
 * <li>NacosEncryptAutoConfigurationTest - 自动配置测试</li>
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Suite
@SuiteDisplayName("Mimir Boot Starter Nacos 完整测试套件")
@SelectPackages("com.yggdrasil.labs.nacos")
@IncludeClassNamePatterns({".*Test"})
public class AllNacosTests {
    // 这是一个测试套件类，不需要添加任何代码
    // JUnit 5 会通过 @SelectPackages 自动发现并运行所有匹配的测试类
}
