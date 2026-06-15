package org.pluribourse.shared;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: verifies Liquibase changesets 001 and 002 execute correctly on H2 (MODE=MySQL).
 */
@SpringBootTest
class LiquibaseMigrationIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void usersTableExists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE UPPER(table_name) = 'USERS' AND UPPER(table_schema) = 'PUBLIC'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void springSessionTableExists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE UPPER(table_name) = 'SPRING_SESSION' AND UPPER(table_schema) = 'PUBLIC'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void defaultAdminAccountExists() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = 'Admin' AND role = 'ADMIN' AND force_password_change = TRUE",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void usersTableHasAllRequiredColumns() {
        List<String> columns = jdbcTemplate.queryForList(
                "SELECT UPPER(column_name) FROM information_schema.columns WHERE UPPER(table_name) = 'USERS' AND UPPER(table_schema) = 'PUBLIC'",
                String.class);
        assertThat(columns).containsExactlyInAnyOrder(
                "ID", "USERNAME", "PASSWORD", "ROLE",
                "PREFERRED_LANGUAGE", "SELLER_PROFILE_ID", "FORCE_PASSWORD_CHANGE");
    }
}
