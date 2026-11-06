package com.yggdrasil.labs.test.arch;

import java.util.List;
import java.util.Map;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.library.Architectures.LayeredArchitecture;

/** COLA 分层、领域框架隔离与包循环的可选规则预设。 */
public final class ColaArchRules {
    private ColaArchRules() {}

    /**
     * 检查 COLA 五层的访问关系，缺失层可选，实际出现的层必须至少有两层。
     *
     * @param rootPackage 包含 adapter、client、app、domain、infrastructure 的完整根包名
     * @return COLA 访问约束规则，层外的装配代码不受约束
     * @throws IllegalArgumentException 根包名为空或无效时抛出
     */
    public static ArchRule layered(String rootPackage) {
        Map<String, List<String>> layers =
                LayerSpec.of(rootPackage)
                        .layer("adapter")
                        .layer("client")
                        .layer("app")
                        .layer("domain")
                        .layer("infrastructure")
                        .definitions();
        LayeredArchitecture architecture =
                LayerRules.architecture(layers)
                        .whereLayer("adapter")
                        .mayNotBeAccessedByAnyLayer()
                        .whereLayer("app")
                        .mayOnlyBeAccessedByLayers("adapter")
                        .whereLayer("client")
                        .mayOnlyBeAccessedByLayers("adapter", "app")
                        .whereLayer("domain")
                        .mayOnlyBeAccessedByLayers("app", "infrastructure")
                        .whereLayer("infrastructure")
                        .mayNotBeAccessedByAnyLayer();
        return LayerRules.withPopulatedLayers(architecture, layers);
    }

    /**
     * 禁止领域层依赖 Spring、MyBatis、MyBatis-Plus、Servlet 与 JPA。
     *
     * @param rootPackage 领域层所在的完整根包名，领域层固定为其 domain 子包
     * @return 框架隔离规则；领域层不存在时允许通过
     * @throws IllegalArgumentException 根包名为空或无效时抛出
     */
    public static ArchRule domainIsFrameworkFree(String rootPackage) {
        String root = ArchPackages.requirePackage(rootPackage, "rootPackage");
        return MimirArchRules.noDependencyOn(
                        root + ".domain",
                        "org.springframework",
                        "org.apache.ibatis",
                        "com.baomidou.mybatisplus",
                        "jakarta.servlet",
                        "javax.servlet",
                        "jakarta.persistence",
                        "javax.persistence")
                .allowEmptyShould(true);
    }

    /**
     * 组合 COLA 分层、领域框架隔离与根包直接子包间的无循环规则。
     *
     * @param rootPackage 要检查的完整根包名
     * @return 推荐的 COLA 架构规则组合
     * @throws IllegalArgumentException 根包名为空或无效时抛出
     */
    public static ArchRule recommended(String rootPackage) {
        return CompositeArchRule.of(layered(rootPackage))
                .and(domainIsFrameworkFree(rootPackage))
                .and(MimirArchRules.noCycles(rootPackage));
    }
}
