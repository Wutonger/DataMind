# DataMind AI - 智能数据分析平台

## 项目简介

基于 AI 的智能数据分析平台，前端使用 Vue 3 + Naive UI，后端使用 Spring Boot + Spring AI。

## 技术栈

### 后端
- Spring Boot 3.2+
- Spring AI 1.0+
- Spring Data JPA
- MySQL 8.0
- HikariCP

### 前端
- Vue 3.4+
- Vite 5+
- TypeScript 5+
- Naive UI
- ECharts 5
- Pinia

## 项目结构

```
data-analysis/
├── data-analysis-backend/          # 后端项目
│   ├── data-analysis-common/       # 公共模块
│   ├── data-analysis-mcp-server/   # MCP 服务端
│   ├── data-analysis-mcp-client/   # MCP 客户端
│   ├── data-analysis-skills/       # Skills 技能框架
│   ├── data-analysis-core/         # 核心业务
│   └── data-analysis-web/          # Web 层
└── data-analysis-frontend/         # 前端项目
```

## 快速开始

### 前置条件
- JDK 17+
- Maven 3.8+
- Node.js 18+
- MySQL 8.0+

### 1. 初始化数据库

```sql
CREATE DATABASE datamine DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 配置后端

编辑 `data-analysis-web/src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/datamine
    username: your_username
    password: your_password
  ai:
    openai:
      api-key: your_openai_api_key
```

### 3. 启动后端

```bash
cd data-analysis-backend
mvn clean install
cd data-analysis-web
mvn spring-boot:run
```

### 4. 启动前端

```bash
cd data-analysis-frontend
npm install
npm run dev
```

### 5. 访问系统

打开浏览器访问 http://localhost:3000

## 功能模块

| 模块 | 说明 |
|-----|-----|
| 连接管理 | 添加/管理数据库连接 |
| 表分析 | AI 自动分析数据库表结构 |
| 智能问答 | 基于 AI 的数据库问答系统 |
| 报表中心 | 可视化报表管理 |
| 知识库 | 文档管理和 RAG 检索 |

## 接口文档

启动后访问 http://localhost:8080/swagger-ui.html

## 许可证

MIT
