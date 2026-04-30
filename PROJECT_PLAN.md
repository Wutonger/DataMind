# AI 数据分析平台 - 项目规划

## 1. 项目概述

一个基于 AI 的智能数据分析平台，前端使用 Vue 3，后端使用 Spring AI + Spring Boot。用户可连接任意数据库，通过自然语言与 AI 交互完成数据分析、SQL 生成、可视化报表生成等任务。

**技术栈：**
- 前端：Vue 3 + Vite + TypeScript + ECharts + Pinia
- 后端：Spring Boot 3.x + Spring AI 1.x + MCP
- 数据库： MySQL 8.0（元数据存储）+ 支持多类型数据源

---

## 2. 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         Vue 3 Frontend                          │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │
│  │Dashboard│ │Connection│ │Analysis │ │  Chat   │ │ Report  │   │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘   │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Spring Boot Backend                         │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │           Agent Orchestrator (意图识别 + 智能路由)        │    │
│  └─────────────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                    ChatClient + Advisors                 │    │
│  │              (Memory, Compression, RAG)                  │    │
│  └─────────────────────────────────────────────────────────┘    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ MCP Client   │  │ Skills Frame │  │ Chat Memory  │      │
│  │ (协议客户端)  │  │ (业务技能)   │  │ (压缩记忆)   │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │              Multi-Datasource Routing                     │ │
│  │     MySQL | PostgreSQL | Oracle | SQL Server | ...        │ │
│  └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      MCP Server (独立服务)                       │
│  - 标准化工具暴露                                               │
│  - 数据库连接管理                                               │
│  - 跨数据库支持                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. 功能模块

### 3.1 数据库连接管理
- 支持 MySQL、PostgreSQL、Oracle、SQL Server、ClickHouse 等主流数据库
- 动态数据源路由
- 连接池管理 (HikariCP)
- 连接测试与健康检查

### 3.2 表结构分析 (AI)
- 自动扫描数据库所有表
- AI 分析每个表的字段、类型、索引、外键
- 生成表的中文注释和业务说明
- 建立数据血缘关系图

### 3.3 自然语言转 SQL
- 用户描述需求 → AI 生成 SQL
- SQL 审查与优化建议
- 历史 SQL 保存与复用
- 支持多轮对话修正 SQL

### 3.4 可视化报表
- AI 根据查询结果自动生成图表配置
- 支持折线图、柱状图、饼图、散点图、热力图等
- 报表导出 (PDF、Excel)
- 自定义图表主题

### 3.5 AI 智能问答 (带记忆)
- 对单个表或整个数据库提问
- 上下文记忆功能
- 记忆压缩（自动摘要历史对话）
- 多轮追问与澄清
- **RAG 检索增强**：自动从知识库检索相关内容注入上下文

### 3.6 MCP 协议层
- MCP Server：暴露底层数据库操作工具（db_connect, db_execute, db_get_schema 等）
- MCP Client：连接外部 MCP 服务器，动态发现工具
- 支持 STDIO (本地) 和 SSE (远程) 传输

### 3.7 Skills 业务技能层
| 技能名称 | 功能描述 |
|---------|---------|
| `SqlGenerationSkill` | 自然语言转 SQL |
| `TableAnalysisSkill` | 分析表结构 |
| `DataQuerySkill` | 执行查询并格式化 |
| `ChartGenerationSkill` | 图表配置生成 |
| `DataQualitySkill` | 数据质量评估 |
| `InsightDiscoverySkill` | 模式发现与洞察 |
| `ReportExportSkill` | 报告导出 |
| `DataProfilingSkill` | 数据画像 |
| `AnomalyDetectionSkill` | 异常检测 |

### 3.8 Agent 编排层
- **意图识别 (IntentClassifier)**：关键词匹配 + AI 自主判断
- **智能路由 (AgentOrchestrator)**：根据意图分发到对应 Skill
- **路由策略**：规则匹配 → 精确路由 / 模糊请求 → AI 自主决策
- **意图类型**：SQL生成、表分析、异常检测、报表生成、数据洞察等

### 3.9 知识库 + RAG
- **多租户隔离**：每个数据库连接拥有独立知识库
- 文档上传与管理 (PDF/MD/TXT/Excel)
- 文档 Chunk 切分与向量化
- 语义检索 (Semantic Search)
- 上下文自动注入 AI Prompt
- 支持关联特定表/字段
- 知识库版本管理

### 3.10 数据质量评估
- 空值检测、重复检测、格式校验
- 数据分布统计
- 异常值标记 (IQR, Z-score)

### 3.11 智能数据发现
- 自动识别主键、外键、索引
- 识别数据关联关系
- 推荐潜在分析维度

### 3.12 自动报告生成
- 根据分析结果自动生成 Markdown 报告
- 支持定时任务自动刷新
- 邮件推送报告

### 3.13 SQL 性能优化
- 分析 SQL 执行计划
- 给出索引优化建议
- 对比优化前后性能

### 3.14 多模型支持
- 支持 OpenAI、Claude、Local LLM (Ollama)
- 模型切换配置
- 成本统计

### 3.15 表关联分析 (单库血缘)
- 分析表之间的外键关联关系
- 自动识别主键、外键、索引
- 生成可视化 ER 图（表关系图）
- 影响分析（改字段会影响哪些查询）

---

## 4. 项目结构

### 4.1 后端结构
```
data-analysis-backend/
├── data-analysis-common/          # 公共模块
│   ├── exception/
│   ├── constants/
│   └── utils/
├── data-analysis-mcp-server/       # MCP 服务端
│   ├── tools/
│   └── resources/
├── data-analysis-mcp-client/       # MCP 客户端
├── data-analysis-skills/          # 技能框架
│   ├── skill/
│   └── registry/
├── data-analysis-core/            # 核心业务
│   ├── connection/
│   ├── chat/
│   ├── memory/
│   └── analysis/
└── data-analysis-web/             # Web 层
    ├── controller/
    ├── dto/
    └── config/
```

### 4.2 前端结构
```
data-analysis-frontend/
├── src/
│   ├── views/                     # 页面
│   │   ├── Dashboard.vue
│   │   ├── Connection.vue
│   │   ├── Analysis.vue
│   │   ├── Chat.vue
│   │   └── Report.vue
│   ├── components/                # 组件
│   ├── stores/                    # Pinia
│   ├── api/                       # API 调用
│   └── styles/                    # 样式
```

---

## 5. 前端设计风格

**简约现代风格：**
- 主色调：深蓝 (#1a73e8) + 浅灰 (#f8f9fa)
- 卡片式布局，轻阴影
- 大量留白，信息层次分明
- 圆角按钮 (border-radius: 8px)
- 数据可视化使用 ECharts
- 暗色主题支持

---

## 6. 实施计划

### Phase 1: 基础架构 (最高优先级)
1. **AI 模型配置数据化** - 将 AI 配置存储到数据库，前端可配置
2. 初始化 Spring Boot + Vue 3 项目
3. 搭建多模块 Maven 结构
4. 集成 Spring AI ChatClient
5. 配置 MCP 基础架构

### Phase 2: 核心功能
1. 数据库连接管理
2. 表结构扫描与存储
3. 自然语言转 SQL
4. AI 问答基础功能

### Phase 3: 高级功能
1. 记忆压缩
2. 可视化报表
3. MCP 技能扩展
4. 数据质量检测

### Phase 4: 完善与优化
1. 性能优化
2. 前端 UI 打磨
3. 报告导出
4. 自动化任务

---

## 7. 技术选型

| 组件 | 技术 |
|-----|-----|
| 框架 | Spring Boot 3.2+, Spring AI 1.0+ |
| 前端框架 | Vue 3 + Naive UI |
| 状态管理 | Pinia |
| 图表 | ECharts 5 |
| HTTP | Axios + VueUse |
| 数据库 | MySQL 8.0 (元数据) |
| 连接池 | HikariCP |
| 缓存 | Caffeine |
| 构建 | Maven, npm |
