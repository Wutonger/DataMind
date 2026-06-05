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

## 关键流程实现

### 智能执行模块

智能执行是系统的核心功能，通过自然语言对话完成数据库分析、知识检索、报表生成等任务。

#### 执行流程

1. **用户提交任务**：绑定当前会话与连接上下文
2. **知识库检索**：根据问题检索相关文档片段，补充业务背景
3. **Agent 编排**：根据任务自动选择数据库工具、知识库工具或报表保存工具
4. **流式输出**：过程通过 SSE 流式返回给前端
5. **结果保存**：结果、步骤和引用信息写入历史记录
6. **链路追踪**：Workflow 记录整条执行链路

#### Skill 资源

智能执行通过 Skill 组织业务约束和专业提示：

- **knowledge-grounding**：知识库检索，补充业务背景信息
- **artifact-generation**：报表生成，保存分析结果
- **insight-discovery**：数据洞察，发现数据规律

**知识库检索 Skill**：
- 相似度阈值：0.5（低于此值的片段被过滤）
- 文档数量：最多返回 2 个文档
- 片段数量：每个文档最多 2 个片段
- 排序方式：按相似度降序，优先返回高分片段
- 实现方式：计算查询向量与片段向量的余弦相似度，按文档分组取高分片段

#### MCP 资源

智能执行通过 MCP (Model Context Protocol) 暴露数据库工具，由独立的 MCP Server 提供：

- **listTables**：列出当前连接下的所有表
- **getColumns**：获取指定表的字段信息
- **getSchemaText**：获取数据库 schema 文本描述
- **executeQuery**：执行只读 SQL 查询

**MCP 架构**：
- MCP Server：独立进程，端口 8081，通过 SSE 暴露工具
- MCP Client：封装在 `data-analysis-mcp-client` 模块，与 Server 通信
- 工具调用：Agent 根据任务需要，通过 Client 调用 Server 暴露的工具

#### 对话压缩

在长对话场景下自动压缩历史，控制上下文长度。

**压缩策略**：
- 触发条件：对话消息 ≥ 6 条
- 保留策略：保留最近 4 条消息（2 轮对话）
- 压缩方式：更早的消息生成摘要，作为 SystemMessage 放在对话开头
- 原始消息：被压缩的原始消息完整保存，前端可展开查看

**实现细节**：
- 后端：`MessageCompressor.java` 实现滑动窗口压缩
- 数据库：`chat_sessions.compressed_messages` 保存被压缩的原始消息
- 前端：在压缩位置显示分隔线，支持展开查看摘要和原始消息

**压缩效果**：
```
[用户] 早期消息 1
[助手] 早期回复 1
[用户] 早期消息 2
[助手] 早期回复 2
─────────────────────────────────────────
        之前的对话已压缩  2024-01-15 10:30  >
─────────────────────────────────────────
[用户] 最近消息 1
[助手] 最近回复 1
[用户] 最近消息 2
[助手] 最近回复 2
```

#### 执行链路追踪

记录智能执行的运行过程，便于审计和调试。

**三层结构**：
- `workflow_runs`：运行记录（整体执行）
- `workflow_steps`：步骤记录（工具调用、子任务）
- `workflow_timeline`：时间线记录（关键节点）

**追踪内容**：
- 执行类型（智能执行、SQL、表分析、报表生成）
- 触发用户和连接上下文
- 每个步骤的输入、输出、状态、耗时
- 关键节点的时间戳和描述

### 权限控制

系统基于 Sa-Token 实现登录认证和权限控制。

**权限模型**：
- 管理员：可访问所有功能，包括用户管理、连接管理、知识库管理、系统设置
- 普通用户：仅可访问被授权的连接，在授权范围内使用分析能力

**数据隔离**：
- 聊天会话、SQL 历史、报表按 `user_id` 隔离
- 连接访问通过 `connection_user_access` 表控制
- 当前连接上下文保存在用户的 `last_connection_id`

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
