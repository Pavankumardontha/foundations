package com.zenith.catalog;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEventRepository implements EventRepository {

    private final DataSource dataSource;

    public JdbcEventRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<String> findAllNames() {
        List<String> names = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT name FROM event ORDER BY starts_at")) {
            while (rows.next()) {
                names.add(rows.getString("name"));
            }
            return names;
        } catch (SQLException e) {
            throw new RuntimeException("query failed", e);
        }
    }
}
