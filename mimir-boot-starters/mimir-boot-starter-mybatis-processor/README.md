# Mimir Boot Starter MyBatis Processor

基于注解处理器的编译时代码生成工具，自动生成 MyBatis-Plus Mapper、Service 和 ServiceImpl。

## 用途

通过编译期注解处理器，减少样板代码：

- ✅ **自动生成 Mapper 接口**（继承 `BaseMapper<T>`）
- ✅ **自动生成 Service 接口**（继承 `IService<T>`）
- ✅ **自动生成 ServiceImpl 实现类**（继承 `ServiceImpl<M, T>`）
- ✅ **编译期生成**，零运行时开销
- ✅ **可配置包路径和命名规则**

## 快速开始

### 引入依赖

```xml
<dependency>
    <groupId>io.github.yggdrasil-labs</groupId>
    <artifactId>mimir-boot-starter-mybatis-processor</artifactId>
    <version>YOUR_RELEASE_VERSION</version>
    <scope>provided</scope>
</dependency>
```

**重要**：必须使用 `provided` 作用域，因为这是编译期工具，不需要打包到运行时。

处理器生成的 Mapper、Service 和 `@Service` 实现仍需要 MyBatis-Plus 与 Spring 运行时类型。请同时引入 `mimir-boot-starter-mybatis`（或提供等价的 MyBatis-Plus 与 Spring 依赖）：

```xml
<dependency>
    <groupId>io.github.yggdrasil-labs</groupId>
    <artifactId>mimir-boot-starter-mybatis</artifactId>
    <version>YOUR_RELEASE_VERSION</version>
</dependency>
```

### 编译器版本与注解处理开关

处理器声明支持的源版本跟随实际运行的 javac（`SourceVersion.latestSupported()`），较新 JDK 编译时不会再出现“支持的源版本低于当前源版本”的告警。制品自身最低运行要求仍为 Java 17。当前已实测 JDK 17、21；JDK 25 尚未验证，支持版本变宽只是声明，不代表所有新语法均已覆盖。

`latestSupported()` 取决于实际执行编译的 javac：通过 `maven-compiler-plugin` 的 `fork`/`executable` 或 Maven Toolchains 指定其他 JDK 时，以被调用的 javac 为准。

JDK 23 起，javac 在未显式指定 `-proc` 时默认不再执行 classpath 上发现的注解处理器，本处理器会静默不生成代码，直到引用生成类型时才以编译错误暴露：

- **继承 `mimir-boot-parent`**：Parent 已显式开启注解处理，无需额外配置。
- **只导入 `mimir-boot-bom` 或使用其他 Parent**：需自行开启。推荐放入按 JDK 激活的 profile，不要无条件设置（JDK 17 的 javac 不识别 `-proc:full`）：

```xml
<profiles>
    <profile>
        <id>annotation-processing-full</id>
        <activation>
            <!-- 如需同时消除 JDK 21/22 的隐式处理提示，可改为 [21,) -->
            <jdk>[23,)</jdk>
        </activation>
        <properties>
            <maven.compiler.proc>full</maven.compiler.proc>
        </properties>
    </profile>
</profiles>
```

- `maven.compiler.proc=full` 需要 `maven-compiler-plugin` 的 `proc` 参数接受 `full`；已核对 3.13.0～3.16.0 支持，更早版本未核实，旧插件可改为在 `compilerArgs` 中传 `-proc:full`。
- `<jdk>` 激活依据是运行 Maven 的 JVM，而不是 Toolchains 或 `fork` 实际调用的 javac。两者版本不同时，请按实际 javac 版本直接在编译插件中配置 `compilerArgs`（`-proc:full`）或 `maven.compiler.proc`，不要依赖该 profile。

### 使用示例

#### 1. 标注实体类

```java
package com.example;

import com.yggdrasil.labs.mybatis.annotation.AutoMybatis;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;

@AutoMybatis
@TableName("sys_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String email;
    
    // getters and setters...
}
```

#### 2. 编译项目

执行 Maven 编译：

```bash
mvn clean compile
```

#### 3. 生成的代码

编译后会自动生成以下文件：

**生成的 Mapper 接口**：

```java
// 位置：com/example/mapper/UserMapper.java
package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
```

**生成的 Service 接口**：

```java
// 位置：com/example/service/UserService.java
package com.example.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.example.User;

public interface UserService extends IService<User> {
}
```

**生成的 ServiceImpl 实现类**：

```java
// 位置：com/example/service/impl/UserServiceImpl.java
package com.example.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.mapper.UserMapper;
import com.example.User;
import com.example.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
}
```

#### 4. 使用生成的代码

```java
import com.example.service.UserService;
import com.example.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {
    @Autowired
    private UserService userService;  // 注入生成的 Service
    
    @GetMapping("/users/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.getById(id);  // 直接使用 MyBatis-Plus 提供的方法
    }
    
    @PostMapping("/users")
    public User createUser(@RequestBody User user) {
        userService.save(user);
        return user;
    }
}
```

## 配置选项

### 自定义包路径和命名规则

```java
@AutoMybatis(
    mapperPackage = "dao",           // Mapper 包名，默认为 "mapper"
    servicePackage = "service",        // Service 包名，默认为 "service"
    serviceImplPackage = "service.impl", // ServiceImpl 包名，默认为 "service.impl"
    mapperSuffix = "Mapper",          // Mapper 后缀，默认为 "Mapper"
    serviceSuffix = "Service",        // Service 后缀，默认为 "Service"
    serviceImplSuffix = "ServiceImpl" // ServiceImpl 后缀，默认为 "ServiceImpl"
)
@TableName("sys_user")
public class User {
    // ...
}
```

### 配置示例

#### 示例1：使用默认配置

```java
@AutoMybatis  // 使用所有默认值
@TableName("sys_user")
public class User {
    // 生成的类：
    // - com.example.mapper.UserMapper
    // - com.example.service.UserService
    // - com.example.service.impl.UserServiceImpl
}
```

#### 示例2：自定义包路径

```java
@AutoMybatis(
    mapperPackage = "repository",
    servicePackage = "business",
    serviceImplPackage = "business.impl"
)
@TableName("sys_user")
public class User {
    // 生成的类：
    // - com.example.repository.UserMapper
    // - com.example.business.UserService
    // - com.example.business.impl.UserServiceImpl
}
```

#### 示例3：自定义命名后缀

```java
@AutoMybatis(
    mapperSuffix = "Dao",
    serviceSuffix = "Facade",
    serviceImplSuffix = "FacadeImpl"
)
@TableName("sys_user")
public class User {
    // 生成的类：
    // - com.example.mapper.UserDao
    // - com.example.service.UserFacade
    // - com.example.service.impl.UserFacadeImpl
}
```

## 工作原理

### 编译期处理流程

1. **编译时扫描**：注解处理器扫描所有标注了 `@AutoMybatis` 的类
2. **代码生成**：使用 JavaPoet 生成对应的 Mapper、Service、ServiceImpl
3. **写入文件**：将生成的代码写入 `target/generated-sources/annotations` 目录
4. **参与编译**：生成的代码会参与后续的编译过程

### 生成规则

- **Mapper 包路径**：`实体类包名 + mapperPackage`
- **Service 包路径**：`实体类包名 + servicePackage`
- **ServiceImpl 包路径**：`实体类包名 + serviceImplPackage`
- **类名**：`实体类名 + 对应后缀`（如果实体类名以 `DO` 结尾，会自动去除 `DO` 后缀）

### 示例说明

假设实体类为：

```java
package com.example.entity;

@AutoMybatis
public class User {
    // ...
}
```

生成的代码位置：

- `com.example.entity.mapper.UserMapper`
- `com.example.entity.service.UserService`
- `com.example.entity.service.impl.UserServiceImpl`

#### DO 后缀自动处理

如果实体类名以 `DO` 结尾，生成 Mapper、Service 类名时会自动去除 `DO` 后缀：

```java
package com.example.entity;

@AutoMybatis
public class UserDO {
    // ...
}
```

生成的代码位置（注意类名去掉了 DO）：

- `com.example.entity.mapper.UserMapper`（不是 `UserDOMapper`）
- `com.example.entity.service.UserService`（不是 `UserDOService`）
- `com.example.entity.service.impl.UserServiceImpl`（不是 `UserDOServiceImpl`）

生成的代码中，实体类引用仍然使用原始类名 `UserDO`：

```java
public interface UserMapper extends BaseMapper<UserDO> {
    // 实体类引用保持原样
}
```

## 最佳实践

### 1. 目录结构建议

推荐的项目结构：

```
src/main/java/com/example/
├── entity/          # 实体类（标注 @AutoMybatis）
│   ├── User.java
│   └── Order.java
├── mapper/          # Mapper 接口（自动生成）
│   ├── UserMapper.java
│   └── OrderMapper.java
├── service/         # Service 接口（自动生成）
│   ├── UserService.java
│   └── OrderService.java
└── service/impl/    # ServiceImpl 实现（自动生成）
    ├── UserServiceImpl.java
    └── OrderServiceImpl.java
```

### 2. 实体类设计

```java
import com.baomidou.mybatisplus.annotation.*;
import com.yggdrasil.labs.mybatis.annotation.AutoMybatis;

@AutoMybatis
@TableName("sys_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String username;
    private String email;
    
    // 审计字段
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
    
    // 逻辑删除
    @TableLogic
    private Integer deleted;
    
    // 乐观锁
    @Version
    private Integer version;
    
    // getters and setters...
}
```

### 3. 扩展生成的 Service

生成的 `ServiceImpl` 已带 `@Service`。如果通过扩展类加入业务方法并交给 Spring 管理，应同时标记 `@Primary`，避免注入 `UserService` 时出现多个 Bean：

```java
// 方式1：直接在生成的类上添加方法（需要确保不会在重新生成时被覆盖）
// 注意：如果修改了实体类并重新编译，生成的文件会重新生成，自定义方法会丢失

// 方式2：创建扩展类（推荐）
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class UserServiceExt extends UserServiceImpl {
    
    public User findByUsername(String username) {
        return lambdaQuery()
            .eq(User::getUsername, username)
            .one();
    }
    
    public List<User> findActiveUsers() {
        return lambdaQuery()
            .eq(User::getStatus, "ACTIVE")
            .list();
    }
}
```

### 4. 自定义 Mapper 方法

如果需要自定义 SQL，可以创建对应的 XML 文件或在生成的 Mapper 接口中添加方法：

```java
// 方式1：在生成的 Mapper 接口中添加方法（注意重新编译会被覆盖）
// 不推荐，建议使用方式2

// 方式2：创建自定义 Mapper 接口（推荐）
@Mapper
public interface UserMapperCustom {
    List<User> findUsersByCondition(@Param("keyword") String keyword);
}
```

对应的 XML：

```xml
<!-- src/main/resources/mapper/UserMapper.xml -->
<mapper namespace="com.example.mapper.UserMapperCustom">
    <select id="findUsersByCondition" resultType="com.example.entity.User">
        SELECT * FROM sys_user
        <where>
            <if test="keyword != null and keyword != ''">
                AND (username LIKE CONCAT('%', #{keyword}, '%')
                OR email LIKE CONCAT('%', #{keyword}, '%'))
            </if>
        </where>
    </select>
</mapper>
```

## 常见问题

### 问题1：编译后没有生成代码

**症状**：标注了 `@AutoMybatis`，但编译后没有生成对应的 Mapper、Service

**可能原因**：

1. 依赖未正确配置（缺少 `provided` 作用域）
2. IDE 未启用注解处理器
3. Maven 编译插件配置禁用了注解处理
4. 未继承 Parent、在 JDK 23 及以上编译且未开启注解处理

**解决方案**：

1. **检查依赖配置**：

```xml
<dependency>
    <groupId>io.github.yggdrasil-labs</groupId>
    <artifactId>mimir-boot-starter-mybatis-processor</artifactId>
    <version>YOUR_RELEASE_VERSION</version>
    <scope>provided</scope>  <!-- 必须使用 provided -->
</dependency>
```

1. **检查 Maven 编译插件配置**：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <source>17</source>
        <target>17</target>
        <!-- 不要设置 proc:none，这会禁用注解处理 -->
    </configuration>
</plugin>
```

未继承 Parent 且使用 JDK 23 及以上编译时，还需设置 `<maven.compiler.proc>full</maven.compiler.proc>` 或在 `compilerArgs` 中传 `-proc:full`，推荐写法见[编译器版本与注解处理开关](#编译器版本与注解处理开关)。

1. **IDE 配置（IntelliJ IDEA）**：
   - Settings → Build, Execution, Deployment → Compiler → Annotation Processors
   - 勾选 "Enable annotation processing"

2. **手动触发编译**：

```bash
mvn clean compile
```

### 问题2：生成的代码位置不正确

**症状**：生成的代码不在预期的包路径下

**解决方案**：

1. 检查 `@AutoMybatis` 注解的配置
2. 确认实体类的包路径
3. 生成的代码位置为：`实体类包名 + 配置的 package`

### 问题3：重新编译后自定义代码丢失

**症状**：在生成的类中添加了自定义方法，重新编译后丢失

**原因**：生成的代码会在每次编译时重新生成，覆盖之前的修改

**解决方案**：

1. **不要直接修改生成的类**
2. **创建扩展类**（推荐）：

```java
// 不要修改生成的 UserServiceImpl
// 而是创建扩展类

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class UserServiceExt extends UserServiceImpl {
    // 添加自定义方法
    public User findByUsername(String username) {
        return lambdaQuery()
            .eq(User::getUsername, username)
            .one();
    }
}
```

### 问题4：多个实体类生成冲突

**症状**：编译报重复类型或无法创建生成文件的错误。

**原因**：只有“最终生成包 + 去除 `DO` 后缀后的类名 + 配置后缀”完全相同时才冲突。处理器不会覆盖、跳过或自动重命名，重复全限定名直接导致编译失败。

- 不冲突：同包的 `User`、`Order` 使用同一组包与后缀，生成 `UserMapper`、`OrderMapper` 等六个不同类型。
- 冲突：同包的 `UserDO` 与 `User` 使用相同生成包和后缀，都会生成 `UserMapper`、`UserService`、`UserServiceImpl`。

**解决方案**：对冲突实体之一，让 Mapper、Service、ServiceImpl 三类产物都不再重名。只调整 Mapper 不能避让 Service 与 ServiceImpl 的冲突。

```java
// 两者同包，各自三类产物均不重名
@AutoMybatis(
        mapperPackage = "legacy.mapper",
        serviceSuffix = "LegacyService",
        serviceImplSuffix = "LegacyServiceImpl")
public class UserDO { }

@AutoMybatis
public class User { }
```

## 与手动编写代码的对比

### 手动编写方式

```java
// 需要手动编写 3 个类，每个实体类都需要重复类似代码

// 1. UserMapper.java
@Mapper
public interface UserMapper extends BaseMapper<User> {
}

// 2. UserService.java
public interface UserService extends IService<User> {
}

// 3. UserServiceImpl.java
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
}
```

### 使用 Processor 方式

```java
// 只需要在实体类上标注注解
@AutoMybatis
public class User {
    // ...
}

// 编译后自动生成上述 3 个类
```

**优势**：

- ✅ 减少样板代码
- ✅ 统一代码风格
- ✅ 降低出错概率
- ✅ 提高开发效率

## 技术栈

- **JavaPoet** - 代码生成库
- **MyBatis-Plus** - ORM 框架
- **Java Annotation Processing** - 编译期代码生成
- **Maven** - 构建工具
- **Java**: 17+（已实测 JDK 17、21；JDK 25 未验证）

## 📚 相关文档

- [Mimir Boot 项目主页](../../README.md) - 项目总体说明
- [Mimir Boot Starter MyBatis](../mimir-boot-starter-mybatis/README.md) - MyBatis 启动器（配合使用）
- [Mimir Boot Common](../../mimir-boot-common/README.md) - 公共组件说明

## 许可证

Apache License 2.0

## 作者

Yggdrasil Labs
