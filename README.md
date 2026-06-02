# DataMind AI

DataMind AI 是一套面向企业数据分析场景的智能分析平台，聚焦数据库理解、自然语言分析、知识增强、报表输出、权限控制与执行过程追踪，帮助用户在统一界面中完成从“登录系统、选择授权数据源”到“输出分析结果”的主要工作。

## 功能概览

- 数据库连接管理：维护多数据源连接，并按当前连接切换分析上下文。
- 用户与权限：支持登录认证、用户管理、账号状态控制、数据库连接授权与按用户的数据隔离。
- 表结构分析：扫描全库表结构，生成中文业务描述，并分析表关系。
- SQL 工作台：根据自然语言生成 SQL，执行查询并记录生成历史。
- 智能执行：支持流式对话执行，可按需调用数据库工具、知识库工具和报表保存工具。
- 知识库：支持 `PDF`、`TXT`、`Markdown`、`DOCX` 文档上传、切块、检索、预览和引用跳转。
- 报表中心：支持图表报表与 Markdown 文档报告，并支持 PDF 下载。
- 执行链路：记录智能执行、SQL、表分析、报表生成过程中的运行轨迹与步骤明细。
- 系统设置：维护语言模型、向量模型、温度等应用级配置。

## 适用场景

- 快速理解陌生数据库中的表结构与业务含义
- 通过自然语言完成 SQL 生成与数据查询
- 在多用户场景下为不同账号分配可访问的数据连接
- 将业务文档、指标口径和分析说明纳入知识库辅助智能执行
- 生成图表报表和文档报告，沉淀分析结果
- 查看智能分析过程中的执行步骤与链路信息

## 技术栈

### 前端

- Vue 3
- TypeScript
- Vite
- Naive UI
- Pinia
- ECharts

### 后端

- Spring Boot 3.5
- Spring AI 1.1
- Spring AI Alibaba 1.1
- Spring Data JPA
- MySQL 8
- Sa-Token
- MCP（Server + Client）

## 页面结构

- `Login`：登录页
- `Dashboard`：首页统计概览
- `Connections`：连接管理与当前连接选择
- `Chat`：智能执行
- `SqlStudio`：SQL 工作台
- `Reports`：报表中心
- `Analysis`：表结构分析
- `Knowledge`：知识库（管理员）
- `Workflow`：执行链路
- `Settings`：系统设置（管理员）
- `Users`：用户与权限（管理员）

## 代码结构

### 后端模块

- `data-analysis-common`：公共常量、工具类、基础模型
- `data-analysis-mcp-server`：独立 MCP Server，暴露数据库工具
- `data-analysis-mcp-client`：MCP Client 封装
- `data-analysis-skills`：Skill 资源与保存型工具
- `data-analysis-agent`：Agent 编排、工具拦截、执行链路追踪
- `data-analysis-core`：连接、聊天、知识库、报表、分析等核心业务
- `data-analysis-web`：REST API 与应用启动入口

### Skill 资源

Skill 资源位于 `data-analysis-backend/data-analysis-skills/src/main/resources/skills`：

- `knowledge-grounding`
- `artifact-generation`
- `insight-discovery`

### 目录结构

```text
data_analysis/
├── data-analysis-backend/
│   ├── data-analysis-agent/
│   ├── data-analysis-common/
│   ├── data-analysis-core/
│   ├── data-analysis-mcp-client/
│   ├── data-analysis-mcp-server/
│   ├── data-analysis-skills/
│   └── data-analysis-web/
├── data-analysis-frontend/
├── sql/
│   └── init.sql
├── storage/
├── PROJECT_PLAN.md
├── TASKS.md
└── README.md
```

## 环境要求

- JDK 17
- Maven 3.8+
- Node.js 18+
- MySQL 8.0+

如果本机默认是 JDK 8，需要在启动前显式切换 `JAVA_HOME` 到 JDK 17。

## 数据库初始化

项目初始化脚本位于：

- `sql/init.sql`

脚本包含以下核心表：

- 连接管理：`connections`
- 用户与权限：`sys_user`、`connection_user_access`
- 表分析：`table_metadata`
- 聊天会话：`chat_sessions`
- 知识库：`knowledge_documents`、`document_chunks`
- 报表中心：`reports`
- SQL 生成历史：`sql_history`
- 执行链路：`workflow_runs`、`workflow_steps`、`workflow_timeline`
- 系统配置：`app_config`

## 配置说明

### Web 应用

默认配置文件：

- `data-analysis-backend/data-analysis-web/src/main/resources/application.yml`

建议至少确认以下配置：

- `spring.datasource.*`
- `server.port`

### MCP Server

默认配置文件：

- `data-analysis-backend/data-analysis-mcp-server/src/main/resources/application.yml`

默认端口为 `8081`，SSE 端点为：

- `/sse`
- `/mcp/message`

建议通过环境变量覆盖敏感配置，例如：

- `MAIN_DB_URL`
- `MAIN_DB_USER`
- `MAIN_DB_PASSWORD`

## 权限说明

- 系统登录基于 `sys_user` 数据表与 Sa-Token 会话实现。
- 管理员可以维护用户、启停账号、配置系统参数、管理知识库，并为普通用户授权可访问的数据库连接。
- 普通用户可以登录系统、查看自己被授权的连接，并在授权范围内使用智能执行、SQL 工作台、报表中心和表分析能力。
- 聊天会话、SQL 生成历史、报表数据均按 `user_id` 隔离保存；执行链路保留触发用户信息用于运行追踪。
- 当前连接选择会回写到用户的 `last_connection_id`，用于下次登录后的上下文恢复。

## 使用说明

- 首次使用前，请确保 `sys_user` 中已准备可登录账号。
- 普通用户如果未被授权任何连接，登录后将无法访问数据库分析相关能力。

## 启动顺序

推荐按下面顺序启动：

### 1. 启动 MCP Server

```bash
cd data-analysis-backend
mvn -pl data-analysis-mcp-server spring-boot:run
```

### 2. 启动 Web 后端

```bash
cd data-analysis-backend
mvn -pl data-analysis-web -am spring-boot:run
```

### 3. 启动前端

```bash
cd data-analysis-frontend
npm install
npm run dev
```

## 默认访问地址

- 前端：[http://localhost:3000](http://localhost:3000)
- Web 后端：[http://localhost:8080](http://localhost:8080)
- MCP Server：[http://localhost:8081](http://localhost:8081)

## 常用构建命令

### 后端编译

```bash
cd data-analysis-backend
mvn -pl data-analysis-web -am -DskipTests compile
```

### 前端构建

```bash
cd data-analysis-frontend
npm run build
```

## 当前支持

- 知识库支持 `PDF`、`TXT`、`Markdown`、`DOCX`
- 执行链路支持展示运行过程、步骤和关键节点信息

## 相关文档

- `PROJECT_PLAN.md`
- `TASKS.md`
