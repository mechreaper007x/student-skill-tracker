package com.skilltracker.student_skill_tracker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import org.springframework.test.annotation.DirtiesContext;
import com.skilltracker.student_skill_tracker.model.SkillData;
import com.skilltracker.student_skill_tracker.service.SkillService;
import org.springframework.boot.test.mock.mockito.MockBean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class StudentSkillTrackerApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SkillService skillService;

	@Test
	void contextLoads() {
	}

    @Test
    void getStudents_shouldReturnOkAndEmptyArray() throws Exception {
        mockMvc.perform(get("/api/students"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void registerStudent_shouldReturnCreated() throws Exception {
        // Mock the skillService to return an empty SkillData object
        when(skillService.updateSkillData(any())).thenReturn(new SkillData());

        // Register a new user
        mockMvc.perform(post("/api/students/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test User\",\"email\":\"test@example.com\",\"password\":\"password\",\"leetcodeUsername\":\"testuser\"}"))
                .andExpect(status().isCreated());
    }
}
