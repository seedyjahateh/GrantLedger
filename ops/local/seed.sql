-- Explicit opt-in synthetic seed; never a production migration.
INSERT INTO department(id,code,name,active) VALUES
('10000000-0000-0000-0000-000000000001','RESEARCH','Research administration',true),
('10000000-0000-0000-0000-000000000002','MEDICINE','School of medicine',true)
ON CONFLICT(id) DO NOTHING;
