package com.chamabosal.global.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.global.discord.ServerErrorEvent;
import com.chamabosal.global.common.response.Response;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(GlobalExceptionHandlerTest.TestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandlerTest.TestController.class)
@RecordApplicationEvents
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationEvents events;

    @Test
    void 성공_응답은_200과_data를_반환한다() throws Exception {
        mockMvc.perform(get("/test/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data").value("hello"));
    }

    @Test
    void 생성_응답은_201을_반환한다() throws Exception {
        mockMvc.perform(post("/test/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제목\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("201"))
                .andExpect(jsonPath("$.message").value("생성되었습니다."))
                .andExpect(jsonPath("$.data").value("제목"));
    }

    @Test
    void BaseException은_ErrorCode의_상태와_코드로_변환된다() throws Exception {
        mockMvc.perform(get("/test/base-exception"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_001"))
                .andExpect(jsonPath("$.field").doesNotExist());
    }

    @Test
    void 요청_본문_검증_실패는_COMMON_002와_field를_반환한다() throws Exception {
        mockMvc.perform(post("/test/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"))
                .andExpect(jsonPath("$.message").value("제목은 필수입니다."))
                .andExpect(jsonPath("$.field").value("title"));
    }

    @Test
    void 읽을_수_없는_본문은_COMMON_003을_반환한다() throws Exception {
        mockMvc.perform(post("/test/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_003"));
    }

    @Test
    void 필수_파라미터_누락은_COMMON_004를_반환한다() throws Exception {
        mockMvc.perform(get("/test/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_004"))
                .andExpect(jsonPath("$.field").value("name"));
    }

    @Test
    void 타입_불일치는_COMMON_005를_반환한다() throws Exception {
        mockMvc.perform(get("/test/items/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_005"))
                .andExpect(jsonPath("$.field").value("id"));
    }

    @Test
    void 지원하지_않는_메서드는_COMMON_006을_반환한다() throws Exception {
        mockMvc.perform(post("/test/ok"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"))
                .andExpect(jsonPath("$.code").value("COMMON_006"));
    }

    @Test
    void 지원하지_않는_ContentType은_COMMON_007을_반환한다() throws Exception {
        mockMvc.perform(post("/test/items")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("title"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("COMMON_007"));
    }

    @Test
    void 개별_처리하지_않은_4xx_예외는_원래_상태와_COMMON_010을_반환한다() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON_010"));
    }

    @Test
    void 개별_처리하지_않은_5xx_예외는_원래_상태와_COMMON_999를_반환한다() throws Exception {
        mockMvc.perform(get("/test/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("COMMON_999"));
    }

    @Test
    void 처리되지_않은_예외는_COMMON_999를_반환한다() throws Exception {
        mockMvc.perform(get("/test/unhandled"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON_999"));
    }

    @Test
    void 처리되지_않은_예외는_요청_정보와_함께_ServerErrorEvent를_발행한다() throws Exception {
        mockMvc.perform(get("/test/unhandled"));

        assertThat(events.stream(ServerErrorEvent.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.method()).isEqualTo("GET");
                    assertThat(event.uri()).isEqualTo("/test/unhandled");
                    assertThat(event.status()).isEqualTo(500);
                    assertThat(event.errorCode()).isEqualTo("COMMON_999");
                    assertThat(event.exceptionClass()).isEqualTo(IllegalStateException.class.getName());
                });
    }

    @Test
    void 개별_처리하지_않은_5xx_예외는_원래_상태로_ServerErrorEvent를_발행한다() throws Exception {
        mockMvc.perform(get("/test/unavailable"));

        assertThat(events.stream(ServerErrorEvent.class))
                .singleElement()
                .satisfies(event -> assertThat(event.status()).isEqualTo(503));
    }

    @Test
    void 서버_에러_BaseException은_ServerErrorEvent를_발행한다() throws Exception {
        mockMvc.perform(get("/test/base-server-error"))
                .andExpect(status().isInternalServerError());

        assertThat(events.stream(ServerErrorEvent.class)).hasSize(1);
    }

    @Test
    void 클라이언트_에러는_ServerErrorEvent를_발행하지_않는다() throws Exception {
        mockMvc.perform(get("/test/base-exception"));
        mockMvc.perform(get("/test/conflict"));

        assertThat(events.stream(ServerErrorEvent.class)).isEmpty();
    }

    @Test
    void 클라이언트_연결_끊김은_ServerErrorEvent를_발행하지_않는다() throws Exception {
        mockMvc.perform(get("/test/disconnected"));

        assertThat(events.stream(ServerErrorEvent.class)).isEmpty();
    }

    @Test
    void 메시지가_연결_끊김처럼_보여도_서버_쪽_IO_오류는_ServerErrorEvent를_발행한다() throws Exception {
        mockMvc.perform(get("/test/connection-reset"))
                .andExpect(status().isInternalServerError());

        assertThat(events.stream(ServerErrorEvent.class)).hasSize(1);
    }

    record TestCreateRequest(@NotBlank(message = "제목은 필수입니다.") String title) {
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/ok")
        ResponseEntity<Response<String>> ok() {
            return ResponseEntity.ok(Response.ok("hello"));
        }

        @PostMapping("/items")
        ResponseEntity<Response<String>> create(@RequestBody @Valid TestCreateRequest request) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Response.created("생성되었습니다.", request.title()));
        }

        @GetMapping("/items/{id}")
        ResponseEntity<Response<Long>> getItem(@PathVariable Long id) {
            return ResponseEntity.ok(Response.ok(id));
        }

        @GetMapping("/param")
        ResponseEntity<Response<String>> param(@RequestParam String name) {
            return ResponseEntity.ok(Response.ok(name));
        }

        @GetMapping("/base-exception")
        void baseException() {
            throw new BaseException(BaseErrorCode.ENDPOINT_NOT_FOUND);
        }

        @GetMapping("/base-server-error")
        void baseServerError() {
            throw new BaseException(BaseErrorCode.UNHANDLED_ERROR);
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }

        @GetMapping("/unavailable")
        void unavailable() {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        }

        @GetMapping("/disconnected")
        void disconnected() throws AsyncRequestNotUsableException {
            throw new AsyncRequestNotUsableException("Broken pipe");
        }

        @GetMapping("/connection-reset")
        void connectionReset() throws IOException {
            throw new IOException("Connection reset by peer");
        }

        @GetMapping("/unhandled")
        void unhandled() {
            throw new IllegalStateException("boom");
        }
    }
}
