package com.yggdrasil.labs.log;

import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.IncludePackages;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * 日志模块测试套件
 * 
 * <p>统一运行所有单元测试</p>
 * 
 * <p>使用方法：</p>
 * <ul>
 * <li>在 IDEA 中右键此文件，选择 Run 'AllLogTests' 即可运行所有测试</li>
 * <li>或在命令行执行：mvn test -Dtest=AllLogTests</li>
 * </ul>
 * 
 * <p>包含的测试类：</p>
 * <ul>
 * <li>LogbackBasicTest - Logback 基础功能测试</li>
 * <li>MdcUtilTest - MDC 工具类测试</li>
 * <li>SensitiveDataPatternTest - 敏感数据模式测试</li>
 * <li>SensitiveDataConverterTest - 敏感数据转换器测试</li>
 * <li>AccessLogPropertiesTest - 访问日志配置测试</li>
 * <li>AccessLogAutoConfigurationTest - 访问日志自动配置测试</li>
 * <li>AccessLogFilterTest - 访问日志过滤器测试</li>
 * </ul>
 * 
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Suite
@SuiteDisplayName("Mimir Boot Starter Log 完整测试套件")
@SelectPackages("com.yggdrasil.labs.log")
@IncludeClassNamePatterns({".*Test"})
public class AllLogTests {
    // 这是一个测试套件类，不需要添加任何代码
    // JUnit 5 会通过 @SelectPackages 自动发现并运行所有匹配的测试类
}

