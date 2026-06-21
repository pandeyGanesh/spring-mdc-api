package in.ganeshpandey.spring_mdc_api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SpringMdcApiApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void testPostEndpointAndInterceptor() throws Exception {
		String jsonBody = "{\"key\": \"someValue\"}";

		mockMvc.perform(post("/test")
						.contentType(MediaType.APPLICATION_JSON)
						.content(jsonBody)
						.header("X-Custom-Test-Header", "HelloMDC"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.thread-name", notNullValue()))
				.andExpect(jsonPath("$.mdc-values.X-Custom-Test-Header").value("HelloMDC"));
	}

}
