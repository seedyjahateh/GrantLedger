-- Run as the migration/schema owner after Flyway; the login must already exist.
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM grantledger_app;
GRANT USAGE ON SCHEMA public TO grantledger_app;
GRANT SELECT ON department,grant_record,budget_allocation,ledger_entry,audit_event,idempotency_record TO grantledger_app;
GRANT INSERT,UPDATE ON grant_record,budget_allocation TO grantledger_app;
GRANT INSERT ON ledger_entry,audit_event,idempotency_record TO grantledger_app;
GRANT SELECT,INSERT,UPDATE,DELETE ON spring_session,spring_session_attributes TO grantledger_app;
ALTER ROLE grantledger_app SET statement_timeout = '5s';
