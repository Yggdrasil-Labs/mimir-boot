package com.yggdrasil.labs.mybatis;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

/**
 * MyBatis Processor 测试套件
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Suite
@SelectPackages({
    "com.yggdrasil.labs.mybatis.annotation",
    "com.yggdrasil.labs.mybatis.processor"
})
public class AllMybatisProcessorTests {
}

