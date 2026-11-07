<?xml version="1.0" encoding="UTF-8"?>
<!-- 从固定官方描述符派生独立入口；测试目标复用官方 Mojo，只调整源码根默认值和分叉目标。 -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
    <xsl:output method="xml" encoding="UTF-8" indent="yes"/>
    <xsl:param name="upstream-version"/>
    <xsl:param name="plugin-group-id"/>
    <xsl:param name="plugin-artifact-id"/>
    <xsl:param name="plugin-version"/>
    <xsl:template match="/">
        <xsl:if test="not(plugin/groupId = 'org.apache.maven.plugins' and plugin/artifactId = 'maven-pmd-plugin' and plugin/version = $upstream-version and plugin/goalPrefix = 'pmd')">
            <xsl:message terminate="yes">官方 PMD 描述符根节点坐标不匹配</xsl:message>
        </xsl:if>
        <xsl:if test="count(plugin/mojos/mojo[goal = 'pmd' or goal = 'check' or goal = 'cpd' or goal = 'cpd-check']) != 4">
            <xsl:message terminate="yes">官方 PMD 描述符缺少所需目标</xsl:message>
        </xsl:if>
        <xsl:comment>由 Apache Maven PMD Plugin <xsl:value-of select="$upstream-version"/> 派生；修改坐标、名称和前缀，保留四个原目标并派生两个测试目标，移除描述符依赖清单。官方实现通过插件依赖提供。</xsl:comment>
        <xsl:apply-templates/>
    </xsl:template>
    <xsl:template match="@*|node()">
        <xsl:copy><xsl:apply-templates select="@*|node()"/></xsl:copy>
    </xsl:template>
    <xsl:template match="/plugin/name"><name>Mimir Boot Code Quality</name></xsl:template>
    <xsl:template match="/plugin/description"><description>Mimir Boot 独立 PMD/CPD 入口，复用官方实现。</description></xsl:template>
    <xsl:template match="/plugin/groupId"><groupId><xsl:value-of select="$plugin-group-id"/></groupId></xsl:template>
    <xsl:template match="/plugin/artifactId"><artifactId><xsl:value-of select="$plugin-artifact-id"/></artifactId></xsl:template>
    <xsl:template match="/plugin/version"><version><xsl:value-of select="$plugin-version"/></version></xsl:template>
    <xsl:template match="/plugin/goalPrefix"><goalPrefix>mimir-quality</goalPrefix></xsl:template>
    <xsl:template match="/plugin/dependencies"><dependencies/></xsl:template>
    <xsl:template match="/plugin/mojos/mojo[not(goal = 'pmd' or goal = 'check' or goal = 'cpd' or goal = 'cpd-check')]"/>
    <xsl:template match="/plugin/mojos">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
            <xsl:apply-templates select="mojo[goal = 'pmd' or goal = 'check']" mode="test"/>
        </xsl:copy>
    </xsl:template>
    <xsl:template match="@*|node()" mode="test">
        <xsl:copy><xsl:apply-templates select="@*|node()" mode="test"/></xsl:copy>
    </xsl:template>
    <xsl:template match="mojo/goal" mode="test">
        <goal>test-<xsl:value-of select="."/></goal>
    </xsl:template>
    <xsl:template match="mojo/description" mode="test">
        <description>Mimir 测试源码 PMD 入口，复用官方实现与原生开关，报告位于独立测试目录。</description>
    </xsl:template>
    <xsl:template match="mojo/executeGoal" mode="test"><executeGoal>test-pmd</executeGoal></xsl:template>
    <!-- 显式绑定测试根，防止 Maven 忽略空集合后由官方 Mojo 回退到主源码根。 -->
    <xsl:template match="mojo/configuration/compileSourceRoots" mode="test">
        <!-- 双花括号让 Maven 表达式原样进入描述符，避免 XSLT 提前求值。 -->
        <compileSourceRoots implementation="java.util.List" default-value="${{project.testCompileSourceRoots}}"/>
    </xsl:template>
    <xsl:template match="mojo/parameters/parameter[name = 'compileSourceRoots']/description" mode="test">
        <description>测试入口默认与 testSourceRoots 一样绑定 Maven 实际 testCompileSourceRoots。</description>
    </xsl:template>
    <xsl:template match="mojo/configuration/includeTests/@default-value" mode="test">
        <xsl:attribute name="default-value">true</xsl:attribute>
    </xsl:template>
</xsl:stylesheet>
