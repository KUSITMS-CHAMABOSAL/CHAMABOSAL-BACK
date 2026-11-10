package com.chamabosal.global.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** 에러 code는 프론트와의 약속이므로, 기록된 code가 바뀌거나 사라지면 실패한다. */
class ErrorCodeSnapshotTest {

    private static final String SNAPSHOT = "/error-codes.snapshot";

    @Test
    void 기록된_에러_code는_바뀌거나_사라지지_않는다() throws IOException {
        Set<String> missing = new TreeSet<>(snapshotCodes());
        missing.removeAll(currentCodes());

        assertThat(missing)
                .as("기존 에러 code가 바뀌었거나 사라졌습니다. 정해진 code는 바꾸지 말고 되돌리세요")
                .isEmpty();
    }

    @Test
    void 새_에러_code는_스냅샷에_기록한다() throws IOException {
        Set<String> unrecorded = new TreeSet<>(currentCodes());
        unrecorded.removeAll(snapshotCodes());

        assertThat(unrecorded)
                .as("새 에러 code를 src/test/resources%s에 추가하세요", SNAPSHOT)
                .isEmpty();
    }

    /** 테스트 코드를 제외한 애플리케이션의 ErrorCode enum에서 code를 모은다. */
    private static Set<String> currentCodes() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.chamabosal")
                .stream()
                .filter(JavaClass::isEnum)
                .filter(javaClass -> javaClass.isAssignableTo(ErrorCode.class))
                .flatMap(javaClass -> Arrays.stream(javaClass.reflect().getEnumConstants()))
                .map(constant -> ((ErrorCode) constant).getCode())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> snapshotCodes() throws IOException {
        try (InputStream input = ErrorCodeSnapshotTest.class.getResourceAsStream(SNAPSHOT)) {
            assertThat(input).as("%s 파일이 없습니다", SNAPSHOT).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }
}
