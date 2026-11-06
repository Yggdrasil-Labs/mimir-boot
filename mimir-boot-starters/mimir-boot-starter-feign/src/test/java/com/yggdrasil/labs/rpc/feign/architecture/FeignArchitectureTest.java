package com.yggdrasil.labs.rpc.feign.architecture;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import com.yggdrasil.labs.test.arch.MimirArchClasses;
import com.yggdrasil.labs.test.arch.MimirArchRules;

class FeignArchitectureTest {
    @Test
    void productionClassesFollowModuleDependencyDirection() {
        JavaClasses classes = MimirArchClasses.importDirectory(Path.of("target/classes"));
        MimirArchRules.onlyDependOnWithin(
                        "com.yggdrasil.labs",
                        "com.yggdrasil.labs.rpc.feign",
                        "com.yggdrasil.labs.common",
                        "com.yggdrasil.labs.rpc.core")
                .check(classes);
        // TD-051：只保留已发布构造器/返回类型涉及的配置属性依赖，其他循环仍检查。
        SlicesRuleDefinition.slices()
                .matching("com.yggdrasil.labs.rpc.feign.(*)..")
                .should()
                .beFreeOfCycles()
                .ignoreDependency(
                        "com.yggdrasil.labs.rpc.feign.client.RpcFeignClient",
                        "com.yggdrasil.labs.rpc.feign.config.FeignProperties")
                .ignoreDependency(
                        "com.yggdrasil.labs.rpc.feign.client.RpcFeignCapability",
                        "com.yggdrasil.labs.rpc.feign.config.FeignProperties")
                .check(classes);
    }
}
