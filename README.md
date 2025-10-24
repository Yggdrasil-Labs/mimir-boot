# Mimir Boot

> Yggdrasil-Labs 的 Java 企业级基础框架  
> 统一依赖版本、公共组件、自定义 Starter 与编译期代码生成工具。

## ✨ 特性
- ✅ 统一依赖版本管理（Spring Boot / MyBatis-Plus / MapStruct）
- ⚙️ 公共库封装（响应模型、异常、工具类）
- 🚀 Starter 模块（Web、DB）
- 🧬 编译期代码生成（注解 + JavaPoet）
- 🧱 企业级父依赖与 BOM 管理

## 📦 模块
| 模块 | 描述 |
|------|------|
| mimir-boot-parent | 父 POM 与依赖集中管理 |
| mimir-boot-bom | 依赖版本统一管理 |
| mimir-boot-common | 公共模型与工具 |
| mimir-boot-web | Web Starter（异常、日志、拦截器） |
| mimir-boot-db | MyBatis-Plus Starter |
| mimir-boot-samples | 示例项目 |

## 🧩 快速使用

### 使用 Parent POM
```xml
<parent>
  <groupId>com.yggdrasil.labs</groupId>
  <artifactId>mimir-boot-parent</artifactId>
  <version>1.0-SNAPSHOT</version>
</parent>
```

### 使用 BOM 依赖管理
```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.yggdrasil.labs</groupId>
      <artifactId>mimir-boot-bom</artifactId>
      <version>1.0-SNAPSHOT</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

## 🛠️ Maven Profiles

| Profile | 描述 | 用途 |
|---------|------|------|
| `dev` | 开发环境（默认） | 本地开发 |
| `test` | 测试环境 | 测试部署 |
| `prod` | 生产环境 | 生产部署 |
| `skip-tests` | 跳过测试 | 快速构建 |
| `quality` | 代码质量检查 | 代码审查 |
| `release` | 发布配置 | Maven 发布 |

## 📋 包含的依赖

### Spring 生态
- Spring Boot 3.3.13
- Spring Cloud 2023.0.6
- Spring Cloud Alibaba 2023.0.3.3

### 数据库相关
- MyBatis-Plus 3.5.14
- MySQL Connector 8.0.33
- PostgreSQL 42.7.4
- HikariCP 5.1.0

### 工具类
- MapStruct 1.5.5.Final
- Lombok 1.18.36
- Hutool 5.8.34
- FastJSON2 2.0.54

### 测试框架
- JUnit 5.11.1
- Mockito 5.12.0
- TestContainers 1.20.5

### 监控与安全
- Micrometer 1.13.2
- Spring Security 6.3.4
- JWT 0.12.6

### 文件处理
- Apache POI 5.2.5
- iText 8.0.2

### 定时任务与分布式
- Quartz 2.3.2
- Apache Curator 5.5.0

## 🚀 Maven Wrapper

项目已集成 Maven Wrapper，无需本地安装 Maven：

```bash
# Windows
./mvnw.cmd clean install

# Linux/macOS
./mvnw clean install
```

## 📝 使用示例

### 创建新项目
```xml
<parent>
  <groupId>com.yggdrasil.labs</groupId>
  <artifactId>mimir-boot-parent</artifactId>
  <version>1.0-SNAPSHOT</version>
</parent>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.yggdrasil.labs</groupId>
      <artifactId>mimir-boot-bom</artifactId>
      <version>1.0-SNAPSHOT</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
  </dependency>
</dependencies>
```