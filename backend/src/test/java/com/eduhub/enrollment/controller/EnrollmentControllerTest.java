package com.eduhub.enrollment.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eduhub.user.domain.Role;
import com.eduhub.user.domain.User;
import com.eduhub.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
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

	@Autowired
	UserRepository userRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Test
	void 임직원이_신청하면_201과_CONFIRMED를_받는다() throws Exception {
		long sessionId = createSession(adminToken());

		mockMvc.perform(enroll(sessionId, employeeToken()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.enrollmentId").isNumber())
				.andExpect(jsonPath("$.sessionId").value(sessionId))
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.appliedAt").isNotEmpty())
				.andExpect(jsonPath("$.waitingNo").hasJsonPath())
				.andExpect(jsonPath("$.waitingNo").value(nullValue()));
	}

	@Test
	void 신청_직후_내_신청_상태를_조회하면_CONFIRMED이고_순번은_null이다() throws Exception {
		long sessionId = createSession(adminToken());
		String employee = employeeToken();
		mockMvc.perform(enroll(sessionId, employee)).andExpect(status().isCreated());

		mockMvc.perform(findMine(sessionId, employee))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sessionId").value(sessionId))
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.waitingNo").hasJsonPath())
				.andExpect(jsonPath("$.waitingNo").value(nullValue()));
	}

	@Test
	void 신청한_적_없는_회차의_내_상태_조회는_404_E004() throws Exception {
		long sessionId = createSession(adminToken());

		mockMvc.perform(findMine(sessionId, employeeToken()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("E004"));
	}

	@Test
	void 없는_회차의_내_상태_조회는_404_C002() throws Exception {
		mockMvc.perform(findMine(999999L, employeeToken()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("C002"));
	}

	@Test
	void 관리자_토큰으로_내_상태를_조회하면_403() throws Exception {
		String admin = adminToken();
		long sessionId = createSession(admin);

		mockMvc.perform(findMine(sessionId, admin))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("G003"));
	}

	@Test
	void 토큰_없이_내_상태를_조회하면_401() throws Exception {
		long sessionId = createSession(adminToken());

		mockMvc.perform(get("/api/v1/sessions/{id}/enrollments/me", sessionId))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("G002"));
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

	@Test
	void 신청_후_취소하면_200_CANCELLED이고_순번은_null이며_내_상태도_CANCELLED이다() throws Exception {
		long sessionId = createSession(adminToken());
		String employee = employeeToken();
		mockMvc.perform(enroll(sessionId, employee)).andExpect(status().isCreated());

		mockMvc.perform(cancel(sessionId, employee))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sessionId").value(sessionId))
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.waitingNo").hasJsonPath())
				.andExpect(jsonPath("$.waitingNo").value(nullValue()));
		mockMvc.perform(findMine(sessionId, employee))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));
	}

	@Test
	void 이미_취소한_신청을_다시_취소하면_409_E005() throws Exception {
		long sessionId = createSession(adminToken());
		String employee = employeeToken();
		mockMvc.perform(enroll(sessionId, employee)).andExpect(status().isCreated());
		mockMvc.perform(cancel(sessionId, employee)).andExpect(status().isOk());

		mockMvc.perform(cancel(sessionId, employee))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("E005"));
	}

	@Test
	void 신청이_없는_회차를_취소하면_404_E004() throws Exception {
		long sessionId = createSession(adminToken());

		mockMvc.perform(cancel(sessionId, employeeToken()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("E004"));
	}

	@Test
	void 없는_회차를_취소하면_404_C002() throws Exception {
		mockMvc.perform(cancel(999999L, employeeToken()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("C002"));
	}

	@Test
	void 관리자_토큰으로_취소하면_403() throws Exception {
		String admin = adminToken();
		long sessionId = createSession(admin);

		mockMvc.perform(cancel(sessionId, admin))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("G003"));
	}

	@Test
	void 토큰_없이_취소하면_401() throws Exception {
		long sessionId = createSession(adminToken());

		mockMvc.perform(post("/api/v1/sessions/{id}/enrollments/me/cancel", sessionId))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("G002"));
	}

	@Test
	void 취소_후_다시_신청하면_201_CONFIRMED이다() throws Exception {
		long sessionId = createSession(adminToken());
		String employee = employeeToken();
		mockMvc.perform(enroll(sessionId, employee)).andExpect(status().isCreated());
		mockMvc.perform(cancel(sessionId, employee)).andExpect(status().isOk());

		mockMvc.perform(enroll(sessionId, employee))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("CONFIRMED"));
	}

	@Test
	void 임직원_토큰으로_회차를_종료하면_403이다_취소_규칙이_다른_경로를_열지_않는다() throws Exception {
		long sessionId = createSession(adminToken());

		mockMvc.perform(post("/api/v1/sessions/{id}/close", sessionId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("G003"));
	}

	@Test
	void 다른_임직원의_신청은_취소할_수_없다_본인_신청만_취소_가능() throws Exception {
		long sessionId = createSession(adminToken());
		String employeeA = employeeToken();
		mockMvc.perform(enroll(sessionId, employeeA)).andExpect(status().isCreated());
		String password = "b-test-password";
		String emailB = "b-" + UUID.randomUUID() + "@test.local";
		userRepository.saveAndFlush(
				User.create(emailB, passwordEncoder.encode(password), "임직원B", Role.EMPLOYEE));
		String employeeB = login(emailB, password);

		mockMvc.perform(cancel(sessionId, employeeB))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("E004"));

		mockMvc.perform(findMine(sessionId, employeeA))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"));
		String courses = mockMvc.perform(get("/api/v1/courses").param("keyword", "신청 테스트"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		long courseId = JsonPath.<Number>read(courses, "$.content[0].id").longValue();
		mockMvc.perform(get("/api/v1/courses/{id}", courseId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sessions[0].id").value(sessionId))
				.andExpect(jsonPath("$.sessions[0].remainingSeats").value(1));
	}

	private MockHttpServletRequestBuilder cancel(long sessionId, String token) {
		return post("/api/v1/sessions/{id}/enrollments/me/cancel", sessionId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}

	private MockHttpServletRequestBuilder findMine(long sessionId, String token) {
		return get("/api/v1/sessions/{id}/enrollments/me", sessionId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
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
