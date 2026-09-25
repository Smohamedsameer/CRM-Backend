-- Lets an admin reply (over WhatsApp) to a customer's "request changes" note from the new
-- Client Request page, and records that a reply was sent.
ALTER TABLE customer_responses
    ADD COLUMN admin_reply VARCHAR(2000) NULL,
    ADD COLUMN replied_at  DATETIME      NULL;
