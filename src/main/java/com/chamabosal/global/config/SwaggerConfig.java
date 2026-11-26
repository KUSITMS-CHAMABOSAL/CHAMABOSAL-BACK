package com.chamabosal.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Chamabosal API",
                description = """
                        ### Chamabosal 서버 API 문서

                        에러 코드 목록은 [/docs/error-codes](/docs/error-codes)에서 확인할 수 있습니다.
                        """,
                version = "v1.0.0"
        ),
        // 프록시 뒤 운영에서 요청 URL로 만든 주소가 http로 잡혀 https Swagger에서 호출이 막혔다 (#225).
        // 상대 경로로 두면 Swagger를 연 주소(https)로 보내서 로컬·운영·K8s 모두 그대로 동작한다.
        servers = @Server(url = "/", description = "Swagger를 연 서버"),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class SwaggerConfig {
}
