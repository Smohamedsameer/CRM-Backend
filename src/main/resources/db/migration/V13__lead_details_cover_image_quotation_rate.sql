-- Lead details for the quotation (address, pincode, GST, project name, date), cover image storage,
-- and the area / rate used to price a quotation.
-- Every ADD COLUMN is guarded, so this is safe even if a column was already created by hand or by
-- an earlier migration (e.g. enquiries.extra_details).

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE leads ADD COLUMN address VARCHAR(500)',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'leads' AND column_name = 'address');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE leads ADD COLUMN pincode VARCHAR(20)',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'leads' AND column_name = 'pincode');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE leads ADD COLUMN gst_number VARCHAR(30)',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'leads' AND column_name = 'gst_number');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE leads ADD COLUMN project_name VARCHAR(255)',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'leads' AND column_name = 'project_name');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE leads ADD COLUMN quotation_date DATE',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'leads' AND column_name = 'quotation_date');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE quotations ADD COLUMN area_sqft DOUBLE',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'quotations' AND column_name = 'area_sqft');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE quotations ADD COLUMN rate_per_sqft DECIMAL(14,2)',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'quotations' AND column_name = 'rate_per_sqft');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE enquiries ADD COLUMN extra_details LONGTEXT',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'enquiries' AND column_name = 'extra_details');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS lead_cover_images (
    lead_id      BIGINT       NOT NULL PRIMARY KEY,
    content_type VARCHAR(100) NOT NULL,
    data         LONGBLOB     NOT NULL,
    CONSTRAINT fk_cover_lead FOREIGN KEY (lead_id) REFERENCES leads(id) ON DELETE CASCADE
) ENGINE=InnoDB;
