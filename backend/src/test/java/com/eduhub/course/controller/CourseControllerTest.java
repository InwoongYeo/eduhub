package com.eduhub.course.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * local 프로필의 초기 데이터(강의 3개)를 사용한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CourseControllerTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void 강의_목록은_로그인_없이_페이지_형식으로_조회된다() throws Exception {
		mockMvc.perform(get("/api/courses").param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.totalElements").value(3));
	}

	@Test
	void 강사_이름으로_검색할_수_있다() throws Exception {
		mockMvc.perform(get("/api/courses").param("keyword", "김강사"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3));
	}

	@Test
	void 없는_강의는_공통_오류_형식의_404를_돌려준다() throws Exception {
		mockMvc.perform(get("/api/courses/99999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("C001"))
				.andExpect(jsonPath("$.message").exists())
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void 보호된_API는_로그인하지_않으면_401을_돌려준다() throws Exception {
		mockMvc.perform(post("/api/courses"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("G002"));
	}

	@Test
	void local에서는_Swagger_문서가_열린다() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("EduHub API"));
	}
}
