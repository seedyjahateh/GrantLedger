-- SYNTHETIC DATABASE ONLY. Explicit confirmation is required before this script runs.
\if :{?synthetic_database_confirmed}
\else
  \echo 'Pass -v synthetic_database_confirmed=true for a dedicated synthetic database.'
  \quit 3
\endif
BEGIN;
INSERT INTO department(id,code,name,active) VALUES ('10000000-0000-0000-0000-000000000001','RESEARCH','Synthetic research',true) ON CONFLICT(id) DO NOTHING;
INSERT INTO grant_record(id,award_number,department_id,title,sponsor_name,start_date,end_date,award_amount,currency,status,version,created_at,created_by_issuer,created_by_subject,updated_at,updated_by_issuer,updated_by_subject)
SELECT md5('load-grant-' || n)::uuid,'LOAD-' || n,'10000000-0000-0000-0000-000000000001','Synthetic load grant','Synthetic foundation',CURRENT_DATE-365,CURRENT_DATE+365,1000000.00,'USD','ACTIVE',0,now(),'synthetic-seed','seed',now(),'synthetic-seed','seed' FROM generate_series(1,1000) n;
INSERT INTO budget_allocation(id,grant_id,category,allocated_amount,created_at,updated_at,created_by_issuer,created_by_subject,updated_by_issuer,updated_by_subject)
SELECT md5(g.id::text || c)::uuid,g.id,c,200000.00,now(),now(),'synthetic-seed','seed','synthetic-seed','seed'
FROM grant_record g CROSS JOIN unnest(ARRAY['PERSONNEL','EQUIPMENT','SUPPLIES','TRAVEL','OTHER']) c WHERE g.award_number LIKE 'LOAD-%';
INSERT INTO ledger_entry(id,grant_id,budget_allocation_id,type,amount,effective_date,external_reference,description,original_entry_id,created_at,created_by_issuer,created_by_subject)
SELECT md5(g.id::text || '-expense-' || n)::uuid,g.id,md5(g.id::text || 'TRAVEL')::uuid,'EXPENSE',1.00,CURRENT_DATE-30,'SEED-' || n,'Synthetic expense',NULL,now(),'synthetic-seed','seed'
FROM grant_record g CROSS JOIN generate_series(1,900) n WHERE g.award_number LIKE 'LOAD-%';
INSERT INTO ledger_entry(id,grant_id,budget_allocation_id,type,amount,effective_date,external_reference,description,original_entry_id,created_at,created_by_issuer,created_by_subject)
SELECT md5(g.id::text || '-reversal-' || n)::uuid,g.id,md5(g.id::text || 'TRAVEL')::uuid,'REVERSAL',1.00,CURRENT_DATE-29,NULL,'Synthetic reversal',md5(g.id::text || '-expense-' || n)::uuid,now(),'synthetic-seed','seed'
FROM grant_record g CROSS JOIN generate_series(1,100) n WHERE g.award_number LIKE 'LOAD-%';
COMMIT;
ANALYZE;
SELECT string_agg(id::text, ',') AS grant_ids FROM (SELECT id FROM grant_record WHERE award_number LIKE 'LOAD-%' ORDER BY id LIMIT 20) selected;
