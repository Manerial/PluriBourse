-- All test accounts use password "Admin" (BCrypt cost 10).
-- test_admin : ADMIN without forcePasswordChange — used for management scenario tests.
-- volunteer1/volunteer2 : fixture volunteers for scenario tests.
-- seller1 : fixture SELLER account — used to assert SELLER is blocked from web-facing endpoints (SecurityConfig).
-- global_instance_config.association_name : migration 004 seeds this blank ("") — most scenarios
-- assume an association that has already been configured, so it's set here to a non-blank value.
-- A dedicated test for the blank-name guard (EditionService.createEdition) blanks it back out
-- itself via the repository (the update endpoint's @NotBlank makes that state otherwise
-- unreachable through the API once a non-blank value is in place).

UPDATE global_instance_config SET association_name = 'Association Test' WHERE id = 1;

INSERT INTO users (username, password, role, preferred_language, force_password_change, first_name, last_name, enabled, language_initialized)
VALUES ('test_admin', '$2a$10$p/ss7jITZbA3ynvXulje2.NFSNXpmJ/HVI7QO8DhUvdokiO0QZXYW', 'ADMIN', 'FR', false, 'Test', 'Admin', true, true);

INSERT INTO users (username, password, role, preferred_language, force_password_change, first_name, last_name, enabled, language_initialized)
VALUES ('volunteer1', '$2a$10$p/ss7jITZbA3ynvXulje2.NFSNXpmJ/HVI7QO8DhUvdokiO0QZXYW', 'VOLUNTEER', 'FR', false, 'Alice', 'Smith', true, true);

INSERT INTO users (username, password, role, preferred_language, force_password_change, first_name, last_name, enabled, language_initialized)
VALUES ('volunteer2', '$2a$10$p/ss7jITZbA3ynvXulje2.NFSNXpmJ/HVI7QO8DhUvdokiO0QZXYW', 'VOLUNTEER', 'FR', false, 'Bob', 'Jones', true, true);

INSERT INTO users (username, password, role, preferred_language, force_password_change, first_name, last_name, enabled, language_initialized)
VALUES ('seller1', '$2a$10$p/ss7jITZbA3ynvXulje2.NFSNXpmJ/HVI7QO8DhUvdokiO0QZXYW', 'SELLER', 'FR', false, 'Claire', 'Petit', true, true);
