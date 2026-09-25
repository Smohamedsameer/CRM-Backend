-- Certificates & Documents page: company, project, quality and safety files uploaded by admins.
-- Files are kept in the database so they back up with everything else.
CREATE TABLE company_documents (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    category     VARCHAR(100)  NOT NULL,
    doc_type     VARCHAR(150)  NOT NULL,
    file_name    VARCHAR(255)  NOT NULL,
    content_type VARCHAR(150)  NOT NULL,
    size_bytes   BIGINT        NOT NULL,
    data         LONGBLOB      NOT NULL,
    uploaded_at  DATETIME      NOT NULL,
    INDEX idx_company_documents_type (category, doc_type)
);
