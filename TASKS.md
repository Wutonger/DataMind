# AI 数据分析平台 - 实施任务清单

## Phase 1: 项目初始化

### 1.1 后端 Spring Boot 项目初始化
- [x] 1.1.1 创建 Maven 多模块项目结构
- [x] 1.1.2 配置 pom.xml (Spring Boot 3.2+, Spring AI 1.0.0-M6, MySQL)
- [x] 1.1.3 配置 application.yml
- [x] 1.1.4 创建启动类 DataAnalysisApplication

### 1.2 前端 Vue 3 项目初始化
- [x] 1.2.1 使用 Vite 创建 Vue 3 + TypeScript 项目
- [x] 1.2.2 安装依赖 (Vue Router, Pinia, Axios, ECharts, Naive UI)
- [x] 1.2.3 配置项目结构 (views, components, stores, api)
- [x] 1.2.4 配置全局样式和主题

### 1.3 AI 模型配置数据化 (最高优先级)
- [x] 1.3.1 创建 AppConfig 实体 (存储 AI 模型配置到数据库)
- [x] 1.3.2 创建 AppConfig Repository
- [x] 1.3.3 实现 AppConfig Service (CRUD)
- [x] 1.3.4 实现 AppConfig API (GET/PUT /api/config)
- [x] 1.3.5 前端 Settings 页面对接后端 API
- [x] 1.3.6 后端启动时从数据库加载 AI 配置

---

## Phase 2: 基础设施层

### 2.1 数据库连接管理
- [x] 2.1.1 创建 Connection 实体和 Repository
- [x] 2.1.2 实现 DynamicDataSource 动态数据源路由
- [x] 2.1.3 实现数据库连接测试服务
- [x] 2.1.4 实现连接 CRUD API
- [x] 2.1.5 实现连接激活/取消激活 API
- [x] 2.1.6 实现密码 AES 加密存储
- [x] 2.1.7 前端连接管理页面（添加/编辑/测试/删除/选中）

### 2.2 MCP 协议层
- [x] 2.2.1 搭建 MCP Server 模块（独立进程，端口8081）
- [x] 2.2.2 配置 MCP SSE 传输（官方 spring-ai-mcp-server-spring-boot-starter）
- [x] 2.2.3 创建 MCP Client（官方 spring-ai-mcp-client-spring-boot-starter + McpSyncClient）
- [x] 2.2.4 实现底层工具：db_connect, db_execute, db_get_schema, db_list_tables, db_get_columns（@Tool 注解）

### 2.3 Chat 基础设施
- [x] 2.3.1 配置动态 ChatClient（基于数据库 AI 配置）
- [x] 2.3.2 创建 ChatMemory 内存管理
- [x] 2.3.3 实现消息压缩器 (MessageCompressor)
- [x] 2.3.4 配置 ChatClient + Memory 集成

---

## Phase 3: Skills 业务技能层

### 3.1 技能定义
- [x] 3.1.1 创建 SqlGenerationSkill
- [x] 3.1.2 创建 TableAnalysisSkill
- [x] 3.1.3 创建 DataQuerySkill
- [x] 3.1.4 创建 ChartGenerationSkill
- [x] 3.1.5 创建 DataQualitySkill
- [x] 3.1.6 创建 InsightDiscoverySkill
- [x] 3.1.7 创建 ReportExportSkill
- [x] 3.1.8 创建 DataProfilingSkill
- [x] 3.1.9 创建 AnomalyDetectionSkill

### 3.2 技能注册
- [x] 3.2.1 实现 SkillRegistry 技能注册表
- [x] 3.2.2 实现 MethodToolCallbackProvider (BaseSkill + manual)
- [x] 3.2.3 配置默认 Skills 到 ChatClient

### 3.3 Agent 编排层
- [x] 3.3.1 实现 IntentClassifier 意图识别器
- [x] 3.3.2 实现 AgentOrchestrator 编排器
- [x] 3.3.3 配置规则路由 (关键词匹配)
- [x] 3.3.4 配置 AI 自主决策路由
- [x] 3.3.5 实现混合路由策略

---

## Phase 4: 核心业务功能

### 4.1 表结构分析
- [x] 4.1.1 实现 TableScanner 扫描数据库表
- [x] 4.1.2 实现 SchemaReader 读取表结构 (information_schema)
- [x] 4.1.3 创建 TableMetadata 存储分析结果
- [x] 4.1.4 实现 AI 表分析 Prompt 模板
- [x] 4.1.5 提供表分析 API 和结果缓存

### 4.2 自然语言转 SQL
- [x] 4.2.1 实现 SqlGenerator 服务
- [x] 4.2.2 实现 SQL 执行服务 (JdbcTemplate)
- [x] 4.2.3 实现 SQL 格式化服务
- [x] 4.2.4 创建 SQL 执行历史记录
- [x] 4.2.5 提供自然语言转 SQL API

### 4.3 AI 智能问答
- [x] 4.3.1 实现 ChatService 聊天服务
- [x] 4.3.2 实现 ChatSession 会话管理（数据库持久化）
- [x] 4.3.3 实现 MemoryCompression 记忆压缩
- [ ] 4.3.4 配置 RAG 上下文注入
- [x] 4.3.5 提供流式响应 (SSE)

### 4.4 可视化报表
- [x] 4.4.1 实现 ChartRecommendationService
- [x] 4.4.2 创建 ChartConfig 生成器
- [x] 4.4.3 实现 Report 实体和 CRUD
- [x] 4.4.4 实现 PDF/Excel 导出
- [x] 4.4.5 提供报表 API

---

## Phase 5: 前端开发

### 5.1 基础框架
- [x] 5.1.1 配置 Vue Router 路由
- [x] 5.1.2 配置 Pinia Store
- [x] 5.1.3 创建 Layout 布局组件
- [x] 5.1.4 配置全局样式和主题

### 5.2 页面开发
- [x] 5.2.1 Dashboard 首页
- [x] 5.2.2 Connection 连接管理页面
- [x] 5.2.3 TableAnalysis 表分析页面
- [x] 5.2.4 SqlStudio SQL工作室页面
- [x] 5.2.5 Chat 智能问答页面，上下文管理，压缩上下文
- [x] 5.2.6 Report 报表中心页面
- [x] 5.2.7 Knowledge 知识库管理页面（占位，后端在 Phase 6 实现）

### 5.3 组件开发
- [x] 5.3.1 ConnectionCard 连接卡片组件
- [x] 5.3.2 SqlEditor SQL编辑器组件
- [x] 5.3.3 ChatMessage 聊天消息组件
- [x] 5.3.4 ChartRenderer 图表渲染组件
- [x] 5.3.5 DataTable 数据表格组件
- [x] 5.3.6 StatusBadge 状态徽章组件

### 5.4 API 对接
- [x] 5.4.1 实现 Axios API 服务层
- [ ] 5.4.2 实现 WebSocket 服务
- [x] 5.4.3 对接所有后端 API

---

## Phase 6: 高级功能

### 6.1 知识库 + RAG
- [ ] 6.1.1 实现文档上传服务 (PDF/MD/TXT/Excel)
- [ ] 6.1.2 实现文档 Chunk 切分
- [ ] 6.1.3 配置 Embedding 模型 (OpenAI/Local)
- [ ] 6.1.4 实现向量存储 (MySQL Vector / Milvus)
- [ ] 6.1.5 实现语义检索服务
- [ ] 6.1.6 实现 RAG Prompt 注入
- [ ] 6.1.7 知识库管理界面

### 6.2 数据质量
- [ ] 6.2.1 实现 NullDetection 空值检测
- [ ] 6.2.2 实现 DuplicateDetection 重复检测
- [ ] 6.2.3 实现 AnomalyDetection 异常检测
- [ ] 6.2.4 实现 DataProfiling 数据画像

### 6.3 表关联分析 (单库血缘)
- [ ] 6.3.1 分析表间外键关联关系
- [ ] 6.3.2 自动识别主键、外键、索引
- [ ] 6.3.3 生成可视化 ER 图

### 6.4 自动报告
- [ ] 6.4.1 实现 Markdown 报告生成
- [ ] 6.4.2 实现定时任务调度
- [ ] 6.4.3 实现邮件推送功能

### 6.5 SQL 优化
- [ ] 6.5.1 实现 EXPLAIN 分析
- [ ] 6.5.2 实现索引推荐
- [ ] 6.5.3 实现性能对比

---

## Phase 7: 优化与完善

### 7.1 性能优化
- [ ] 7.1.1 实现 Caffeine 本地缓存
- [ ] 7.1.2 实现 Redis 分布式缓存
- [ ] 7.1.3 优化大结果集处理

### 7.2 安全加固
- [x] 7.2.1 密码加密存储 (AES)
- [ ] 7.2.2 SQL 注入防护
- [ ] 7.2.3 权限控制

### 7.3 测试
- [ ] 7.3.1 编写单元测试
- [ ] 7.3.2 编写集成测试
- [ ] 7.3.3 编写 E2E 测试

---

## 任务依赖关系

```
Phase 1 (项目初始化)
    ↓
Phase 2 (基础设施) → Phase 3 (Skills)
    ↓                   ↓
Phase 4 (核心业务) ← Phase 3
    ↓
Phase 5 (前端开发)
    ↓
Phase 6 (高级功能)
    ↓
Phase 7 (优化完善)
```

---

## 关键里程碑

| 里程碑 | 内容 | 目标 |
|-------|-----|-----|
| M1 | 基础项目 + 数据库连接 | 完成基础架子 |
| M2 | 表分析 + NL2SQL | 核心功能可用 |
| M3 | AI问答 + 记忆 | 智能对话可用 |
| M4 | 可视化报表 | 完整分析流程 |
| M5 | 高级功能 | 完整版发布 |
