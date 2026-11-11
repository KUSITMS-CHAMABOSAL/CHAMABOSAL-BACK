package com.chamabosal.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SwaggerConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void API_문서에_정보가_담기고_에러코드_페이지는_제외된다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Chamabosal API"))
                .andExpect(jsonPath("$.paths['/docs/error-codes']").doesNotExist());
    }

    @Test
    void Swagger_UI에_접속할_수_있다() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void Swagger에서_초안과_구현된_API를_고를_수_있다() throws Exception {
        mockMvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urls[?(@.url == '/dev/openapi.yaml')]").exists())
                .andExpect(jsonPath("$.urls[?(@.url == '/v3/api-docs')]").exists())
                .andExpect(jsonPath("$['urls.primaryName']").value("API 초안 (구현 전)"));
    }

    @Test
    void OpenAPI_초안은_토큰_없이_볼_수_있다() throws Exception {
        mockMvc.perform(get("/dev/openapi.yaml"))
                .andExpect(status().isOk());
    }

    @Test
    void 공개하지_않은_dev_경로는_토큰이_필요하다() throws Exception {
        mockMvc.perform(get("/dev/other.yaml"))
                .andExpect(status().isUnauthorized());
    }
}
