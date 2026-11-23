package com.skilltracker.student_skill_tracker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = { "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop" })
class StudentSkillTrackerApplicationTests {

	@Test
	void contextLoads() {
	}

}
