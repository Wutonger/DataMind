# AI 数据分析平台 - 功能规格说明书

## 1. 项目简介

**项目名称：** DataMind AI - 智能数据分析平台

**核心价值：** 让非技术人员通过自然语言即可完成复杂的数据分析工作，无需编写 SQL 代码。

---

## 2. 用户故事

| 角色 | 需求 | 价值 |
|-----|-----|-----|
| 业务分析师 | 连接数据库后直接提问"上季度销售额趋势" | 10分钟内获取报表 |
| 产品经理 | 让AI解释每张表的作用，快速了解新系统 | 无需查阅文档 |
| 数据专员 | 输入描述生成SQL，自动可视化 | 零SQL基础 |
| 运维人员 | 监控数据异常，AI自动告警 | 被动变主动 |

---

## 3. 功能模块详细说明

### 3.1 数据库连接中心

**功能点：**
- 添加/编辑/删除数据库连接
- 支持连接池自动管理
- 支持 JDBC 全类型数据库
- 连接状态实时监控
- 密码加密存储

**支持的数据库：**
- MySQL 8.0+
- PostgreSQL 12+
- Oracle 19c+
- SQL Server 2019+
- ClickHouse 23+
- DM (达梦)
- KingBase (金仓)

**UI 表现：**
- 连接列表卡片，支持状态徽章
- 一键测试连接
- 连接详情弹窗

---

### 3.2 AI 表分析助手

**功能点：**
- 扫描数据库所有表 (information_schema)
- AI 分析每个表的：
  - 字段含义 (中文注释)
  - 业务用途说明
  - 数据样例 (前3条)
  - 与其他表的关系
- 自动建立数据字典
- 支持批量生成文档

**输出示例：**
```
表名: orders
分析结果:
- 主键: order_id
- 核心字段: user_id, amount, status, created_at
- 业务说明: 订单主表，记录用户下单信息
- 数据规模: 约1200万条
```

---

### 3.3 自然语言转 SQL

**功能点：**
- 输入自然语言描述 → 输出 SQL
- 支持多轮对话修正 SQL
- SQL 语法高亮与格式化
- 执行预览 (不执行)
- 一键执行并展示结果
- 保存常用查询模板

**示例对话：**
```
用户: 查看每个月的销售总额，按降序排列
AI: SELECT DATE_TRUNC('month', created_at) as month,
           SUM(amount) as total_sales
    FROM orders
    WHERE status = 'completed'
    GROUP BY 1
    ORDER BY 2 DESC;

用户: 只看华东地区
AI: [自动修改SQL添加WHERE条件]
```

---

### 3.4 智能可视化报表

**功能点：**
- AI 根据查询结果自动推荐图表类型
- 支持手动切换图表类型
- 图表配置面板 (标题、颜色、标签)
- 报表导出: PNG, PDF, Excel
- 报表收藏与分享
- 定时刷新数据

**图表类型支持：**
- 折线图 (趋势)
- 柱状图 (对比)
- 饼图 (占比)
- 散点图 (分布)
- 热力图 (密度)
- 仪表盘 (KPI)

---

### 3.5 AI 智能问答 (带记忆)

**功能点：**
- 对整个数据库或指定表提问
- 上下文记忆 (保留最近20轮)
- 记忆压缩 (自动摘要，保留关键信息)
- 支持多轮追问
- 回答可追溯 (引用具体表/字段)

**记忆压缩策略：**
```
当对话超过阈值:
1. 识别关键信息 (已查询的表、SQL模式、业务术语)
2. 压缩为摘要
3. 保留摘要，清理原始消息
4. 继续对话
```

---

### 3.6 MCP 协议层 (连接外部工具)

**架构原则：** MCP 是协议层，负责与外部工具/服务通信；Skills 是业务层，封装具体智能能力。

**MCP Server（独立进程）暴露的工具：**
| 工具 | 描述 |
|-----|-----|
| `db_connect` | 建立数据库连接 |
| `db_disconnect` | 断开连接 |
| `db_execute` | 执行 SQL 语句 |
| `db_get_schema` | 获取数据库元数据 |
| `db_list_tables` | 列出所有表 |
| `db_get_columns` | 获取列信息 |

**MCP Client 能力：**
- 连接外部 MCP 服务器
- 动态发现可用工具
- MCP Server：**独立进程**，暴露底层数据库操作工具
- 支持 STDIO (本地) / SSE (远程) 传输

---

### 3.7 Skills 技能中心 (业务层)

**架构原则：** Skills 基于 MCP 协议调用底层工具，封装具体业务智能能力。

**标准化技能定义：**

| Skill | 描述 | 输入 | 输出 |
|-------|-----|-----|-----|
| SqlGeneration | 自然语言转SQL | 用户描述 | SQL语句 |
| TableAnalysis | 分析表结构和含义 | 表名 | 分析报告 |
| DataQuery | 执行查询并格式化 | SQL | 结果集+统计 |
| ChartRecommend | 推荐图表类型 | 查询结果 | 图表配置 |
| DataQuality | 数据质量评估 | 表名 | 质量报告 |
| InsightFind | 发现数据洞察 | 分析请求 | 洞察列表 |
| ReportGen | 生成分析报告 | 分析数据 | Markdown |
| DataProfiling | 数据画像 | 表名 | 统计报告 |
| AnomalyDetection | 异常检测 | 表名 | 异常列表 |
| SqlOptimization | SQL性能优化 | SQL | 优化建议 |

---

### 3.8 Agent 编排层

**架构原则：** 智能路由请求到对应的 Skill 或直接 AI 对话

**路由策略：**

| 策略 | 适用场景 | 示例 |
|-----|---------|-----|
| **规则匹配** | 明确意图关键词 | "检测异常" → AnomalyDetectionSkill |
| **AI 自主决策** | 模糊/复杂请求 | "帮我看看这个表有什么问题" → AI判断 |
| **直接对话** | 闲聊/通用问答 | "你好" → ChatAI 带记忆 |

**路由流程：**
```
用户请求
    │
    ▼
┌─────────────────────────────────────┐
│      IntentClassifier (意图识别)      │
│                                     │
│  关键词匹配:                          │
│  - "检测/异常" → ANOMALY           │
│  - "生成报表/导出" → REPORT         │
│  - "分析/洞察" → INSIGHT           │
│  - "什么意思/解释" → TABLE_ANALYSIS │
│                                     │
│  无匹配 → 进入 AI 自主决策           │
└─────────────────┬───────────────────┘
                  ▼
┌─────────────────────────────────────┐
│     Agent Orchestrator (编排器)       │
│                                     │
│  switch(intent) {                    │
│    case ANOMALY → AnomalyDetection  │
│    case REPORT → ReportExport       │
│    case INSIGHT → InsightDiscovery  │
│    case SQL_GEN → SqlGeneration     │
│    default → ChatAI + Memory + RAG  │
│  }                                  │
└─────────────────────────────────────┘
```

**意图类型定义：**

| Intent | 触发关键词 | 路由目标 |
|--------|----------|---------|
| SQL_GENERATION | "查一下"、"计算"、"帮我写SQL" | SqlGenerationSkill |
| TABLE_ANALYSIS | "这张表是什么"、"字段含义" | TableAnalysisSkill |
| ANOMALY_DETECTION | "检测异常"、"有什么问题" | AnomalyDetectionSkill |
| CHART_GENERATION | "生成图表"、"可视化" | ChartGenerationSkill |
| REPORT_EXPORT | "导出报表"、"生成报告" | ReportExportSkill |
| INSIGHT_DISCOVERY | "分析一下"、"发现什么" | InsightDiscoverySkill |
| DATA_QUALITY | "数据质量"、"完整性" | DataQualitySkill |
| DATA_PROFILING | "画像"、"分布"、"统计" | DataProfilingSkill |
| CHAT_AI | 其他通用场景 | ChatAI + Memory + RAG |

---

## 4. 其他核心功能

### 4.1 知识库 + RAG
- **多租户隔离**：每个数据库连接拥有独立的知识库，互不干扰
- **文档管理**：上传/管理 PDF/MD/TXT/Excel 文档
- **向量化**：文档 Chunk 切分 + Embedding 模型生成向量
- **向量存储**：MySQL Vector / Milvus / Chroma
- **语义检索**：根据用户问题检索相关知识（仅当前连接的知识库）
- **RAG 注入**：将检索结果注入 AI Prompt，提升回答准确性
- **关联绑定**：文档可关联特定表/字段，便于精准检索
- **知识库管理界面**：文档列表、版本管理、检索测试

### 4.2 数据异常检测
- 自动检测数值异常 (IQR, Z-score)
- 重复记录检测
- 格式一致性检查
- 异常记录标记与导出

### 4.3 智能数据发现

**功能点：**
- 自动识别主键、外键
- 推荐表关联关系
- 发现潜在分析维度
- 给出分析建议
- 智能字段分类（维度/度量）
- 数据分布统计
- 异常模式识别

### 4.4 SQL 性能诊断
- 分析 EXPLAIN 输出
- 识别全表扫描
- 推荐索引策略
- 预估查询时间

### 4.5 自动数据报告
- 输入主题 → 生成完整报告
- 包含统计图表、文字分析
- 支持定时邮件推送
- 历史报告存档

### 4.6 多模型路由
- OpenAI GPT-4 (高精度)
- Anthropic Claude (长文本)
- Local Ollama (私有部署)
- 自动选择最优模型

### 4.7 表关联分析 (单库血缘)
- 分析表之间的外键关联关系
- 自动识别主键、外键、索引
- 生成可视化 ER 图（表关系图）
- 影响分析（改字段会影响哪些查询）

---

## 5. 前端页面设计

### 5.1 页面列表
1. **Dashboard** - 首页，展示已连接数据库概览
2. **连接管理** - 数据库连接 CRUD
3. **表分析** - 查看所有表及AI分析结果
4. **SQL工作室** - 自然语言转SQL，执行查询
5. **智能问答** - AI对话界面
6. **报表中心** - 可视化报表管理
7. **系统设置** - 模型配置、技能配置

### 5.2 技术栈
- **框架**: Vue 3 + Naive UI
- **构建**: Vite 5+
- **语言**: TypeScript 5+
- **状态管理**: Pinia
- **图表**: ECharts 5
- **HTTP**: Axios + VueUse

### 5.3 设计规范
```
主色: #1a73e8 (Google Blue)
辅色: #34a853 (Green), #ea4335 (Red), #fbbc04 (Yellow)
背景: #ffffff (浅色), #1e1e1e (深色)
文字: #202124 (主), #5f6368 (次)
圆角: 8px (按钮), 12px (卡片)
阴影: 0 1px 3px rgba(0,0,0,0.12)
间距: 8px 基准单位
```

### 5.4 组件清单
- `ConnectionCard` - 连接卡片
- `TableCard` - 表信息卡片
- `SqlEditor` - SQL编辑器 (Monaco)
- `ChatMessage` - 聊天消息
- `ChartRenderer` - 图表渲染器
- `DataTable` - 数据表格
- `StatusBadge` - 状态徽章

---

## 6. 接口设计

### 6.1 核心 API

| 方法 | 路径 | 描述 |
|-----|-----|-----|
| POST | /api/connections | 创建连接 |
| GET | /api/connections | 获取连接列表 |
| DELETE | /api/connections/{id} | 删除连接 |
| POST | /api/connections/{id}/test | 测试连接 |
| GET | /api/connections/{id}/tables | 获取所有表 |
| GET | /api/tables/{id}/schema | 获取表结构 |
| POST | /api/chat | 发送AI对话 |
| POST | /api/sql/generate | 生成SQL |
| POST | /api/sql/execute | 执行SQL |
| POST | /api/reports | 创建报表 |
| GET | /api/reports | 获取报表列表 |

### 6.2 WebSocket
- `/ws/chat` - AI 流式对话
- `/ws/execute` - SQL 执行进度

---

## 7. 数据模型

### 7.1 核心实体
```
Connection {
  id: Long
  name: String
  type: String (mysql/postgresql/oracle/...)
  host: String
  port: Integer
  database: String
  username: String
  password: String (加密)
  status: String
  createdAt: DateTime
}

TableMetadata {
  id: Long
  connectionId: Long
  tableName: String
  schema: String
  aiDescription: String (AI生成)
  fields: JSON
  rowCount: Long
  analyzedAt: DateTime
}

ChatSession {
  id: String
  connectionId: Long
  messages: JSON
  summary: String (压缩后)
  createdAt: DateTime
  updatedAt: DateTime
}

Report {
  id: Long
  name: String
  config: JSON
  chartType: String
  query: String
  createdAt: DateTime
}

KnowledgeDocument {
  id: Long
  connectionId: Long (知识库所属的数据库连接)
  name: String
  type: String (pdf/md/txt/xlsx)
  filePath: String
  status: String (pending/embedding/ready)
  totalChunks: Integer
  createdAt: DateTime
  updatedAt: DateTime
}

DocumentChunk {
  id: Long
  documentId: Long
  connectionId: Long (与文档关联的连接)
  content: String
  chunkIndex: Integer
  embedding: JSON (向量数据)
  metadata: JSON (表名、页码等)
}
```

---

## 8. AI 模型配置数据化

**原则：** AI 模型配置存储在数据库中，前端页面可配置，无需修改代码或重启服务。

**配置项：**

| 字段 | 类型 | 说明 |
|-----|-----|-----|
| provider | String | 提供商 (openai/claude/ollama) |
| baseUrl | String | API 基础地址 |
| apiKey | String | API 密钥 (加密存储) |
| model | String | 模型名称 |
| temperature | Double | 温度参数 |

**数据模型：**
```
AppConfig {
  id: Long
  configKey: String (唯一标识，如 "ai.model.default")
  configValue: String (JSON 格式存储)
  description: String
  createdAt: DateTime
  updatedAt: DateTime
}
```

**加载时机：**
- 服务启动时加载到内存
- 支持运行时刷新配置
- 前端修改后实时生效

---

## 9. 非功能性需求

- **性能**: SQL 执行结果 < 10s (百万级数据)
- **并发**: 支持 100 并发用户
- **安全**: 密码加密存储，SQL 注入防护
- **可用性**: 连接断线重连
- **可扩展**: 插件式 Skill 机制
