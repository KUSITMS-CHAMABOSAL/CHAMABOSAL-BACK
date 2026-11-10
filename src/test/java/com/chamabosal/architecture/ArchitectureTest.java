package com.chamabosal.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.chamabosal.global.common.entity.BaseEntity;
import com.chamabosal.global.common.response.Response;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * CLAUDE.md·CONVENTION.md의 규칙 중 기계로 확인할 수 있는 것을 검사한다.
 * 규칙을 통과시키려고 이 테스트를 약하게 고치지 않는다. 규칙을 바꿔야 하면 문서를 먼저 고친다.
 */
@AnalyzeClasses(packages = "com.chamabosal", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String SWAGGER_REQUEST_BODY = "io.swagger.v3.oas.annotations.parameters.RequestBody";

    @ArchTest
    static final ArchRule 현재_시각은_주입받은_Clock으로_구한다 = noClasses()
            .should().callMethod(LocalDateTime.class, "now")
            .orShould().callMethod(LocalDate.class, "now")
            .orShould().callMethod(LocalTime.class, "now")
            .orShould().callMethod(Instant.class, "now")
            .orShould().callMethod(ZonedDateTime.class, "now")
            .orShould().callMethod(OffsetDateTime.class, "now")
            .orShould().callMethod(System.class, "currentTimeMillis")
            .because("현재 시각은 주입받은 Clock으로 구해야 시간 시나리오를 고정된 Clock으로 테스트할 수 있다");

    @ArchTest
    static final ArchRule 엔티티는_BaseEntity를_상속한다 = classes()
            .that().areAnnotatedWith(Entity.class)
            .should().beAssignableTo(BaseEntity.class)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule 엔티티의_enum_필드는_문자열로_저장한다 = fields()
            .that().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
            .and(enumType())
            .should(beEnumeratedAsString())
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule DTO는_record로_작성한다 = classes()
            .that().resideInAPackage("..dto..")
            .and().areTopLevelClasses()
            .should().beRecords()
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule 요청_바디는_Spring의_RequestBody로_받는다 = methods()
            .should(notHaveParameterAnnotatedWith(SWAGGER_REQUEST_BODY))
            .because("Swagger의 RequestBody는 문서용이라 요청 바디를 바인딩하지 않는다");

    @ArchTest
    static final ArchRule API_컨트롤러는_ResponseEntity_Response를_반환한다 = methods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
            .and().arePublic()
            .should(returnResponseEntityOfResponse())
            .allowEmptyShould(true);

    private static DescribedPredicate<JavaField> enumType() {
        return DescribedPredicate.describe("enum 타입이다", field -> field.getRawType().isEnum());
    }

    private static ArchCondition<JavaField> beEnumeratedAsString() {
        return new ArchCondition<>("@Enumerated(EnumType.STRING)이 붙어 있다") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                boolean satisfied = field.tryGetAnnotationOfType(Enumerated.class)
                        .map(enumerated -> enumerated.value() == EnumType.STRING)
                        .orElse(false);
                events.add(new SimpleConditionEvent(field, satisfied,
                        field.getFullName() + "에 @Enumerated(EnumType.STRING)이 없습니다"));
            }
        };
    }

    private static ArchCondition<JavaMethod> notHaveParameterAnnotatedWith(String annotationName) {
        return new ArchCondition<>("파라미터에 " + annotationName + "을 쓰지 않는다") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean violated = method.getParameters().stream()
                        .anyMatch(parameter -> parameter.isAnnotatedWith(annotationName));
                events.add(new SimpleConditionEvent(method, !violated,
                        method.getFullName() + "의 파라미터에 Swagger RequestBody가 있습니다. "
                                + "org.springframework.web.bind.annotation.RequestBody를 쓰세요"));
            }
        };
    }

    private static ArchCondition<JavaMethod> returnResponseEntityOfResponse() {
        return new ArchCondition<>("ResponseEntity<Response<T>>를 반환한다") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                events.add(new SimpleConditionEvent(method, isResponseEntityOfResponse(method.getReturnType()),
                        method.getFullName() + "의 반환 타입이 ResponseEntity<Response<T>>가 아닙니다"));
            }
        };
    }

    private static boolean isResponseEntityOfResponse(JavaType returnType) {
        if (!returnType.toErasure().isEquivalentTo(ResponseEntity.class)
                || !(returnType instanceof JavaParameterizedType parameterized)) {
            return false;
        }
        return parameterized.getActualTypeArguments().stream()
                .findFirst()
                .map(argument -> argument.toErasure().isEquivalentTo(Response.class))
                .orElse(false);
    }
}
