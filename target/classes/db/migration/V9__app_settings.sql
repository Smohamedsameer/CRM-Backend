-- Generic key/value store backing the new Settings page (Company Profile, Pricing Defaults).
-- A missing key means "use the application.yml / .env default" - see SettingsService.
CREATE TABLE app_settings (
    setting_key   VARCHAR(100)  NOT NULL PRIMARY KEY,
    setting_value VARCHAR(2000) NULL
);
