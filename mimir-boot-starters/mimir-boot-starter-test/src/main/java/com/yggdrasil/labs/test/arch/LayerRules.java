package com.yggdrasil.labs.test.arch;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import java.util.List;
import java.util.Map;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.AbstractClassesTransformer;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.Priority;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.Architectures.LayeredArchitecture;

/** 自定义分层与 COLA 预设共用的层导入及数量检查。 */
final class LayerRules {
    private LayerRules() {}

    /**
     * 将有序层定义转换为 ArchUnit 原生分层，允许层缺失。
     *
     * @param layers 层名与完整包名列表
     * @return 仅检查声明层之间依赖的架构
     */
    static LayeredArchitecture architecture(Map<String, List<String>> layers) {
        LayeredArchitecture architecture =
                layeredArchitecture()
                        .consideringOnlyDependenciesInLayers()
                        .withOptionalLayers(true);
        for (Map.Entry<String, List<String>> layer : layers.entrySet()) {
            architecture =
                    architecture
                            .layer(layer.getKey())
                            .definedBy(
                                    ArchPackages.patterns(
                                            layer.getValue().toArray(String[]::new),
                                            "packageNames",
                                            false));
        }
        return architecture;
    }

    /**
     * 为分层规则补充至少两个实际层的导入检查。
     *
     * @param rule 原始依赖方向规则
     * @param layers 层名与完整包名列表
     * @return 层数量与依赖方向的组合规则
     */
    static ArchRule withPopulatedLayers(ArchRule rule, Map<String, List<String>> layers) {
        AbstractClassesTransformer<JavaClasses> allClasses =
                new AbstractClassesTransformer<>("导入的生产类") {
                    @Override
                    public Iterable<JavaClasses> doTransform(JavaClasses classes) {
                        return List.of(classes);
                    }
                };
        ArchCondition<JavaClasses> countLayers =
                new ArchCondition<>("包含至少 2 个有类的层") {
                    @Override
                    public void check(JavaClasses classes, ConditionEvents events) {
                        long count =
                                layers.values().stream()
                                        .filter(
                                                packages ->
                                                        classes.stream()
                                                                .anyMatch(
                                                                        javaClass ->
                                                                                packages.stream()
                                                                                        .anyMatch(
                                                                                                packageName ->
                                                                                                        ArchPackages
                                                                                                                .contains(
                                                                                                                        packageName,
                                                                                                                        javaClass
                                                                                                                                .getPackageName()))))
                                        .count();
                        events.add(
                                new SimpleConditionEvent(
                                        classes,
                                        count >= 2,
                                        "有类的层少于 2 个，实际为 " + count + "；请检查根包、层包与导入范围"));
                    }
                };
        return CompositeArchRule.of(
                        ArchRule.Factory.create(allClasses, countLayers, Priority.MEDIUM))
                .and(rule);
    }
}
