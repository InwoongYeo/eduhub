package com.eduhub.enrollment.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-04 수강 신청 API. local 초기 계정으로 실제 로그인해 받은 토큰을 쓰고, 회차는 관리자 API로 만든다.
 * 회차 시각은 실제 Clock 기준으로 지나지 않도록 먼 미래로 둔다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EnrollmentControllerTest {

	private static final String COURSE_JSON = """
			{"title": "신청 테스트", "description": "수강 신청", "category": "개발"}
			""";
	private static final String SESSION_JSON = """
			{"startsAt": "2099-01-10T09:00:00", "endsAt": "2099-01-10T18:00:00", "location": "본사 5층", "capacity": 2}
			""";

	@Autowired
	MockMvc mockMvc;

	@Test
	void 임직원이_신청하면_201과_CONFIRMED를_받는다() throws Exception {
		long sessionId = createSession(adminToken());

		mockMvc.perform(enroll(sessionId, employeeToken()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.enrollmentId").isNumber())
				.andExpect(jsonPath("$.sessionId").value(sessionId))
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.appliedAt").isNotEmpty());
	}

	@Test
	void 토큰_없이_신청하면_401() throws Exception {
		long sessionId = createSession(adminToken());

		mockMvc.perform(post("/api/v1/sessions/{id}/enrollments", sessionId))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("G002"));
	}

	@Test
	void 관리자_토큰으로_신청하면_403() throws Exception {
		String admin = adminToken();
		long sessionId = createSession(admin);

		mockMvc.perform(enroll(sessionId, admin))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("G003"));
	}

	@Test
	void 같은_회차를_두_번_신청하면_409_E001() throws Exception {
		long sessionId = createSession(adminToken());
		String employee = employeeToken();

		mockMvc.perform(enroll(sessionId, employee)).andExpect(status().isCreated());
		mockMvc.perform(enroll(sessionId, employee))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("E001"));
	}

	@Test
	void 없는_회차에_신청하면_404_C002() throws Exception {
		mockMvc.perform(enroll(999999L, employeeToken()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("C002"));
	}

	@Test
	void 종료된_회차에_신청하면_409_E002() throws Exception {
		String admin = adminToken();
		long sessionId = createSession(admin);
		mockMvc.perform(post("/api/v1/sessions/{id}/close", sessionId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
				.andExpect(status().isNoContent());

		mockMvc.perform(enroll(sessionId, employeeToken()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("E002"));
	}

	@Test
	void 임직원_토큰으로_회차를_수정하면_403() throws Exception {
		long sessionId = createSession(adminToken());

		mockMvc.perform(put("/api/v1/sessions/{id}", sessionId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken())
						.contentType(MediaType.APPLICATION_JSON).content(SESSION_JSON))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("G003"));
	}

	private MockHttpServletRequestBuilder enroll(long sessionId,
			String token) {
		return post("/api/v1/sessions/{id}/enrollments", sessionId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}

	private String adminToken() throws Exception {
		return login("admin@eduhub.local", "local-admin-only");
	}

	private String employeeToken() throws Exception {
		return login("employee@eduhub.local", "local-employee-only");
	}

	private String login(String email, String password) throws Exception {
		String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

	private long createSession(String adminToken) throws Exception {
		String courseBody = mockMvc.perform(post("/api/v1/courses")
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON).content(COURSE_JSON))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long courseId = JsonPath.<Number>read(courseBody, "$.id").longValue();
		String sessionBody = mockMvc.perform(post("/api/v1/courses/{id}/sessions", courseId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
						.contentType(MediaType.APPLICATION_JSON).content(SESSION_JSON))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.<Number>read(sessionBody, "$.id").longValue();
	}
}
