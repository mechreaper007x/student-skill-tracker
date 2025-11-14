package db.migration;

import java.util.List;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

public class V3__Delete_duplicate_skill_data extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource(context.getConnection(), true));

        List<Long> studentIds = jdbcTemplate.query("SELECT id FROM students", (rs, rowNum) -> rs.getLong("id"));

        for (Long studentId : studentIds) {
            List<Long> skillDataIds = jdbcTemplate.query(
                "SELECT id FROM skill_data WHERE student_id = ? ORDER BY created_at DESC",
                (rs, rowNum) -> rs.getLong("id"),
                studentId
            );

            if (skillDataIds.size() > 1) {
                for (int i = 1; i < skillDataIds.size(); i++) {
                    jdbcTemplate.update("DELETE FROM skill_data WHERE id = ?", skillDataIds.get(i));
                }
            }
        }
    }
}
