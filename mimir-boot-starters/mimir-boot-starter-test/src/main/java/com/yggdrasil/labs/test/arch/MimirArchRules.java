package com.yggdrasil.labs.test.arch;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures.LayeredArchitecture;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/** Mimir 架构依赖规则。 */
public final class MimirArchRules {
    private MimirArchRules() {}

    /**
     * 禁止根包的直接子包之间形成循环，同一直接子包内部的循环不检查。
     *
     * @param rootPackage 要检查的完整根包名
     * @return 按直接子包切片的循环依赖规则
     * @throws IllegalArgumentException 根包名为空或无效时抛出
     */
    public static ArchRule noCycles(String rootPackage) {
        String root = ArchPackages.requirePackage(rootPackage, "rootPackage");
        return SlicesRuleDefinition.slices()
                .matching(root + ".(*)..")
                .should()
                .beFreeOfCycles()
                .allowEmptyShould(true);
    }

    /**
     * 禁止源包依赖指定包及其子包。
     *
     * @param sourcePackage 源类所在的完整包名
     * @param forbiddenPackages 禁止依赖的完整包名列表
     * @return 可在普通 JUnit 测试中执行的依赖规则
     * @throws IllegalArgumentException 包名为空、无效或禁止列表为空时抛出
     */
    public static ArchRule noDependencyOn(String sourcePackage, String... forbiddenPackages) {
        return noClasses()
                .that()
                .resideInAPackage(ArchPackages.pattern(sourcePackage, "sourcePackage"))
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        ArchPackages.patterns(forbiddenPackages, "forbiddenPackages", false));
    }

    /**
     * 限制自身包在指定命名空间内只依赖自身与允许包，命名空间外的依赖不受限制。
     *
     * @param namespace 需要限制的完整命名空间
     * @param ownPackage 本模块的完整包名
     * @param allowedPackages 允许依赖的其他完整包名，可为空列表
     * @return 命名空间内的依赖白名单规则
     * @throws IllegalArgumentException 任一包名为空、无效，或允许列表为 null 时抛出
     */
    public static ArchRule onlyDependOnWithin(
            String namespace, String ownPackage, String... allowedPackages) {
        String namespacePattern = ArchPackages.pattern(namespace, "namespace");
        String ownPattern = ArchPackages.pattern(ownPackage, "ownPackage");
        List<String> allowed =
                new ArrayList<>(
                        List.of(ArchPackages.patterns(allowedPackages, "allowedPackages", true)));
        allowed.add(ownPattern);
        return noClasses()
                .that()
                .resideInAPackage(ownPattern)
                .should()
                .dependOnClassesThat(
                        resideInAPackage(namespacePattern)
                                .and(resideOutsideOfPackages(allowed.toArray(String[]::new))));
    }

    /**
     * 按声明顺序自上而下约束依赖，下层不得依赖上层，缺失层可选。
     *
     * @param spec 至少包含两层的分层声明
     * @return 分层规则；执行时有类的层少于两层会报告违规
     * @throws IllegalArgumentException 声明为 null 或声明层数少于两层时抛出
     */
    public static ArchRule layered(LayerSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec 不能为空");
        }
        Map<String, List<String>> layers = spec.definitions();
        List<String> names = new ArrayList<>(layers.keySet());
        LayeredArchitecture architecture = LayerRules.architecture(layers);
        for (int index = 0; index < names.size(); index++) {
            architecture =
                    index == 0
                            ? architecture.whereLayer(names.get(index)).mayNotBeAccessedByAnyLayer()
                            : architecture
                                    .whereLayer(names.get(index))
                                    .mayOnlyBeAccessedByLayers(
                                            names.subList(0, index).toArray(String[]::new));
        }
        return LayerRules.withPopulatedLayers(architecture, layers);
    }
}
