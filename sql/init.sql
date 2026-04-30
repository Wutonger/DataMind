-- DataMind AI schema initialization
-- Database: datamine

CREATE TABLE IF NOT EXISTS `connections` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(100) NOT NULL COMMENT 'Connection name',
  `type` VARCHAR(50) NOT NULL COMMENT 'mysql/doris/postgresql/oracle/sqlserver/clickhouse',
  `host` VARCHAR(255) NOT NULL COMMENT 'Host',
  `port` INT NOT NULL COMMENT 'Port',
  `database_name` VARCHAR(100) NOT NULL COMMENT 'Database name',
  `username` VARCHAR(100) NOT NULL COMMENT 'Username',
  `password` VARCHAR(255) NOT NULL COMMENT 'Encrypted password',
  `status` VARCHAR(20) DEFAULT 'disconnected' COMMENT 'connected/disconnected/error',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Database connections';

CREATE TABLE IF NOT EXISTS `table_metadata` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `connection_id` BIGINT NOT NULL COMMENT 'Connection ID',
  `table_name` VARCHAR(100) NOT NULL COMMENT 'Table name',
  `schema_name` VARCHAR(100) DEFAULT NULL COMMENT 'Schema name',
  `ai_description` TEXT COMMENT 'AI generated description',
  `fields` JSON COMMENT 'Field metadata JSON',
  `relations` JSON COMMENT 'Table relation JSON',
  `row_count` BIGINT DEFAULT 0 COMMENT 'Row count',
  `analyzed_at` DATETIME DEFAULT NULL COMMENT 'Last analyzed at',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_conn_table` (`connection_id`, `table_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Table metadata';

CREATE TABLE IF NOT EXISTS `chat_sessions` (
  `id` VARCHAR(36) PRIMARY KEY COMMENT 'Session ID',
  `connection_id` BIGINT NOT NULL COMMENT 'Connection ID',
  `messages` JSON COMMENT 'Message history JSON',
  `summary` TEXT COMMENT 'Compressed summary',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Chat sessions';

CREATE TABLE IF NOT EXISTS `knowledge_documents` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `connection_id` BIGINT NOT NULL COMMENT 'Connection ID',
  `name` VARCHAR(255) NOT NULL COMMENT 'Document name',
  `type` VARCHAR(20) DEFAULT NULL COMMENT 'pdf/md/txt/docx',
  `file_path` VARCHAR(500) DEFAULT NULL COMMENT 'Stored source path',
  `status` VARCHAR(20) DEFAULT 'pending' COMMENT 'pending/embedding/ready/error',
  `total_chunks` INT DEFAULT 0 COMMENT 'Chunk count',
  `error_message` TEXT DEFAULT NULL COMMENT 'Indexing error message',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Knowledge documents';

CREATE TABLE IF NOT EXISTS `document_chunks` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `document_id` BIGINT NOT NULL COMMENT 'Document ID',
  `connection_id` BIGINT NOT NULL COMMENT 'Connection ID',
  `content` TEXT NOT NULL COMMENT 'Chunk content',
  `chunk_index` INT NOT NULL COMMENT 'Chunk index',
  `embedding` JSON DEFAULT NULL COMMENT 'Embedding JSON',
  `metadata` JSON DEFAULT NULL COMMENT 'Chunk metadata JSON'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Knowledge chunks';

CREATE TABLE IF NOT EXISTS `reports` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `connection_id` BIGINT DEFAULT NULL COMMENT 'Connection ID',
  `name` VARCHAR(200) NOT NULL COMMENT 'Report name',
  `config` JSON DEFAULT NULL COMMENT 'Chart config JSON',
  `chart_type` VARCHAR(50) DEFAULT NULL COMMENT 'line/bar/pie/scatter/report',
  `query` TEXT DEFAULT NULL COMMENT 'SQL query',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Reports';

CREATE TABLE IF NOT EXISTS `sql_history` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `connection_id` BIGINT NOT NULL COMMENT 'Connection ID',
  `session_id` VARCHAR(36) DEFAULT NULL COMMENT 'Session ID',
  `sql` TEXT NOT NULL COMMENT 'SQL statement',
  `natural_language` TEXT DEFAULT NULL COMMENT 'Natural language question',
  `result_preview` TEXT DEFAULT NULL COMMENT 'Result preview',
  `row_count` INT DEFAULT NULL COMMENT 'Returned row count',
  `execution_time_ms` BIGINT DEFAULT NULL COMMENT 'Execution time ms',
  `status` VARCHAR(20) DEFAULT 'success' COMMENT 'success/error',
  `error_message` TEXT DEFAULT NULL COMMENT 'Error message',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SQL history';

CREATE TABLE IF NOT EXISTS `workflow_runs` (
  `id` VARCHAR(64) PRIMARY KEY COMMENT 'Workflow run ID',
  `scene` VARCHAR(32) NOT NULL COMMENT 'chat/sql/analysis/report',
  `title` VARCHAR(120) NOT NULL COMMENT 'Workflow title',
  `connection_id` BIGINT DEFAULT NULL COMMENT 'Related connection ID',
  `route_mode` VARCHAR(64) DEFAULT 'DATA_ONLY' COMMENT 'Final route mode',
  `status` VARCHAR(32) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/COMPLETED/FAILED/PENDING',
  `started_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Run started at',
  `finished_at` DATETIME DEFAULT NULL COMMENT 'Run finished at',
  `final_path` LONGTEXT DEFAULT NULL COMMENT 'Final path JSON string',
  `used_agents` LONGTEXT DEFAULT NULL COMMENT 'Used agents JSON string',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_workflow_run_conn` (`connection_id`),
  KEY `idx_workflow_run_scene_conn_started` (`scene`, `connection_id`, `started_at`),
  KEY `idx_workflow_run_scene_started` (`scene`, `started_at`),
  KEY `idx_workflow_run_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Workflow runs';

CREATE TABLE IF NOT EXISTS `workflow_steps` (
  `id` VARCHAR(96) PRIMARY KEY COMMENT 'Workflow step ID',
  `run_id` VARCHAR(64) NOT NULL COMMENT 'Workflow run ID',
  `step_order` INT NOT NULL COMMENT 'Step order within the run',
  `agent_id` VARCHAR(32) NOT NULL COMMENT 'coordinator/knowledge/data',
  `owner` VARCHAR(64) NOT NULL COMMENT 'Owner display name',
  `title` VARCHAR(120) NOT NULL COMMENT 'Step title',
  `kind` VARCHAR(64) NOT NULL COMMENT 'Step kind',
  `status` VARCHAR(32) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/COMPLETED/FAILED/PENDING',
  `input_summary` LONGTEXT DEFAULT NULL COMMENT 'Input summary',
  `output_summary` LONGTEXT DEFAULT NULL COMMENT 'Output summary',
  `tools` LONGTEXT DEFAULT NULL COMMENT 'Tool list JSON string',
  `started_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Step started at',
  `finished_at` DATETIME DEFAULT NULL COMMENT 'Step finished at',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_workflow_step_run_order` (`run_id`, `step_order`),
  KEY `idx_workflow_step_agent` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Workflow steps';

CREATE TABLE IF NOT EXISTS `workflow_timeline` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `run_id` VARCHAR(64) NOT NULL COMMENT 'Workflow run ID',
  `event_order` INT NOT NULL COMMENT 'Timeline event order',
  `time_label` VARCHAR(16) NOT NULL COMMENT 'HH:mm:ss display time',
  `node_id` VARCHAR(96) NOT NULL COMMENT 'Related node or step ID',
  `title` VARCHAR(64) NOT NULL COMMENT 'Timeline title',
  `message` LONGTEXT DEFAULT NULL COMMENT 'Timeline message',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_workflow_timeline_run_order` (`run_id`, `event_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Workflow timeline';

CREATE TABLE IF NOT EXISTS `app_config` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `config_key` VARCHAR(100) NOT NULL UNIQUE COMMENT 'Config key',
  `config_value` TEXT NOT NULL COMMENT 'Config value JSON',
  `description` VARCHAR(255) DEFAULT NULL COMMENT 'Description',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Application config';

INSERT INTO `app_config` (`config_key`, `config_value`, `description`) VALUES
('ai.model.default', '{"provider":"openai","baseUrl":"https://api.openai.com/v1","apiKey":"","model":"gpt-4o","embeddingModel":"text-embedding-3-small","temperature":0.7}', 'Default AI model config');
