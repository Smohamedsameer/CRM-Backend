CREATE TABLE employees  (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'SALES',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
) ENGINE=InnoDB;

CREATE TABLE leads (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lead_code VARCHAR(40) NOT NULL UNIQUE,
    customer_name VARCHAR(150) NOT NULL,
    company_name VARCHAR(150),
    square_feet DOUBLE,
    estimated_amount DECIMAL(14,2),
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(190),
    source VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'NEW',
    enquiry_token VARCHAR(100) UNIQUE,
    enquiry_token_expires_at DATETIME,
    created_by BIGINT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_lead_employee FOREIGN KEY (created_by) REFERENCES employees(id),
    INDEX idx_lead_phone (phone),
    INDEX idx_lead_status (status)
) ENGINE=InnoDB;

CREATE TABLE enquiries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lead_id BIGINT NOT NULL UNIQUE,
    requirement VARCHAR(2000),
    product_or_service VARCHAR(255),
    quantity VARCHAR(100),
    location VARCHAR(255),
    required_date DATETIME,
    specifications VARCHAR(2000),
    material_preference VARCHAR(255),
    additional_requirements VARCHAR(2000),
    remarks VARCHAR(2000),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_enquiry_lead FOREIGN KEY (lead_id) REFERENCES leads(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE quotations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    quotation_number VARCHAR(40) NOT NULL UNIQUE,
    lead_id BIGINT NOT NULL,
    customer_name VARCHAR(150),
    company_name VARCHAR(150),
    subtotal DECIMAL(14,2) NOT NULL DEFAULT 0,
    discount DECIMAL(14,2) NOT NULL DEFAULT 0,
    tax DECIMAL(14,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
    valid_until DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    pdf_path VARCHAR(500),
    secure_token VARCHAR(100) UNIQUE,
    token_expires_at DATETIME,
    sent_at DATETIME,
    viewed_at DATETIME,
    responded_at DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_quotation_lead FOREIGN KEY (lead_id) REFERENCES leads(id) ON DELETE CASCADE,
    INDEX idx_quotation_lead (lead_id)
) ENGINE=InnoDB;

CREATE TABLE quotation_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    quotation_id BIGINT NOT NULL,
    description VARCHAR(500) NOT NULL,
    quantity DOUBLE,
    unit_price DECIMAL(14,2),
    amount DECIMAL(14,2),
    CONSTRAINT fk_item_quotation FOREIGN KEY (quotation_id) REFERENCES quotations(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE customer_responses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    quotation_id BIGINT NOT NULL,
    response_type VARCHAR(30) NOT NULL,
    change_request_notes VARCHAR(2000),
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_response_quotation FOREIGN KEY (quotation_id) REFERENCES quotations(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lead_id BIGINT NOT NULL UNIQUE,
    quotation_id BIGINT,
    order_number VARCHAR(40),
    order_amount DECIMAL(14,2),
    status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED',
    notes VARCHAR(2000),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_order_lead FOREIGN KEY (lead_id) REFERENCES leads(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_quotation FOREIGN KEY (quotation_id) REFERENCES quotations(id)
) ENGINE=InnoDB;

CREATE TABLE whatsapp_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    lead_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    to_phone VARCHAR(20),
    provider_message_id VARCHAR(150),
    delivery_status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    payload_summary VARCHAR(4000),
    error_message VARCHAR(2000),
    retry_count INT NOT NULL DEFAULT 0,
    sent_at DATETIME,
    delivered_at DATETIME,
    read_at DATETIME,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_message_lead FOREIGN KEY (lead_id) REFERENCES leads(id) ON DELETE CASCADE,
    INDEX idx_message_provider_id (provider_message_id)
) ENGINE=InnoDB;

CREATE TABLE audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type VARCHAR(50),
    entity_id BIGINT,
    event VARCHAR(100) NOT NULL,
    details VARCHAR(2000),
    performed_by VARCHAR(190),
    created_at DATETIME NOT NULL
) ENGINE=InnoDB;

-- Default admin user: email admin@company.com / password Admin@123
-- IMPORTANT: change this password immediately after first login in any real deployment.
INSERT INTO employees (email, password_hash, full_name, role, active, created_at, updated_at)
VALUES (
    'admin@company.com',
    '$2b$10$QpAfSdEDYD8MUJlf7qtrAOvvbGayrf1OLx62UkCvi7XnCt/oTbrLK',
    'System Admin',
    'ADMIN',
    TRUE,
    NOW(),
    NOW()
);