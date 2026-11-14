package com.chamabosal.domain.certification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.rice.entity.RiceHistory;
import com.chamabosal.domain.rice.entity.RiceReason;
import com.chamabosal.domain.rice.entity.RiceRefType;
import com.chamabosal.domain.rice.repository.RiceHistoryRepository;
import com.chamabosal.domain.rice.service.RiceService;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.entity.RoundStatus;
import com.chamabosal.domain.round.repository.RoundParticipantRepository;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.domain.vote.service.VoteStartService;
import com.chamabosal.global.security.jwt.JwtProvider;
import com.chamabosal.support.MutableClock;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 인증 수정·삭제(CERT-02), 잠금(CERT-03), 회수(RICE-02). 공통 Given: 방 인원은 A, B, C, D 4명이다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
class CertificationEditTest {

    private static final LocalDate DEC_1 = LocalDate.of(2026, 12, 1);
    private static final LocalDateTime T0 = DEC_1.atTime(20, 0);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MutableClock clock;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    VoteStartService voteStartService;

    @Autowired
    RiceService riceService;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    RiceHistoryRepository riceHistoryRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoomRepository roomRepository;

    @Autowired
    RoomMemberRepository roomMemberRepository;

    @Autowired
    RoundRepository roundRepository;

    @Autowired
    RoundParticipantRepository roundParticipantRepository;

    @Autowired
    CertificationRepository certificationRepository;

    private int userSequence;
    private Room room;
    private User userA;
    private User userB;
    private User userC;
    private User userD;

    @BeforeEach
    void setUp() {
        clock.set(T0);
        room = newRoom();
        userA = newUser();
        userB = newUser();
        userC = newUser();
        userD = newUser();
        addMember(room, userA, "민지", BosalColor.PINK);
        addMember(room, userB, "지수", BosalColor.PURPLE);
        addMember(room, userC, "하린", BosalColor.MINT);
        addMember(room, userD, "도윤", BosalColor.YELLOW);
    }

    @AfterEach
    void tearDown() {
        riceHistoryRepository.deleteAll();
        roundParticipantRepository.deleteAll();
        certificationRepository.deleteAll();
        roundRepository.deleteAll();
        roomMemberRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void CERT_02_1_10분_안에_수정하면_공양미를_다시_주지_않는다() throws Exception {
        Long id = certify(userA, room, endured("집밥 해먹었어요"));

        clock.set(T0.plusMinutes(5));
        update(userA, id, endured("집밥에 김치찌개"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memo").value("집밥에 김치찌개"))
                .andExpect(jsonPath("$.data.editableUntil").value("2026-12-01T20:10:00"));

        assertThat(certificationRepository.findById(id).orElseThrow().getMemo()).isEqualTo("집밥에 김치찌개");
        assertThat(histories(userA)).extracting(RiceHistory::getDelta).containsExactly(10);
    }

    @Test
    void CERT_02_2_10분이_지나면_수정_시간_지남으로_거부한다() throws Exception {
        Long id = certify(userA, room, endured("집밥 해먹었어요"));

        clock.set(T0.plusMinutes(11));
        update(userA, id, endured("고쳐요"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CERT_004"));
    }

    @Test
    void CERT_02_3_투표가_시작되면_투표_시작으로_거부한다() throws Exception {
        Long id = certify(userA, room, endured("집밥 해먹었어요"));
        certifyOthersUntilVoteStarts();

        clock.set(T0.plusMinutes(3));
        update(userA, id, endured("고쳐요"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CERT_005"));
    }

    @Test
    void CERT_02_4_본인_인증이_아니면_수정도_삭제도_거부한다() throws Exception {
        Long id = certify(userA, room, endured("집밥 해먹었어요"));

        clock.set(T0.plusMinutes(3));
        update(userB, id, endured("남의 인증"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CERT_003"));
        remove(userB, id)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CERT_003"));
    }

    @Test
    void CERT_02_5_삭제하면_받은_공양미를_회수한다() throws Exception {
        Long id = certify(userA, room, endured("집밥 해먹었어요"));

        remove(userA, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.riceRecalled").value(10));

        assertThat(certificationRepository.findById(id).orElseThrow().isDeleted()).isTrue();
        assertThat(histories(userA)).extracting(RiceHistory::getReason, RiceHistory::getDelta)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(RiceReason.CERTIFICATION, 10),
                        org.assertj.core.groups.Tuple.tuple(RiceReason.RECALL, -10));
    }

    @Test
    void CERT_02_6_삭제한_뒤_같은_회차에_다시_인증하면_보상을_다시_준다() throws Exception {
        Long id = certify(userA, room, endured("집밥 해먹었어요"));
        remove(userA, id).andExpect(status().isOk());

        clock.set(T0.plusMinutes(2));
        post(userA, room, endured("다시 인증"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roundDate").value("2026-12-01"))
                .andExpect(jsonPath("$.data.riceEarned").value(10));
    }

    @Test
    void CERT_02_7_같은_삭제_요청이_반복돼도_회수는_한_번이다() throws Exception {
        Long id = certify(userA, room, endured("집밥 해먹었어요"));
        remove(userA, id).andExpect(status().isOk());

        remove(userA, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.riceRecalled").value(0));

        assertThat(histories(userA)).filteredOn(h -> h.getReason() == RiceReason.RECALL).hasSize(1);
    }

    /**
     * 수정과 마지막 인증(투표 시작)이 같은 방 행 락을 기다린다. 수정이 먼저면 바뀐 내용으로 투표가 시작되고,
     * 투표 시작이 먼저면 수정이 거부된다. 어느 쪽이든 투표 시작 뒤에 인증이 바뀌지 않는다.
     */
    @Test
    void CERT_02_8_수정과_투표_시작이_동시에_실행돼도_투표_뒤에는_바뀌지_않는다() throws Exception {
        Long id = certify(userA, room, endured("집밥 해먹었어요"));
        clock.set(T0.plusMinutes(1));
        certify(userB, room, endured("B"));
        certify(userC, room, endured("C"));

        clock.set(T0.plusMinutes(3));
        CountDownLatch ready = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<MvcResult> edit = CompletableFuture.supplyAsync(() -> {
                await(ready);
                return call(() -> update(userA, id, endured("동시에 고침")).andReturn());
            }, executor);
            CompletableFuture<MvcResult> last = CompletableFuture.supplyAsync(() -> {
                await(ready);
                return call(() -> post(userD, room, endured("D")).andReturn());
            }, executor);
            ready.countDown();

            MvcResult editResult = edit.join();
            assertThat(last.join().getResponse().getStatus()).isEqualTo(201);
            int editStatus = editResult.getResponse().getStatus();
            assertThat(editStatus).isIn(200, 409);
            String memo = certificationRepository.findById(id).orElseThrow().getMemo();
            if (editStatus == 200) {
                assertThat(memo).isEqualTo("동시에 고침");
            } else {
                assertThat(editResult.getResponse().getContentAsString()).contains("CERT_005");
                assertThat(memo).isEqualTo("집밥 해먹었어요");
            }
        } finally {
            executor.shutdown();
        }
        assertThat(onlyRound(room).getStatus()).isEqualTo(RoundStatus.VOTING);
    }

    @Test
    void CERT_02_9_참았어요로_바꾸면_저장된_금액을_지운다() throws Exception {
        Long id = certify(userA, room, """
                {"result": "FAILED", "memo": "치킨 시켰어요", "spentAmount": 23000}
                """);

        clock.set(T0.plusMinutes(3));
        update(userA, id, """
                {"result": "ENDURED", "memo": "치킨 참았어요", "spentAmount": 23000}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("ENDURED"))
                .andExpect(jsonPath("$.data.spentAmount").doesNotExist());

        Certification saved = certificationRepository.findById(id).orElseThrow();
        assertThat(saved.getResult()).isEqualTo(CertificationResult.ENDURED);
        assertThat(saved.getSpentAmount()).isNull();
    }

    @Test
    void CERT_02_13_10분도_지나고_투표도_시작됐으면_수정_시간_지남이다() throws Exception {
        Long id = certify(userA, room, endured("집밥 해먹었어요"));
        certifyOthersUntilVoteStarts();

        clock.set(T0.plusMinutes(11));
        update(userA, id, endured("고쳐요"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CERT_004"));
    }

    @Test
    void CERT_02_14_잠긴_뒤_삭제_재시도도_성공하고_회수는_한_번이다() throws Exception {
        Long id = certify(userA, room, endured("집밥 해먹었어요"));
        remove(userA, id).andExpect(status().isOk());

        clock.set(T0.plusMinutes(11));
        remove(userA, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.riceRecalled").value(0));

        assertThat(histories(userA)).filteredOn(h -> h.getReason() == RiceReason.RECALL).hasSize(1);
    }

    @Test
    void CERT_03_1_전원_인증으로_투표가_시작되면_먼저_한_인증도_잠긴다() throws Exception {
        Long id = certify(userA, room, endured("집밥 해먹었어요"));
        certifyOthersUntilVoteStarts();

        clock.set(T0.plusMinutes(3));
        remove(userA, id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CERT_005"));
    }

    @Test
    void CERT_03_2_마지막_인증자는_등록하자마자_잠긴다() throws Exception {
        certify(userA, room, endured("A"));
        certify(userB, room, endured("B"));
        certify(userC, room, endured("C"));
        Long last = certify(userD, room, endured("D"));

        update(userD, last, endured("바로 고침"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CERT_005"));
    }

    @Test
    void CERT_03_3_03시에_투표가_시작되면_10분이_안_지나도_잠긴다() throws Exception {
        clock.set(DEC_1.plusDays(1).atTime(2, 55));
        Long id = certify(userA, room, endured("늦게 인증"));

        Round round = onlyRound(room);
        voteStartService.startAtDeadline(room.getId(), round.getId(), DEC_1.plusDays(1).atTime(3, 0));
        assertThat(onlyRound(room).getStatus()).isEqualTo(RoundStatus.VOTING);

        clock.set(DEC_1.plusDays(1).atTime(3, 1));
        update(userA, id, endured("고쳐요"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CERT_005"));
    }

    @Test
    void RICE_02_1_인증을_삭제하면_개인_공양미를_회수한다() throws Exception {
        Long id = certify(userA, room, endured("집밥 해먹었어요"));

        remove(userA, id).andExpect(jsonPath("$.data.riceRecalled").value(10));

        RiceHistory recall = histories(userA).stream()
                .filter(h -> h.getReason() == RiceReason.RECALL).findFirst().orElseThrow();
        assertThat(recall.getDelta()).isEqualTo(-10);
        assertThat(recall.getRefType()).isEqualTo(RiceRefType.CERTIFICATION);
        assertThat(recall.getRefId()).isEqualTo(id);
        assertThat(riceHistoryRepository.sumDeltaByUserId(userA.getId())).isZero();
    }

    @Test
    void RICE_02_2_지급되지_않은_보상은_회수하지_않는다() throws Exception {
        Room soloRoom = newRoom();
        User solo = newUser();
        addMember(soloRoom, solo, "혼자", BosalColor.PINK);
        Long id = certify(solo, soloRoom, endured("혼자 참았어요"));

        remove(solo, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.riceRecalled").value(0));

        assertThat(histories(solo)).isEmpty();
    }

    @Test
    void RICE_02_5_회수하면_잔액이_음수가_될_수_있다() throws Exception {
        Long id = certify(userA, room, endured("집밥 해먹었어요"));
        riceHistoryRepository.save(RiceHistory.create(userA, null, -5, RiceReason.PURCHASE, RiceRefType.ITEM, 1L));
        assertThat(riceHistoryRepository.sumDeltaByUserId(userA.getId())).isEqualTo(5);

        remove(userA, id).andExpect(status().isOk());

        assertThat(riceHistoryRepository.sumDeltaByUserId(userA.getId())).isEqualTo(-5);
        assertThat(riceService.getDisplayBalance(userA.getId())).isZero();
    }

    @Test
    void RICE_02_6_회수가_다시_실행돼도_추가로_기록하지_않는다() throws Exception {
        Long id = certify(userA, room, endured("집밥 해먹었어요"));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        RoomMember memberA = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(room.getId(), userA.getId())
                .orElseThrow();

        int first = tx.execute(status -> riceService.recallCertification(memberWithUser(memberA), id));
        int second = tx.execute(status -> riceService.recallCertification(memberWithUser(memberA), id));

        assertThat(first).isEqualTo(10);
        assertThat(second).isZero();
        assertThat(histories(userA)).filteredOn(h -> h.getReason() == RiceReason.RECALL).hasSize(1);
    }

    @Test
    void CERT_01_16_투표_없음_회차에서_삭제한_멤버는_03시까지_다시_인증한다() throws Exception {
        Room pairRoom = newRoom();
        User first = newUser();
        User second = newUser();
        addMember(pairRoom, first, "하나", BosalColor.PINK);
        addMember(pairRoom, second, "둘", BosalColor.MINT);
        Long id = certify(first, pairRoom, endured("하나"));
        certify(second, pairRoom, endured("둘"));
        assertThat(onlyRound(pairRoom).getStatus()).isEqualTo(RoundStatus.NO_VOTE);

        clock.set(T0.plusMinutes(5));
        remove(first, id).andExpect(status().isOk());

        clock.set(T0.plusHours(1));
        post(first, pairRoom, endured("다시 하나"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roundDate").value("2026-12-01"))
                .andExpect(jsonPath("$.data.riceEarned").value(10));
        post(second, pairRoom, endured("또 둘"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CERT_001"));
    }

    /** A 뒤로 B, C, D가 1분 간격으로 인증해 D의 인증으로 투표가 시작된다. 시각은 원래대로 돌려 둔다. */
    private void certifyOthersUntilVoteStarts() throws Exception {
        clock.set(T0.plusMinutes(1));
        certify(userB, room, endured("B"));
        certify(userC, room, endured("C"));
        post(userD, room, endured("D")).andExpect(jsonPath("$.data.isVoteStarted").value(true));
        clock.set(T0);
    }

    private Long certify(User user, Room target, String body) throws Exception {
        String content = post(user, target, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(content, "$.data.certificationId")).longValue();
    }

    private ResultActions post(User user, Room target, String body) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/rooms/{roomId}/certifications", target.getId())
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions update(User user, Long certificationId, String body) throws Exception {
        return mockMvc.perform(patch("/api/certifications/{certificationId}", certificationId)
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions remove(User user, Long certificationId) throws Exception {
        return mockMvc.perform(delete("/api/certifications/{certificationId}", certificationId)
                .header("Authorization", bearer(user)));
    }

    private String bearer(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId());
    }

    private static String endured(String memo) {
        return """
                {"result": "ENDURED", "memo": "%s"}
                """.formatted(memo);
    }

    private List<RiceHistory> histories(User user) {
        return riceHistoryRepository.findAll().stream()
                .filter(h -> h.getUser().getId().equals(user.getId()))
                .toList();
    }

    private Round onlyRound(Room target) {
        return roundRepository.findAll().stream()
                .filter(r -> r.getRoom().getId().equals(target.getId()))
                .findFirst().orElseThrow();
    }

    /** 트랜잭션 밖에서 읽은 멤버라 회수 안에서 쓰는 사용자 ID를 미리 채워 둔다. */
    private RoomMember memberWithUser(RoomMember member) {
        return roomMemberRepository.findById(member.getId()).orElseThrow();
    }

    private Room newRoom() {
        return roomRepository.save(Room.create("배달 끊기 방", RoomGoal.DELIVERY, 4));
    }

    private User newUser() {
        return userRepository.save(User.create("anon-edit-" + userSequence++));
    }

    private void addMember(Room target, User user, String nickname, BosalColor color) {
        roomMemberRepository.save(RoomMember.create(target, user, nickname, color, DEC_1.minusDays(10).atStartOfDay()));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static <T> T call(ThrowingSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
