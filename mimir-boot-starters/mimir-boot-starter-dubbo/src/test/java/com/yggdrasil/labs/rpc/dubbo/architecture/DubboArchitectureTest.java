package com.yggdrasil.labs.rpc.dubbo.architecture;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import com.yggdrasil.labs.test.arch.MimirArchClasses;
import com.yggdrasil.labs.test.arch.MimirArchRules;

class DubboArchitectureTest {
    @Test
    void productionClassesFollowModuleDependencyDirection() {
        JavaClasses classes = MimirArchClasses.importDirectory(Path.of("target/classes"));
        MimirArchRules.onlyDependOnWithin(
                        "com.yggdrasil.labs",
                        "com.yggdrasil.labs.rpc.dubbo",
                        "com.yggdrasil.labs.common",
                        "com.yggdrasil.labs.rpc.core")
                .check(classes);
        // TD-051：只保留已发布构造器/返回类型涉及的配置属性依赖，其他循环仍检查。
        SlicesRuleDefinition.slices()
                .matching("com.yggdrasil.labs.rpc.dubbo.(*)..")
                .should()
                .beFreeOfCycles()
                .ignoreDependency(
                        "com.yggdrasil.labs.rpc.dubbo.support.RpcDubboSupportHolder",
                        "com.yggdrasil.labs.rpc.dubbo.config.DubboProperties")
                .ignoreDependency(
                        "com.yggdrasil.labs.rpc.dubbo.support.RpcDubboSupportHolder$Snapshot",
                        "com.yggdrasil.labs.rpc.dubbo.config.DubboProperties")
                .check(classes);
    }
}
