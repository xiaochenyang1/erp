# ERP Phase 0 Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将当前空的 Maven Java 项目重构为可启动的 Spring Boot ERP 后端基础工程，并落下数据库初始化脚本与版本迁移骨架，为后续平台、主数据和业务模块开发提供稳定底座。

**Architecture:** 采用模块化单体基础骨架，先建立统一启动入口、配置体系、通用返回结构、异常处理、健康检查、配置绑定和数据库脚本目录。数据库脚本先覆盖系统基础表和初始化数据骨架，同时引入 Flyway 作为后续演进入口。

**Tech Stack:** Java 17, Spring Boot 3.x, Maven, Spring Web, Validation, Actuator, Security, Redis, MySQL 8, MyBatis-Plus, Flyway, JUnit 5, MockMvc

---

### Task 1: Bootstrap Spring Boot Application

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/tuowei/erp/ErpServerApplication.java`
- Create: `src/test/java/com/tuowei/erp/ErpServerApplicationTests.java`

- [x] **Step 1: Write the failing context-load test**

```java
package com.tuowei.erp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ErpServerApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=ErpServerApplicationTests test
```

Expected: FAIL with missing Spring Boot dependencies or missing application bootstrap class.

- [x] **Step 3: Write minimal implementation**

Update `pom.xml` to:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.tuowei</groupId>
    <artifactId>erp-server</artifactId>
    <version>1.0-SNAPSHOT</version>
    <name>erp-server</name>
    <description>ERP backend foundation</description>

    <properties>
        <java.version>17</java.version>
        <mybatis-plus.version>3.5.7</mybatis-plus.version>
        <springdoc.version>2.6.0</springdoc.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-mysql</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

Create `src/main/java/com/tuowei/erp/ErpServerApplication.java`:

```java
package com.tuowei.erp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.tuowei.erp.**.mapper")
public class ErpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ErpServerApplication.class, args);
    }
}
```

- [x] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=ErpServerApplicationTests test
```

Expected: PASS with `ErpServerApplicationTests` green.

- [x] **Step 5: Commit**

```powershell
git add pom.xml src/main/java/com/tuowei/erp/ErpServerApplication.java src/test/java/com/tuowei/erp/ErpServerApplicationTests.java
git commit -m "feat: bootstrap spring boot foundation"
```

### Task 2: Add Common API Envelope and Health Endpoint

**Files:**
- Create: `src/main/java/com/tuowei/erp/common/web/ApiResponse.java`
- Create: `src/main/java/com/tuowei/erp/common/exception/GlobalExceptionHandler.java`
- Create: `src/main/java/com/tuowei/erp/system/controller/HealthController.java`
- Create: `src/test/java/com/tuowei/erp/system/controller/HealthControllerTest.java`

- [x] **Step 1: Write the failing web test**

```java
package com.tuowei.erp.system.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(com.tuowei.erp.common.exception.GlobalExceptionHandler.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReturnsUnifiedResponse() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=HealthControllerTest test
```

Expected: FAIL because `HealthController`, `ApiResponse`, or unified response structure does not exist.

- [x] **Step 3: Write minimal implementation**

Create `src/main/java/com/tuowei/erp/common/web/ApiResponse.java`:

```java
package com.tuowei.erp.common.web;

public record ApiResponse<T>(String code, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("0", "success", data);
    }
}
```

Create `src/main/java/com/tuowei/erp/common/exception/GlobalExceptionHandler.java`:

```java
package com.tuowei.erp.common.exception;

import com.tuowei.erp.common.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<String> handleValidation(MethodArgumentNotValidException ex) {
        return new ApiResponse<>("400", ex.getBindingResult().getAllErrors().get(0).getDefaultMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<String> handleUnexpected(Exception ex) {
        return new ApiResponse<>("500", ex.getMessage(), null);
    }
}
```

Create `src/main/java/com/tuowei/erp/system/controller/HealthController.java`:

```java
package com.tuowei.erp.system.controller;

import com.tuowei.erp.common.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.success(Map.of("status", "UP"));
    }
}
```

- [x] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=HealthControllerTest test
```

Expected: PASS with unified response structure asserted.

- [x] **Step 5: Commit**

```powershell
git add src/main/java/com/tuowei/erp/common/web/ApiResponse.java src/main/java/com/tuowei/erp/common/exception/GlobalExceptionHandler.java src/main/java/com/tuowei/erp/system/controller/HealthController.java src/test/java/com/tuowei/erp/system/controller/HealthControllerTest.java
git commit -m "feat: add common api response and health endpoint"
```

### Task 3: Add Configuration Baseline and Typed App Properties

**Files:**
- Create: `src/main/java/com/tuowei/erp/common/config/AppProperties.java`
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/application-dev.yml`
- Create: `src/main/resources/application-test.yml`
- Modify: `src/main/java/com/tuowei/erp/ErpServerApplication.java`
- Create: `src/test/java/com/tuowei/erp/common/config/AppPropertiesTest.java`

- [x] **Step 1: Write the failing properties binding test**

```java
package com.tuowei.erp.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@EnableConfigurationProperties(AppProperties.class)
class AppPropertiesTest {

    @Autowired
    private AppProperties appProperties;

    @Test
    void bindsApplicationMetadataFromProfile() {
        assertThat(appProperties.code()).isEqualTo("erp-server");
        assertThat(appProperties.name()).isEqualTo("ERP Server");
        assertThat(appProperties.timezone()).isEqualTo("Asia/Shanghai");
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=AppPropertiesTest test
```

Expected: FAIL because `AppProperties` or profile configuration does not exist.

- [x] **Step 3: Write minimal implementation**

Create `src/main/java/com/tuowei/erp/common/config/AppProperties.java`:

```java
package com.tuowei.erp.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "erp.app")
public record AppProperties(String code, String name, String timezone) {
}
```

Update `src/main/java/com/tuowei/erp/ErpServerApplication.java`:

```java
package com.tuowei.erp;

import com.tuowei.erp.common.config.AppProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@MapperScan("com.tuowei.erp.**.mapper")
@ConfigurationPropertiesScan(basePackageClasses = AppProperties.class)
public class ErpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ErpServerApplication.class, args);
    }
}
```

Create `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: erp-server
  profiles:
    active: dev
  jackson:
    time-zone: Asia/Shanghai
    serialization:
      write-dates-as-timestamps: false
  mvc:
    problemdetails:
      enabled: false

server:
  port: 8080

erp:
  app:
    code: erp-server
    name: ERP Server
    timezone: Asia/Shanghai
```

Create `src/main/resources/application-dev.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/erp_server?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: root
  data:
    redis:
      host: 127.0.0.1
      port: 6379
  flyway:
    enabled: true
    locations: classpath:db/migration
```

Create `src/main/resources/application-test.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:erp_server;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false
    driver-class-name: org.h2.Driver
    username: sa
    password:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
  flyway:
    enabled: false
```

- [x] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=AppPropertiesTest test
```

Expected: PASS with profile-bound metadata assertions green.

- [x] **Step 5: Commit**

```powershell
git add src/main/java/com/tuowei/erp/common/config/AppProperties.java src/main/java/com/tuowei/erp/ErpServerApplication.java src/main/resources/application.yml src/main/resources/application-dev.yml src/main/resources/application-test.yml src/test/java/com/tuowei/erp/common/config/AppPropertiesTest.java
git commit -m "feat: add baseline application configuration"
```

### Task 4: Add Database Initialization Script Skeleton and Flyway Baseline

**Files:**
- Create: `db/init/01_create_database.sql`
- Create: `db/init/02_create_tables.sql`
- Create: `db/init/03_create_indexes.sql`
- Create: `db/init/04_init_dict_and_config.sql`
- Create: `db/init/05_init_security.sql`
- Create: `db/init/06_init_workflow.sql`
- Create: `db/init/07_init_finance.sql`
- Create: `src/main/resources/db/migration/V1__base_schema.sql`
- Create: `src/test/java/com/tuowei/erp/db/DbScriptLayoutTest.java`

- [x] **Step 1: Write the failing script layout test**

```java
package com.tuowei.erp.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DbScriptLayoutTest {

    @Test
    void requiredInitializationScriptsExist() {
        assertThat(Files.exists(Path.of("db/init/01_create_database.sql"))).isTrue();
        assertThat(Files.exists(Path.of("db/init/02_create_tables.sql"))).isTrue();
        assertThat(Files.exists(Path.of("db/init/03_create_indexes.sql"))).isTrue();
        assertThat(Files.exists(Path.of("db/init/04_init_dict_and_config.sql"))).isTrue();
        assertThat(Files.exists(Path.of("db/init/05_init_security.sql"))).isTrue();
        assertThat(Files.exists(Path.of("db/init/06_init_workflow.sql"))).isTrue();
        assertThat(Files.exists(Path.of("db/init/07_init_finance.sql"))).isTrue();
        assertThat(Files.exists(Path.of("src/main/resources/db/migration/V1__base_schema.sql"))).isTrue();
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=DbScriptLayoutTest test
```

Expected: FAIL because script files do not exist.

- [x] **Step 3: Write minimal implementation**

Create `db/init/01_create_database.sql`:

```sql
CREATE DATABASE IF NOT EXISTS erp_server
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_0900_ai_ci;
```

Create `db/init/02_create_tables.sql`:

```sql
USE erp_server;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL PRIMARY KEY,
    company_id BIGINT NOT NULL DEFAULT 1,
    account_book_id BIGINT NOT NULL DEFAULT 1,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(255) NOT NULL,
    real_name VARCHAR(64) NOT NULL,
    mobile VARCHAR(32),
    dept_id BIGINT,
    post_id BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT NOT NULL PRIMARY KEY,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT NOT NULL PRIMARY KEY,
    parent_id BIGINT NOT NULL DEFAULT 0,
    menu_type VARCHAR(32) NOT NULL,
    menu_code VARCHAR(64) NOT NULL,
    menu_name VARCHAR(64) NOT NULL,
    path VARCHAR(255),
    component VARCHAR(255),
    permission VARCHAR(128),
    sort_no INT NOT NULL DEFAULT 0,
    visible_flag TINYINT NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_role_menu (
    id BIGINT NOT NULL PRIMARY KEY,
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT NOT NULL PRIMARY KEY,
    config_code VARCHAR(64) NOT NULL,
    config_name VARCHAR(64) NOT NULL,
    config_value VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    deleted_flag TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_sequence_rule (
    id BIGINT NOT NULL PRIMARY KEY,
    biz_type VARCHAR(64) NOT NULL,
    prefix VARCHAR(32) NOT NULL,
    date_pattern VARCHAR(32) NOT NULL,
    seq_length INT NOT NULL,
    current_value BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL DEFAULT 0,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 0
);
```

Create `db/init/03_create_indexes.sql`:

```sql
USE erp_server;

CREATE UNIQUE INDEX uk_sys_user_username ON sys_user (username);
CREATE UNIQUE INDEX uk_sys_role_role_code ON sys_role (role_code);
CREATE UNIQUE INDEX uk_sys_menu_menu_code ON sys_menu (menu_code);
CREATE UNIQUE INDEX uk_sys_config_config_code ON sys_config (config_code);
CREATE UNIQUE INDEX uk_sys_sequence_rule_biz_type ON sys_sequence_rule (biz_type);
CREATE INDEX idx_sys_user_dept_id ON sys_user (dept_id);
CREATE INDEX idx_sys_user_role_user_id ON sys_user_role (user_id);
CREATE INDEX idx_sys_user_role_role_id ON sys_user_role (role_id);
CREATE INDEX idx_sys_role_menu_role_id ON sys_role_menu (role_id);
CREATE INDEX idx_sys_role_menu_menu_id ON sys_role_menu (menu_id);
```

Create `db/init/04_init_dict_and_config.sql`:

```sql
USE erp_server;

INSERT INTO sys_config (id, config_code, config_name, config_value)
VALUES
    (1001, 'erp.default.password', '默认密码', 'ChangeMe123!'),
    (1002, 'erp.stock.allow-negative', '是否允许负库存', 'false'),
    (1003, 'erp.approval.enabled', '审批开关', 'true');

INSERT INTO sys_sequence_rule (id, biz_type, prefix, date_pattern, seq_length, current_value)
VALUES
    (2001, 'PURCHASE_ORDER', 'PO', 'yyyyMMdd', 4, 0),
    (2002, 'SALES_ORDER', 'SO', 'yyyyMMdd', 4, 0),
    (2003, 'STOCK_ADJUST', 'IA', 'yyyyMMdd', 4, 0);
```

Create `db/init/05_init_security.sql`:

```sql
USE erp_server;

INSERT INTO sys_role (id, role_code, role_name)
VALUES
    (3001, 'SUPER_ADMIN', '超级管理员'),
    (3002, 'ERP_ADMIN', '企业管理员');

INSERT INTO sys_user (id, username, password, real_name, status)
VALUES
    (4001, 'admin', '$2a$10$7EqJtq98hPqEX7fNZaFWoOHiA2Rv4RANQ0H3TnYx1p8g4a9e6fW7K', '系统管理员', 'ACTIVE');

INSERT INTO sys_menu (id, parent_id, menu_type, menu_code, menu_name, path, component, permission, sort_no)
VALUES
    (5001, 0, 'CATALOG', 'SYSTEM', '系统管理', '/system', 'Layout', null, 1),
    (5002, 5001, 'MENU', 'SYSTEM_USER', '用户管理', '/system/users', 'system/user/index', 'system:user:view', 1),
    (5003, 5001, 'MENU', 'SYSTEM_ROLE', '角色管理', '/system/roles', 'system/role/index', 'system:role:view', 2);

INSERT INTO sys_user_role (id, user_id, role_id)
VALUES
    (6001, 4001, 3001);

INSERT INTO sys_role_menu (id, role_id, menu_id)
VALUES
    (7001, 3001, 5001),
    (7002, 3001, 5002),
    (7003, 3001, 5003);
```

Create `db/init/06_init_workflow.sql`:

```sql
USE erp_server;

CREATE TABLE IF NOT EXISTS wf_approval_definition (
    id BIGINT NOT NULL PRIMARY KEY,
    biz_type VARCHAR(64) NOT NULL,
    flow_name VARCHAR(64) NOT NULL,
    enabled_flag TINYINT NOT NULL DEFAULT 1,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_approval_node_definition (
    id BIGINT NOT NULL PRIMARY KEY,
    definition_id BIGINT NOT NULL,
    node_no INT NOT NULL,
    approver_role_code VARCHAR(64) NOT NULL,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO wf_approval_definition (id, biz_type, flow_name, enabled_flag)
VALUES
    (8001, 'PURCHASE_ORDER', '采购订单审批', 1),
    (8002, 'SALES_ORDER', '销售订单审批', 1);

INSERT INTO wf_approval_node_definition (id, definition_id, node_no, approver_role_code)
VALUES
    (8101, 8001, 1, 'ERP_ADMIN'),
    (8102, 8002, 1, 'ERP_ADMIN');
```

Create `db/init/07_init_finance.sql`:

```sql
USE erp_server;

CREATE TABLE IF NOT EXISTS fin_account_subject (
    id BIGINT NOT NULL PRIMARY KEY,
    subject_code VARCHAR(64) NOT NULL,
    subject_name VARCHAR(128) NOT NULL,
    subject_level INT NOT NULL,
    direction VARCHAR(16) NOT NULL,
    parent_id BIGINT NOT NULL DEFAULT 0,
    enabled_flag TINYINT NOT NULL DEFAULT 1,
    created_by BIGINT NOT NULL DEFAULT 0,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO fin_account_subject (id, subject_code, subject_name, subject_level, direction, parent_id)
VALUES
    (9001, '1001', '库存现金', 1, 'DEBIT', 0),
    (9002, '1122', '应收账款', 1, 'DEBIT', 0),
    (9003, '2202', '应付账款', 1, 'CREDIT', 0),
    (9004, '6001', '主营业务收入', 1, 'CREDIT', 0);
```

Create `src/main/resources/db/migration/V1__base_schema.sql`:

```sql
CREATE TABLE sys_bootstrap_marker (
    id BIGINT NOT NULL PRIMARY KEY,
    marker_code VARCHAR(64) NOT NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

- [x] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=DbScriptLayoutTest test
```

Expected: PASS with all required script files present.

- [x] **Step 5: Commit**

```powershell
git add db/init src/main/resources/db/migration/V1__base_schema.sql src/test/java/com/tuowei/erp/db/DbScriptLayoutTest.java
git commit -m "feat: add database initialization skeleton"
```

### Task 5: Add Baseline Security Rules for Phase 0

**Files:**
- Create: `src/main/java/com/tuowei/erp/common/security/SecurityConfig.java`
- Create: `src/main/java/com/tuowei/erp/system/controller/ProfileController.java`
- Create: `src/test/java/com/tuowei/erp/common/security/SecurityConfigTest.java`

- [x] **Step 1: Write the failing security behavior test**

```java
package com.tuowei.erp.common.security;

import com.tuowei.erp.system.controller.HealthController;
import com.tuowei.erp.system.controller.ProfileController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({HealthController.class, ProfileController.class})
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void permitsHealthButProtectsProfileEndpoint() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/system/profile"))
                .andExpect(status().isUnauthorized());
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=SecurityConfigTest test
```

Expected: FAIL because `SecurityConfig` or protected endpoint does not exist.

- [x] **Step 3: Write minimal implementation**

Create `src/main/java/com/tuowei/erp/common/security/SecurityConfig.java`:

```java
package com.tuowei.erp.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/health").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }
}
```

Create `src/main/java/com/tuowei/erp/system/controller/ProfileController.java`:

```java
package com.tuowei.erp.system.controller;

import com.tuowei.erp.common.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class ProfileController {

    @GetMapping("/profile")
    public ApiResponse<Map<String, String>> profile() {
        return ApiResponse.success(Map.of("scope", "protected"));
    }
}
```

- [x] **Step 4: Run test to verify it passes**

Run:

```powershell
mvn "-Dmaven.repo.local=E:\tuowei\python\erpServer\.m2\repository" -Dtest=SecurityConfigTest test
```

Expected: PASS with health anonymous access and protected endpoint 401.

- [x] **Step 5: Commit**

```powershell
git add src/main/java/com/tuowei/erp/common/security/SecurityConfig.java src/main/java/com/tuowei/erp/system/controller/ProfileController.java src/test/java/com/tuowei/erp/common/security/SecurityConfigTest.java
git commit -m "feat: add baseline security configuration"
```

