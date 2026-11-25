package com.chamabosal.domain.room.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.nabal.dto.request.NabalReplantRequest;
import com.chamabosal.domain.nabal.entity.NabalLoss;
import com.chamabosal.domain.nabal.repository.NabalLossRepository;
import com.chamabosal.domain.nabal.service.NabalLossScheduler;
import com.chamabosal.domain.nabal.service.NabalService;
import com.chamabosal.domain.rice.entity.RiceHistory;
import com.chamabosal.domain.rice.entity.RiceReason;
import com.chamabosal.domain.rice.entity.RiceRefType;
import com.chamabosal.domain.rice.service.RiceService;
import com.chamabosal.domain.room.entity.LeaveType;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.global.common.exception.BaseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 나발 (NABAL-01~03). 방 준비 코드를 쓰려고 방 테스트 패키지에 둔다.
 * 공통 Given: 11/10 회차를 11/11 03:00에 판정한다. A는 11/1부터 방에 있었다.
 */
class NabalTest extends RoomTestSupport {

    private static final LocalDate ROUND_DATE = LocalDate.of(2026, 11, 10);
    private static final LocalDateTime JUDGED_AT = LocalDateTime.of(2026, 11, 11, 3, 0);
    private static final LocalDateTime LONG_AGO = LocalDateTime.of(2026, 11, 1, 12, 0);

    @Autowired
    NabalLossScheduler nabalLossScheduler;

    @Autowired
    NabalLossRepository nabalLossRepository;

    @Autowired
    NabalService nabalService;

    @Autowired
    RiceService riceService;

    private User userA;
    private Room room1;
    private RoomMember memberA1;

    @BeforeEach
    void setUp() {
        userA = newUser();
        room1 = createRoom(4);
        memberA1 = joinLongAgo(room1, userA);
    }

    @AfterEach
    void cleanUp() {
        nabalLossRepository.deleteAll();
    }

    @Test
    void NABAL_01_1_인증하지_않았으면_나발이_1개_빠지고_방과_회차_기록이_남는다() {
        judge();

        assertThat(nabalLossRepository.findAll()).singleElement().satisfies(loss -> {
            assertThat(loss.getRoom().getId()).isEqualTo(room1.getId());
            assertThat(loss.getRoundDate()).isEqualTo(ROUND_DATE);
            assertThat(loss.getNabalNumber()).isBetween(0, 37);
        });
    }

    @Test
    void NABAL_01_2_인증했으면_빠지지_않는다() {
        certify(memberA1);

        judge();

        assertThat(nabalLossRepository.count()).isZero();
    }

    @Test
    void NABAL_01_3_한_방만_빠뜨리면_1개_빠진다() {
        joinLongAgo(createRoom(4), userA);
        certify(memberA1);

        judge();

        assertThat(nabalLossRepository.count()).isEqualTo(1);
    }

    @Test
    void NABAL_01_4_두_방_다_빠뜨리면_서로_다른_나발이_2개_빠진다() {
        joinLongAgo(createRoom(4), userA);

        judge();

        assertThat(nabalLossRepository.findAll())
                .hasSize(2)
                .extracting(NabalLoss::getNabalNumber)
                .doesNotHaveDuplicates();
    }

    @Test
    void NABAL_01_5_그날_들어온_방은_세지_않는다() {
        certify(memberA1);
        roomMemberRepository.save(RoomMember.create(createRoom(4), userA, "민지", unusedColor(room1),
                ROUND_DATE.atTime(22, 0)));

        judge();

        assertThat(nabalLossRepository.count()).isZero();
    }

    @Test
    void NABAL_01_6_인증을_삭제했으면_빠진다() {
        Certification certification = certify(memberA1);
        certification.delete(ROUND_DATE.atTime(20, 5));
        certificationRepository.save(certification);

        judge();

        assertThat(nabalLossRepository.count()).isEqualTo(1);
    }

    @Test
    void NABAL_01_7_판정이_다시_돌아도_한_번만_빠진다() {
        judge();
        judge();

        assertThat(nabalLossRepository.count()).isEqualTo(1);
    }

    @Test
    void NABAL_01_8_모두_빠졌으면_더_빠지지_않는다() {
        IntStream.range(0, 38).forEach(number -> nabalLossRepository.save(
                NabalLoss.create(userA, room1, ROUND_DATE.minusDays(number + 1), number)));

        judge();

        assertThat(nabalLossRepository.count()).isEqualTo(38);
    }

    @Test
    void NABAL_01_9_참여_방이_없으면_판정하지_않는다() {
        leave(memberA1.getId(), ROUND_DATE.atTime(12, 0), LeaveType.LEFT);

        judge();

        assertThat(nabalLossRepository.count()).isZero();
    }

    @Test
    void NABAL_01_10_혼자인_방은_세지_않는다() {
        leave(room1.getOwnerMemberId(), ROUND_DATE.atTime(12, 0), LeaveType.LEFT);

        judge();

        assertThat(nabalLossRepository.count()).isZero();
    }

    @Test
    void NABAL_02_1_안_본_빠진_나발은_빠진_나발과_안_본_나발에_모두_있다() throws Exception {
        NabalLoss loss = fallen(7, ROUND_DATE);

        me()
                .andExpect(jsonPath("$.data.nabal.fallenNumbers[0]").value(7))
                .andExpect(jsonPath("$.data.nabal.unseenFallenNumbers[0]").value(7))
                .andExpect(jsonPath("$.data.nabal.lastNabalLossId").value(loss.getId()));
    }

    @Test
    void NABAL_02_2_확인을_저장하면_안_본_나발은_비고_빠진_나발은_그대로다() throws Exception {
        NabalLoss loss = fallen(7, ROUND_DATE);

        seen(loss.getId()).andExpect(status().isOk());

        me()
                .andExpect(jsonPath("$.data.nabal.fallenNumbers[0]").value(7))
                .andExpect(jsonPath("$.data.nabal.unseenFallenNumbers.length()").value(0));
    }

    @Test
    void NABAL_02_3_처음_빠졌으면_처음이라고_돌려준다() throws Exception {
        fallen(7, ROUND_DATE);

        me().andExpect(jsonPath("$.data.nabal.isFirstLoss").value(true));
    }

    @Test
    void NABAL_02_4_전에_확인한_적이_있으면_처음이_아니다() throws Exception {
        NabalLoss first = fallen(3, ROUND_DATE.minusDays(1));
        seen(first.getId()).andExpect(status().isOk());
        fallen(7, ROUND_DATE);

        me().andExpect(jsonPath("$.data.nabal.isFirstLoss").value(false));
    }

    @Test
    void NABAL_02_5_화면을_그린_뒤_빠진_나발은_안_본_나발로_남는다() throws Exception {
        NabalLoss first = fallen(3, ROUND_DATE.minusDays(1));
        fallen(7, ROUND_DATE);

        seen(first.getId()).andExpect(status().isOk());

        me().andExpect(jsonPath("$.data.nabal.unseenFallenNumbers.length()").value(1))
                .andExpect(jsonPath("$.data.nabal.unseenFallenNumbers[0]").value(7));
    }

    @Test
    void 연출을_보기_전에_심은_적이_있으면_다음에_빠져도_처음이_아니다() throws Exception {
        NabalLoss replanted = fallen(3, ROUND_DATE.minusDays(1));
        replanted.replant(ROUND_DATE.atTime(9, 0));
        nabalLossRepository.save(replanted);
        fallen(7, ROUND_DATE);

        me().andExpect(jsonPath("$.data.nabal.isFirstLoss").value(false));
    }

    @Test
    void 빠진_나발이_없으면_보살이고_처음이_아니다() throws Exception {
        me()
                .andExpect(jsonPath("$.data.nabal.fallenNumbers.length()").value(0))
                .andExpect(jsonPath("$.data.nabal.lastNabalLossId").doesNotExist())
                .andExpect(jsonPath("$.data.nabal.isFirstLoss").value(false));
    }

    @Test
    void NABAL_03_1_나발을_심으면_돌아오고_15석을_쓴다() throws Exception {
        fallen(7, ROUND_DATE);
        balance(20);

        replant(List.of(7))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.riceBalance").value(5))
                .andExpect(jsonPath("$.data.fallenNumbers.length()").value(0));
    }

    @Test
    void NABAL_03_2_여러_개를_한_번에_심으면_개수만큼_쓴다() throws Exception {
        fallen(3, ROUND_DATE.minusDays(1));
        fallen(7, ROUND_DATE);
        balance(40);

        replant(List.of(3, 7))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.riceBalance").value(10));

        assertThat(nabalLossRepository.findByUser_IdAndReplantedAtIsNullOrderByNabalNumber(userA.getId())).isEmpty();
    }

    @Test
    void NABAL_03_3_빠진_나발을_모두_심으면_보살로_돌아온다() throws Exception {
        fallen(3, ROUND_DATE.minusDays(1));
        fallen(7, ROUND_DATE);
        balance(30);

        replant(List.of(3)).andExpect(jsonPath("$.data.fallenNumbers[0]").value(7));
        replant(List.of(7)).andExpect(jsonPath("$.data.fallenNumbers.length()").value(0));

        me().andExpect(jsonPath("$.data.nabal.fallenNumbers.length()").value(0));
    }

    @Test
    void NABAL_03_4_빠지지_않은_나발이_섞이면_NABAL_001이고_아무것도_심지_않는다() throws Exception {
        fallen(7, ROUND_DATE);
        balance(40);

        replant(List.of(3, 7))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NABAL_001"));

        assertThat(nabalLossRepository.findByUser_IdAndReplantedAtIsNullOrderByNabalNumber(userA.getId())).hasSize(1);
        assertThat(riceService.getDisplayBalance(userA.getId())).isEqualTo(40);
    }

    @Test
    void NABAL_03_5_공양미가_부족하면_STORE_001이고_빠진_채다() throws Exception {
        fallen(7, ROUND_DATE);
        balance(10);

        replant(List.of(7))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STORE_001"));

        assertThat(nabalLossRepository.findByUser_IdAndReplantedAtIsNullOrderByNabalNumber(userA.getId())).hasSize(1);
    }

    @Test
    void NABAL_03_6_같은_나발을_동시에_심어도_한_번만_쓴다() {
        fallen(7, ROUND_DATE);
        balance(30);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            List<CompletableFuture<Boolean>> futures = IntStream.range(0, 2)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                        ready.countDown();
                        await(start);
                        try {
                            nabalService.replant(userA.getId(), new NabalReplantRequest(List.of(7)));
                            return true;
                        } catch (BaseException e) {
                            return false;
                        }
                    }, executor))
                    .toList();
            await(ready);
            start.countDown();

            assertThat(futures.stream().map(CompletableFuture::join).toList()).containsExactlyInAnyOrder(true, false);
            assertThat(riceService.getDisplayBalance(userA.getId())).isEqualTo(15);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void 빈_목록_범위_밖_중복_번호는_COMMON_002다() throws Exception {
        fallen(7, ROUND_DATE);
        balance(100);

        for (String body : new String[]{"[]", "[38]", "[-1]", "[7, 7]"}) {
            mockMvc.perform(post("/api/users/me/nabal-replants")
                            .header("Authorization", bearer(userA))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nabalNumbers\":%s}".formatted(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_002"));
        }
        assertThat(riceService.getDisplayBalance(userA.getId())).isEqualTo(100);
    }

    private RoomMember joinLongAgo(Room room, User user) {
        return roomMemberRepository.save(RoomMember.create(room, user, "민지", unusedColor(room), LONG_AGO));
    }

    private Certification certify(RoomMember member) {
        Round round = roundRepository.findByRoom_IdAndRoundDate(member.getRoom().getId(), ROUND_DATE)
                .orElseGet(() -> roundRepository.save(Round.create(member.getRoom(), ROUND_DATE)));
        return certificationRepository.save(Certification.create(
                round, member, CertificationResult.ENDURED, "집밥 해먹었어요", null, ROUND_DATE.atTime(20, 0)));
    }

    private void judge() {
        clock.set(JUDGED_AT);
        nabalLossScheduler.judgeClosedRound();
    }

    private NabalLoss fallen(int number, LocalDate roundDate) {
        return nabalLossRepository.save(NabalLoss.create(userA, room1, roundDate, number));
    }

    private void balance(int amount) {
        riceHistoryRepository.save(RiceHistory.create(
                userA, null, amount, RiceReason.CERTIFICATION, RiceRefType.CERTIFICATION, 999L));
    }

    private ResultActions me() throws Exception {
        return mockMvc.perform(get("/api/users/me").header("Authorization", bearer(userA))).andExpect(status().isOk());
    }

    private ResultActions seen(Long nabalLossId) throws Exception {
        return mockMvc.perform(put("/api/users/me/seen-nabal-loss")
                .header("Authorization", bearer(userA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nabalLossId\":%d}".formatted(nabalLossId)));
    }

    private ResultActions replant(List<Integer> numbers) throws Exception {
        return mockMvc.perform(post("/api/users/me/nabal-replants")
                .header("Authorization", bearer(userA))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nabalNumbers\":%s}".formatted(numbers)));
    }
}
