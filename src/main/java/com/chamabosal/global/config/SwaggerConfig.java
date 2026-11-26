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
        // 프록시 뒤에서 http로 잡히지 않게 상대 경로로 둔다.
        servers = @Server(url = "/", description = "Swagger를 연 서버"),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class SwaggerConfig {
}
