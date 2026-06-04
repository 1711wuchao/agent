CREATE TABLE IF NOT EXISTS contract_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    version VARCHAR(32) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS contract_template_variable (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_code VARCHAR(64) NOT NULL,
    field_code VARCHAR(64) NOT NULL,
    field_label VARCHAR(128) NOT NULL,
    field_type VARCHAR(32) NOT NULL,
    required TINYINT NOT NULL DEFAULT 0,
    placeholder VARCHAR(255),
    sort_order INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_template_field (template_code, field_code)
);

CREATE TABLE IF NOT EXISTS contract (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    contract_no VARCHAR(64) NOT NULL UNIQUE,
    contract_type VARCHAR(64) NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS contract_field_value (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    contract_no VARCHAR(64) NOT NULL,
    field_code VARCHAR(64) NOT NULL,
    field_value TEXT,
    UNIQUE KEY uk_contract_field (contract_no, field_code)
);

CREATE TABLE IF NOT EXISTS contract_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    contract_no VARCHAR(64) NOT NULL,
    version_no VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    file_id BIGINT,
    change_summary VARCHAR(512),
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS contract_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    contract_no VARCHAR(64) NOT NULL,
    file_type VARCHAR(32) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    storage_path VARCHAR(512) NOT NULL,
    checksum VARCHAR(128),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_tool_call_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tool_name VARCHAR(128) NOT NULL,
    request_body JSON NOT NULL,
    response_body JSON,
    success TINYINT NOT NULL DEFAULT 0,
    caller VARCHAR(128),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS knowledge_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    contract_type VARCHAR(64),
    source VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    metadata JSON,
    vector_id VARCHAR(128),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
