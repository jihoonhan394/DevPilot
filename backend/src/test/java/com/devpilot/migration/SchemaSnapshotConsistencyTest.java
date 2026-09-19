package com.devpilot.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.PostgresTestcontainersConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * docs/09 §8.1: {@code database/schema.sql}(스냅샷)과 전체 migration 적용 결과가 같은지 비교한다 (docs/04 §10).
 * 컬럼(순서·타입· nullable·default), 제약(정의 문자열 — 이름 무관), 인덱스 정의를 비교한다. Flyway 이력 테이블은 제외한다.
 */
@IntegrationTest
class SchemaSnapshotConsistencyTest {

    private static final String COLUMNS =
            "select table_name, column_name, ordinal_position, data_type,"
                    + " character_maximum_length, is_nullable, column_default, udt_name"
                    + " from information_schema.columns where table_schema = 'devpilot'"
                    + " and table_name <> 'flyway_schema_history' order by 1, 3";
    private static final String CONSTRAINTS =
            "select conrelid::regclass::text, pg_get_constraintdef(oid) from pg_constraint where"
                + " connamespace = 'devpilot'::regnamespace and (select relname from pg_class where"
                + " oid = conrelid) <> 'flyway_schema_history' order by 1, 2";
    private static final String INDEXES =
            "select tablename, indexname, indexdef from pg_indexes where schemaname = 'devpilot'"
                    + " and tablename <> 'flyway_schema_history' order by 1, 2";

    @Test
    void shouldMatchMigrationsWhenSchemaSnapshotIsApplied() throws SQLException, IOException {
        PostgreSQLContainer container = PostgresTestcontainersConfig.container();
        String snapshotDb =
                "snapshot_"
                        + UUID.randomUUID()
                                .toString()
                                .replace("-", "")
                                .substring(0, 12)
                                .toLowerCase(Locale.ROOT);
        try (Connection admin = connect(container, container.getDatabaseName());
                Statement statement = admin.createStatement()) {
            statement.execute("create database " + snapshotDb);
        }
        String schemaSql =
                Files.readString(Path.of("../database/schema.sql"), StandardCharsets.UTF_8);

        // schema.sql 이 세션 search_path 를 바꾸므로 실행 연결과 비교 연결을 분리한다
        try (Connection loader = connect(container, snapshotDb);
                Statement statement = loader.createStatement()) {
            statement.execute(schemaSql);
        }
        try (Connection migrated = connect(container, container.getDatabaseName());
                Connection snapshot = connect(container, snapshotDb)) {
            for (String query : List.of(COLUMNS, CONSTRAINTS, INDEXES)) {
                assertThat(rows(snapshot, query)).as(query).isEqualTo(rows(migrated, query));
            }
        }
    }

    private static Connection connect(PostgreSQLContainer container, String database)
            throws SQLException {
        String url =
                container
                        .getJdbcUrl()
                        .replaceFirst(
                                "/" + container.getDatabaseName() + "(\\?|$)",
                                "/" + database + "$1");
        return DriverManager.getConnection(url, container.getUsername(), container.getPassword());
    }

    private static List<List<String>> rows(Connection connection, String query)
            throws SQLException {
        List<List<String>> result = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(query)) {
            int columns = resultSet.getMetaData().getColumnCount();
            while (resultSet.next()) {
                List<String> row = new ArrayList<>();
                for (int i = 1; i <= columns; i++) {
                    row.add(String.valueOf(resultSet.getString(i)));
                }
                result.add(row);
            }
        }
        return result;
    }
}
