package io.github.ale4694.partsflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PartsflowApplicationTests {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void contextLoadsAndFlywayAppliedBaseline() {
		Integer trgm = jdbc.queryForObject("select count(*) from pg_extension where extname = 'pg_trgm'", Integer.class);
		assertThat(trgm).isEqualTo(1);
	}
}
