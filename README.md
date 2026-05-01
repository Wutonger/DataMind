# DataMind AI

DataMind AI 是一套面向企业数据分析场景的智能分析平台，覆盖数据库连接管理、表结构分析、自然语言转 SQL、智能问答、知识库检索、图表与文档报表生成，以及执行链路追踪等核心能力。

平台以统一的 Agent 编排层为中心，通过 MCP Tool、Skill、知识库与业务服务协同工作，让用户可以在同一套界面中完成“连接数据源、理解库表、提问分析、生成 SQL、沉淀知识、输出报表”的完整流程。

## 核心能力

- 数据库连接管理：维护多数据源连接，按当前连接切换分析上下文。
- 表结构分析：扫描全库表结构，生成中文业务描述，并分析表关系。
- SQL 工作台：自然语言生成 SQL、执行 SQL、记录生成历史。
- 智能问答：支持流式问答，可按需调用数据库工具、知识库工具和报表保存工具。
- 知识库 V1：支持 `PDF`、`TXT`、`Markdown`、`DOCX` 上传、切块、检索、预览和引用跳转。
- 报表中心：支持图表报表与 Markdown 文档报告，并可下载 PDF。
- 执行链路：记录问答、SQL、表分析、报表生成过程中的运行轨迹与步骤明细。
- 系统设置：维护语言模型、向量模型、温度等应用级配置。

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
- MCP（Server + Client）

## 系统架构

### 前端页面

- `Dashboard`：首页统计概览
- `Connections`：连接管理
- `Analysis`：表结构分析
- `SqlStudio`：SQL 工作台
- `Chat`：智能问答
- `Reports`：报表中心
- `Knowledge`：知识库
- `Workflow`：执行链路
- `Settings`：系统设置

### 后端模块

- `data-analysis-common`：公共常量、工具、基础模型
- `data-analysis-mcp-server`：独立 MCP Server，暴露数据库工具
- `data-analysis-mcp-client`：MCP Client 封装
- `data-analysis-skills`：Skill 资源与保存型工具
- `data-analysis-agent`：Agent 编排、工具拦截、执行链路追踪
- `data-analysis-core`：连接、聊天、知识库、报表、分析等核心业务
- `data-analysis-web`：REST API 与应用启动入口

### Skill 目录

当前 Skill 资源位于 `data-analysis-backend/data-analysis-skills/src/main/resources/skills`：

- `knowledge-grounding`
- `artifact-generation`
- `insight-discovery`

## 目录结构

```text
data_analysis/
├── data-analysis-backend/
│   ├── data-analysis-agent/
│   ├── data-analysis-common/
│   ├── data-analysis-core/
│   ├── data-analysis-mcp-client/
│   ├── data-analysis-mcp-server/
│   ├── data-analysis-skills/
│   ├── data-analysis-web/
│   └── storage/
├── data-analysis-frontend/
├── sql/
│   └── init.sql
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

- [init.sql](/D:/data_analysis/sql/init.sql)

脚本包含以下核心表：

- 连接管理：`connections`
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

- [application.yml](/D:/data_analysis/data-analysis-backend/data-analysis-web/src/main/resources/application.yml)

建议至少确认以下项：

- `spring.datasource.*`
- `spring.ai.openai.api-key`
- `server.port`

### MCP Server

默认配置文件：

- [application.yml](/D:/data_analysis/data-analysis-backend/data-analysis-mcp-server/src/main/resources/application.yml)

默认端口为 `8081`，SSE 端点为：

- `/sse`
- `/mcp/message`

建议通过环境变量覆盖数据库敏感信息，例如：

- `MAIN_DB_URL`
- `MAIN_DB_USER`
- `MAIN_DB_PASSWORD`
- `OPENAI_API_KEY`

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

## V1 范围说明

- 知识库 V1 支持 `PDF`、`TXT`、`Markdown`、`DOCX`
- 报表生成链路约束为“单次报表保存只允许一条 SQL”
- 执行链路用于展示运行过程、步骤和关键节点信息

## 相关文档

- [PROJECT_PLAN.md](/D:/data_analysis/PROJECT_PLAN.md)
- [TASKS.md](/D:/data_analysis/TASKS.md)
