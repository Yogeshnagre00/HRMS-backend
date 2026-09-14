-- =============================================================================
-- V4 - RBAC seed data (HRMS Payroll MVP, task V0-003)
--
-- Deterministic, repeatable, version-controlled seed of the v0 roles, the
-- explicit permission catalogue for currently-implemented functionality, and
-- the least-privilege role -> permission mappings.
--
-- Fixed UUIDs are used so the mappings are self-referential within this
-- migration and stable/predictable for tests. No user accounts are seeded here
-- (initial user provisioning is an operational concern, not part of V0-003
-- scope). Roles EMPLOYEE and MANAGER are reserved for future ESS/MSS and are
-- marked non-assignable so v0 cannot grant login/ESS/MSS via them.
--
-- Least privilege: no role receives every permission. Mappings are limited to
-- what the v0 API/security foundation and currently-implemented modules need.
-- Detailed payroll permissions are intentionally NOT fabricated before the
-- payroll module exists; PAYROLL_ADMIN gets a single payroll administration
-- permission required by the API contract for the role.
-- =============================================================================

-- --- Roles -----------------------------------------------------------------
INSERT INTO role (id, code, name, scope_type, assignable, created_at) VALUES
  ('00000000-0000-0000-0000-000000000101', 'SUPER_ADMIN',   'Super Administrator', 'PLATFORM', TRUE,  CURRENT_TIMESTAMP),
  ('00000000-0000-0000-0000-000000000102', 'COMPANY_ADMIN', 'Company Administrator','COMPANY',  TRUE,  CURRENT_TIMESTAMP),
  ('00000000-0000-0000-0000-000000000103', 'PAYROLL_ADMIN', 'Payroll Administrator','COMPANY',  TRUE,  CURRENT_TIMESTAMP),
  -- Reserved for future modules; not assignable in v0.
  ('00000000-0000-0000-0000-000000000104', 'EMPLOYEE',      'Employee (reserved)',  'COMPANY',  FALSE, CURRENT_TIMESTAMP),
  ('00000000-0000-0000-0000-000000000105', 'MANAGER',       'Manager (reserved)',   'COMPANY',  FALSE, CURRENT_TIMESTAMP);

-- --- Permissions -----------------------------------------------------------
INSERT INTO permission (id, code, description, created_at) VALUES
  ('00000000-0000-0000-0000-000000000201', 'security.admin',  'Administer authentication/security settings',       CURRENT_TIMESTAMP),
  ('00000000-0000-0000-0000-000000000202', 'user.read',       'Read user accounts and their role assignments',     CURRENT_TIMESTAMP),
  ('00000000-0000-0000-0000-000000000203', 'user.admin',      'Create/modify users and assign roles',              CURRENT_TIMESTAMP),
  ('00000000-0000-0000-0000-000000000204', 'role.read',       'Read roles',                                        CURRENT_TIMESTAMP),
  ('00000000-0000-0000-0000-000000000205', 'role.admin',      'Create/modify roles and role-permission mappings',  CURRENT_TIMESTAMP),
  ('00000000-0000-0000-0000-000000000206', 'permission.read', 'Read the permission catalogue',                     CURRENT_TIMESTAMP),
  ('00000000-0000-0000-0000-000000000207', 'company.admin',   'Administer company-scoped configuration',           CURRENT_TIMESTAMP),
  ('00000000-0000-0000-0000-000000000208', 'audit.read',      'Read audit log records',                            CURRENT_TIMESTAMP),
  ('00000000-0000-0000-0000-000000000209', 'payroll.admin',   'Administer payroll operations (module added later)', CURRENT_TIMESTAMP);

-- --- Role -> Permission mappings (least privilege) -------------------------
-- SUPER_ADMIN (PLATFORM): platform + user/role/permission administration and
-- audit read, sufficient to provision companies and users in later tasks.
INSERT INTO role_permission (id, role_id, permission_id) VALUES
  ('00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000201'),
  ('00000000-0000-0000-0000-000000000302', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000202'),
  ('00000000-0000-0000-0000-000000000303', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000203'),
  ('00000000-0000-0000-0000-000000000304', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000204'),
  ('00000000-0000-0000-0000-000000000305', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000205'),
  ('00000000-0000-0000-0000-000000000306', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000206'),
  ('00000000-0000-0000-0000-000000000307', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000207'),
  ('00000000-0000-0000-0000-000000000308', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000208');

-- COMPANY_ADMIN (COMPANY): company administration + user/role read + user admin
-- + audit read, within its own company scope (scope enforced server-side).
INSERT INTO role_permission (id, role_id, permission_id) VALUES
  ('00000000-0000-0000-0000-000000000321', '00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000202'),
  ('00000000-0000-0000-0000-000000000322', '00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000203'),
  ('00000000-0000-0000-0000-000000000323', '00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000204'),
  ('00000000-0000-0000-0000-000000000324', '00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000206'),
  ('00000000-0000-0000-0000-000000000325', '00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000207'),
  ('00000000-0000-0000-0000-000000000326', '00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000208');

-- PAYROLL_ADMIN (COMPANY): payroll administration + audit read only.
INSERT INTO role_permission (id, role_id, permission_id) VALUES
  ('00000000-0000-0000-0000-000000000341', '00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000209'),
  ('00000000-0000-0000-0000-000000000342', '00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000208');

-- EMPLOYEE and MANAGER intentionally receive NO permissions in v0.
