# DataMind AI V1 项目规划

## 1. 项目目标

DataMind AI V1 的目标是建设一套完整的数据智能分析平台，围绕数据库分析场景提供统一的接入、分析、问答、知识增强、报表输出与执行追踪能力。

V1 交付重点包括：

- 多数据源连接管理
- 表结构分析与关系识别
- 自然语言转 SQL 与 SQL 工作台
- 智能问答
- 知识库检索增强
- 图表报表与文档报告生成
- 执行链路可视化追踪
- 系统级模型配置管理

## 2. 产品范围

## 2.1 数据库连接管理

- 维护数据库连接信息
- 测试连接可用性
- 按当前连接切换系统上下文
- 为后续表分析、问答、SQL、报表、知识库提供统一的连接入口

## 2.2 表结构分析

- 扫描当前连接下的全部表结构
- 读取字段、类型、主键、索引等基础信息
- 生成表级中文业务描述
- 分析表之间的关联关系
- 在前端展示分析结果与处理进度

## 2.3 SQL 工作台

- 根据自然语言生成 SQL
- 执行 SQL 并展示结果
- 记录 SQL 生成历史
- 为问答、报表、图表等能力提供查询基础

## 2.4 智能问答

- 提供流式问答体验
- 根据问题自动调用数据库工具
- 根据需要调用知识库检索能力
- 根据需要调用报表保存相关工具
- 保留引用信息与执行过程

## 2.5 知识库 V1

- 支持文档上传、索引、检索、预览
- 文档类型：`PDF`、`TXT`、`Markdown`、`DOCX`
- 基于向量检索补充问答上下文
- 支持片段级引用与定位

## 2.6 报表中心

- 保存 AI 生成的图表报表
- 保存 AI 生成的 Markdown 文档报告
- 展示报表列表与内容
- 支持 PDF 下载

## 2.7 执行链路

- 记录问答、SQL、表分析、报表生成等场景的运行过程
- 保存运行记录、步骤记录、时间线记录
- 在前端页面中查看执行轨迹与关键节点

## 2.8 系统设置

- 维护语言模型配置
- 维护向量模型配置
- 维护温度等运行参数

## 3. 技术架构

## 3.1 前端架构

前端使用：

- Vue 3
- TypeScript
- Vite
- Naive UI
- Pinia
- ECharts

页面组成：

- `Dashboard`
- `Connections`
- `Analysis`
- `SqlStudio`
- `Chat`
- `Reports`
- `Knowledge`
- `Workflow`
- `Settings`

## 3.2 后端架构

后端使用：

- Spring Boot
- Spring AI
- Spring AI Alibaba
- Spring Data JPA
- MySQL
- MCP

模块划分：

- `data-analysis-common`
- `data-analysis-mcp-server`
- `data-analysis-mcp-client`
- `data-analysis-skills`
- `data-analysis-agent`
- `data-analysis-core`
- `data-analysis-web`

## 3.3 Agent 与 Tool 组织方式

- 由统一 Agent 编排层处理问答、SQL、报表等主流程
- 数据库能力通过 MCP Server 暴露为标准工具
- 业务约束通过 Skill 资源提供
- 运行过程通过 Workflow 相关表进行追踪

## 3.4 Skill 资源

V1 使用的 Skill 资源包括：

- `knowledge-grounding`
- `artifact-generation`
- `insight-discovery`

## 4. 数据设计

V1 核心数据表包括：

- `connections`
- `table_metadata`
- `chat_sessions`
- `knowledge_documents`
- `document_chunks`
- `reports`
- `sql_history`
- `workflow_runs`
- `workflow_steps`
- `workflow_timeline`
- `app_config`

数据库初始化脚本位于：

- [init.sql](/D:/data_analysis/sql/init.sql)

## 5. 交付标准

V1 需要满足以下交付标准：

- 用户可以维护数据库连接并切换当前分析上下文
- 用户可以完成全库表分析并查看结果
- 用户可以通过自然语言生成并执行 SQL
- 用户可以在智能问答中完成数据库问答与知识增强问答
- 用户可以将图表或文档保存到报表中心
- 用户可以在知识库中上传文档、检索内容、预览片段
- 用户可以在执行链路页面查看关键运行过程
- 用户可以在系统设置中维护模型配置

## 6. V1 约束边界

V1 范围内明确包含：

- 文档型知识库
- 图表报表与文档报告
- 执行链路追踪
- 模型配置管理

V1 范围内暂不包含：

- OCR
- XLSX 知识库解析
- 完整权限体系
- 多租户隔离
- 拖拽式流程编排器

## 7. 后续扩展方向

在 V1 基础上，后续可以继续扩展：

- 数据质量分析
- SQL 执行计划与优化建议
- 更完整的表血缘与关系可视化
- 更强的知识库检索策略
- 更完善的权限与安全控制
- 更丰富的报表导出与交付方式
