package com.chamabosal.domain.certification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.global.config.ClockConfig;
import com.chamabosal.global.security.jwt.JwtProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 공통 Given: 방 인원은 A, B, C, D 4명이다. 시나리오마다 서버가 요청을 받는 시각을 바꾼다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CertificationCreateTest.MutableClockConfig.class)
class CertificationCreateTest {

    private static final LocalDate NOV_30 = LocalDate.of(2026, 11, 30);
    private static final LocalDate DEC_1 = LocalDate.of(2026, 12, 1);

    @TestConfiguration
    static class MutableClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    /** 테스트마다 서버 시각을 바꿀 수 있는 Clock. */
    static class MutableClock extends Clock {

        private volatile Instant instant = Instant.now();

        void set(LocalDateTime time) {
            instant = time.atZone(ClockConfig.KST).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return ClockConfig.KST;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoomRepository roomRepository;

    @Autowired
    RoomMemberRepository roomMemberRepository;

    @Autowired
    RoundRepository roundRepository;

    @Autowired
    CertificationRepository certificationRepository;

    private int userSequence;
    private Room room;
    private User userA;
    private User userB;
    private RoomMember memberA;
    private RoomMember memberB;

    @BeforeEach
    void setUp() {
        clock.set(DEC_1.atTime(20, 0));
        room = roomRepository.save(Room.create("배달 끊기 방", RoomGoal.DELIVERY, 4));
        userA = newUser();
        userB = newUser();
        memberA = addMember(userA, "민지", BosalColor.PINK);
        memberB = addMember(userB, "지수", BosalColor.PURPLE);
        addMember(newUser(), "하린", BosalColor.MINT);
        addMember(newUser(), "도윤", BosalColor.YELLOW);
    }

    @AfterEach
    void tearDown() {
        certificationRepository.deleteAll();
        roundRepository.deleteAll();
        roomMemberRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void CERT_01_1_참았어요_인증이_저장되고_수정_가능_종료_시각은_10분_뒤다() throws Exception {
        clock.set(DEC_1.atTime(19, 24));

        certify(userA, """
                {"result": "ENDURED", "memo": "집밥 해먹었어요"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roundDate").value("2026-12-01"))
                .andExpect(jsonPath("$.data.certifiedAt").value("2026-12-01T19:24:00"))
                .andExpect(jsonPath("$.data.editableUntil").value("2026-12-01T19:34:00"))
                .andExpect(jsonPath("$.data.isVoteStarted").value(false));

        Certification saved = onlyCertification();
        assertThat(saved.getMember().getId()).isEqualTo(memberA.getId());
        assertThat(saved.getMemo()).isEqualTo("집밥 해먹었어요");
        assertThat(saved.getSpentAmount()).isNull();
    }

    @Test
    void CERT_01_2_못_참았어요를_금액_없이_보내면_거부한다() throws Exception {
        certify(userA, """
                {"result": "FAILED", "memo": "치킨 시켰어요"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"))
                .andExpect(jsonPath("$.field").value("spentAmount"));

        assertThat(certificationRepository.count()).isZero();
    }

    @Test
    void CERT_01_3_금액이_999999원을_넘으면_거부한다() throws Exception {
        certify(userA, """
                {"result": "FAILED", "memo": "치킨 시켰어요", "spentAmount": 1000000}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("spentAmount"));
        certify(userA, """
                {"result": "FAILED", "memo": "치킨 시켰어요", "spentAmount": 999999}
                """)
                .andExpect(status().isCreated());

        assertThat(onlyCertification().getSpentAmount()).isEqualTo(999_999);
    }

    @Test
    void CERT_01_4_한_줄_기록이_비어_있거나_41자면_거부한다() throws Exception {
        for (String memo : List.of("", "   ", "가".repeat(41))) {
            certify(userA, "{\"result\": \"ENDURED\", \"memo\": \"" + memo + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.field").value("memo"));
        }
        certify(userA, "{\"result\": \"ENDURED\", \"memo\": \" " + "가".repeat(38) + " \"}")
                .andExpect(status().isCreated());

        assertThat(onlyCertification().getMemo()).isEqualTo(" " + "가".repeat(38) + " ");
    }

    @Test
    void CERT_01_5_이미_인증했으면_거부한다() throws Exception {
        certify(userA, endured()).andExpect(status().isCreated());

        certify(userA, endured())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CERT_001"));

        assertThat(certificationRepository.count()).isEqualTo(1);
    }

    @Test
    void CERT_01_6_방_멤버가_아니면_거부한다() throws Exception {
        certify(newUser(), endured())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROOM_002"));

        assertThat(certificationRepository.count()).isZero();
    }

    @Test
    void CERT_01_7_사진_없이_인증할_수_있다() throws Exception {
        certify(userA, endured()).andExpect(status().isCreated());

        assertThat(onlyCertification().getImageKey()).isNull();
    }

    @Test
    void CERT_01_8_03시를_넘겨_받은_등록은_거부하고_지난_회차는_인증_안_함으로_남는다() throws Exception {
        clock.set(NOV_30.atTime(21, 0));
        certify(userB, endured()).andExpect(status().isCreated());

        clock.set(DEC_1.atTime(3, 0, 1));
        certify(userA, endured())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CERT_006"));

        Round nov30 = roundRepository.findByRoom_IdAndRoundDate(room.getId(), NOV_30).orElseThrow();
        assertThat(certificationRepository.existsByRound_IdAndMember_IdAndDeletedAtIsNull(nov30.getId(), memberA.getId()))
                .isFalse();
    }

    @Test
    void CERT_01_11_03시_전_등록은_전날_회차로_들어간다() throws Exception {
        clock.set(DEC_1.atTime(2, 59));

        certify(userA, endured())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roundDate").value("2026-11-30"));
    }

    @Test
    void CERT_01_12_이전_회차_투표가_15시에_마감되면_그날_회차로_등록한다() throws Exception {
        saveClosedRound(NOV_30, RoundStatus.ANNOUNCED, DEC_1.atTime(15, 0));
        clock.set(DEC_1.atTime(15, 0, 1));

        certify(userA, endured())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roundDate").value("2026-12-01"));
    }

    @Test
    void CERT_01_13_이전_회차_투표가_일찍_끝나면_일찍_인증한다() throws Exception {
        saveClosedRound(NOV_30, RoundStatus.ANNOUNCED, DEC_1.atTime(10, 0));
        clock.set(DEC_1.atTime(10, 5));

        certify(userA, endured())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roundDate").value("2026-12-01"));
    }

    @Test
    void CERT_01_14_이전_회차가_투표_없음이면_바로_인증한다() throws Exception {
        saveClosedRound(NOV_30, RoundStatus.NO_VOTE, null);
        clock.set(DEC_1.atTime(3, 10));

        certify(userA, endured())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roundDate").value("2026-12-01"));
    }

    @Test
    void CERT_01_15_처음_만든_방은_바로_인증한다() throws Exception {
        clock.set(DEC_1.atTime(10, 5));

        certify(userA, endured())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roundDate").value("2026-12-01"));

        assertThat(roundRepository.findByRoom_IdAndRoundDate(room.getId(), DEC_1).orElseThrow().getStatus())
                .isEqualTo(RoundStatus.CERTIFYING);
    }

    /** 시나리오 없음: 전원 인증으로 투표가 시작된 뒤 들어온 멤버 (사람 확인, 2026-11-13) */
    @Test
    void 투표가_시작된_회차에는_인증할_수_없다() throws Exception {
        Round dec1 = roundRepository.save(Round.create(room, DEC_1));
        ReflectionTestUtils.setField(dec1, "status", RoundStatus.VOTING);
        roundRepository.save(dec1);

        certify(userA, endured())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CERT_006"));
    }

    /** 시나리오 없음: 같은 사람이 동시에 두 번 등록해도 인증은 하나다 (처리 규칙 2번) */
    @Test
    void 같은_사람이_동시에_등록해도_인증은_하나다() {
        int requests = 5;
        CountDownLatch ready = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        try {
            List<CompletableFuture<Integer>> results = IntStream.range(0, requests)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                        await(ready);
                        try {
                            return certify(userA, endured()).andReturn().getResponse().getStatus();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }, executor))
                    .toList();
            ready.countDown();

            List<Integer> statuses = results.stream().map(CompletableFuture::join).toList();
            assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
            assertThat(statuses).filteredOn(code -> code == 409).hasSize(requests - 1);
        } finally {
            executor.shutdown();
        }
        assertThat(certificationRepository.count()).isEqualTo(1);
        assertThat(roundRepository.count()).isEqualTo(1);
    }

    private ResultActions certify(User user, String body) throws Exception {
        return mockMvc.perform(post("/api/rooms/{roomId}/certifications", room.getId())
                .header("Authorization", "Bearer " + jwtProvider.createAccessToken(user.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String endured() {
        return """
                {"result": "ENDURED", "memo": "집밥 해먹었어요"}
                """;
    }

    private Certification onlyCertification() {
        List<Certification> all = certificationRepository.findAll();
        assertThat(all).hasSize(1);
        return all.getFirst();
    }

    /** 투표 시작·마감은 VOTE-01·VOTE-04에서 만들어서, 지금은 끝난 상태를 바로 넣는다. */
    private void saveClosedRound(LocalDate roundDate, RoundStatus status, LocalDateTime voteClosedAt) {
        Round round = Round.create(room, roundDate);
        ReflectionTestUtils.setField(round, "status", status);
        ReflectionTestUtils.setField(round, "voteClosedAt", voteClosedAt);
        roundRepository.save(round);
    }

    private User newUser() {
        return userRepository.save(User.create("anon-cert-" + userSequence++));
    }

    private RoomMember addMember(User user, String nickname, BosalColor color) {
        return roomMemberRepository.save(RoomMember.create(room, user, nickname, color, DEC_1.minusDays(10).atStartOfDay()));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
