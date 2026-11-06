# Mimir Boot Code Quality

这是不含 Java 类的资源制品。规则位于 `mimir-boot/pmd/ruleset.xml`，适用于 Maven PMD Plugin 3.28.0 内置的 PMD 7.17.0。

主 jar 的 `META-INF/maven/plugin.xml` 是从固定版本官方描述符生成的独立插件入口，只公开 `pmd`、`check`、`cpd`、`cpd-check`。官方 Java 实现由 Parent 的插件依赖提供，制品 POM 不声明依赖。

规则范围、接入与覆盖方式见源码中的模块 README，以及仓库的 `mimir-boot-parent/README.md`。
