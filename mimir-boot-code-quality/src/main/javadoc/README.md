# Mimir Boot Code Quality

这是不含 Java 类的资源制品。规则位于 `mimir-boot/pmd/main-ruleset.xml` 和 `mimir-boot/pmd/test-ruleset.xml`，适用于 Maven PMD Plugin 3.28.0 内置的 PMD 7.17.0。

主 jar 的 `META-INF/maven/plugin.xml` 从固定版本官方描述符生成，公开 `pmd`、`check`、`cpd`、`cpd-check` 及其四个测试派生目标。测试入口只读取 Maven 的测试源码根。官方 Java 实现由 Parent 的插件依赖提供，制品 POM 不声明依赖。

规则范围、接入与覆盖方式见源码中的模块 README，以及仓库的 `mimir-boot-parent/README.md`。
