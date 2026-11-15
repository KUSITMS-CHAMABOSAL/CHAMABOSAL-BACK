package com.chamabosal.domain.certification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chamabosal.domain.certification.entity.Certification;
import com.chamabosal.domain.certification.photo.MemoryPhotoStorage;
import com.chamabosal.domain.certification.photo.PhotoStorage;
import com.chamabosal.domain.certification.repository.CertificationRepository;
import com.chamabosal.domain.pot.repository.PotContributionRepository;
import com.chamabosal.domain.pot.repository.RoomPotRepository;
import com.chamabosal.domain.rice.repository.RiceHistoryRepository;
import com.chamabosal.domain.room.entity.BosalColor;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomGoal;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.domain.user.entity.User;
import com.chamabosal.domain.user.repository.UserRepository;
import com.chamabosal.global.security.jwt.JwtProvider;
import com.chamabosal.support.MutableClock;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 인증 사진 (CERT-04, CERT-02 9·10번). 저장소는 메모리 구현이고, 업로드 URL로 올리는 단계는 upload로 대신한다.
 * 로컬에 AWS_S3_BUCKET이 있어도 메모리 저장소를 쓰도록 버킷 설정을 비운다.
 */
@SpringBootTest(properties = "storage.s3.bucket=")
@AutoConfigureMockMvc
@Import(MutableClock.Config.class)
class CertificationPhotoTest {

    private static final LocalDate DEC_1 = LocalDate.of(2026, 12, 1);
    private static final LocalDateTime T0 = DEC_1.atTime(20, 0);

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JwtProvider jwtProvider;
    @MockitoSpyBean PhotoStorage photoStorage;
    @Autowired RiceHistoryRepository riceHistoryRepository;
    @Autowired CertificationRepository certificationRepository;
    @Autowired RoundRepository roundRepository;
    @Autowired PotContributionRepository potContributionRepository;
    @Autowired RoomPotRepository roomPotRepository;
    @Autowired RoomMemberRepository roomMemberRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired UserRepository userRepository;

    private int userSequence;
    private Room room;
    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        clock.set(T0);
        room = roomRepository.save(Room.create("배달 끊기 방", RoomGoal.DELIVERY, 4, 300));
        userA = newUser();
        userB = newUser();
        addMember(userA, "민지");
        addMember(userB, "지수");
        addMember(newUser(), "하린");
    }

    @AfterEach
    void tearDown() {
        storage().clear();
        riceHistoryRepository.deleteAll();
        certificationRepository.deleteAll();
        roundRepository.deleteAll();
        potContributionRepository.deleteAll();
        roomPotRepository.deleteAll();
        roomMemberRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void CERT_04_4_인증을_삭제하면_사진도_바로_삭제한다() throws Exception {
        Long id = certifyWithPhoto(userA);
        String saved = imageKeyOf(id);
        assertThat(saved).startsWith("certifications/");
        assertThat(storage().contains(saved)).isTrue();

        mockMvc.perform(delete("/api/certifications/{certificationId}", id).header("Authorization", bearer(userA)))
                .andExpect(status().isOk());

        assertThat(storage().contains(saved)).isFalse();
        assertThat(certificationRepository.findById(id).orElseThrow().hasImage()).isFalse();
    }

    @Test
    void CERT_01_9_사진을_옮기는_사이_03시를_넘겨도_요청을_받은_시각의_회차로_등록한다() throws Exception {
        clock.set(LocalDate.of(2026, 12, 2).atTime(2, 59));
        String key = uploadPhoto(userA);
        doAnswer(invocation -> {
            clock.set(LocalDate.of(2026, 12, 2).atTime(3, 0, 1));
            return invocation.callRealMethod();
        }).when(photoStorage).copy(anyString(), anyString());

        certify(userA, key)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roundDate").value("2026-12-01"));
    }

    @Test
    void CERT_04_6_다른_사람의_사진_키로는_인증할_수_없다() throws Exception {
        String keyOfB = uploadPhoto(userB);

        certify(userA, keyOfB)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"))
                .andExpect(jsonPath("$.field").value("imageKey"));

        assertThat(certificationRepository.findAll()).isEmpty();
    }

    @Test
    void CERT_04_7_허용하지_않는_형식은_업로드_URL을_주지_않는다() throws Exception {
        uploadUrl(userA, "image/gif")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"))
                .andExpect(jsonPath("$.field").value("contentType"));
    }

    @Test
    void CERT_04_8_새_사진으로_바꾸면_기존_사진을_바로_삭제한다() throws Exception {
        Long id = certifyWithPhoto(userA);
        String before = imageKeyOf(id);
        clock.set(T0.plusMinutes(3));

        update(userA, id, "\"imageKey\": \"" + uploadPhoto(userA) + "\", \"isImageKept\": false")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl").value(org.hamcrest.Matchers.startsWith("memory://view/certifications/")));

        String after = imageKeyOf(id);
        assertThat(after).isNotEqualTo(before).startsWith("certifications/");
        assertThat(storage().contains(before)).isFalse();
        assertThat(storage().contains(after)).isTrue();
    }

    @Test
    void CERT_02_10_수정하며_사진을_지우면_기존_사진을_바로_삭제한다() throws Exception {
        Long id = certifyWithPhoto(userA);
        String before = imageKeyOf(id);
        clock.set(T0.plusMinutes(3));

        update(userA, id, "\"isImageKept\": false")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl").doesNotExist());

        assertThat(certificationRepository.findById(id).orElseThrow().hasImage()).isFalse();
        assertThat(storage().contains(before)).isFalse();
    }

    @Test
    void CERT_02_11_수정하며_사진을_유지한다() throws Exception {
        Long id = certifyWithPhoto(userA);
        String before = imageKeyOf(id);
        clock.set(T0.plusMinutes(3));

        update(userA, id, "\"isImageKept\": true")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl").value("memory://view/" + before));

        assertThat(imageKeyOf(id)).isEqualTo(before);
        assertThat(storage().contains(before)).isTrue();
    }

    @Test
    void CERT_02_12_사진_유지_여부가_없으면_COMMON_002이고_그대로다() throws Exception {
        Long id = certifyWithPhoto(userA);
        String before = imageKeyOf(id);
        clock.set(T0.plusMinutes(3));

        mockMvc.perform(patch("/api/certifications/{certificationId}", id)
                        .header("Authorization", bearer(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\": \"ENDURED\", \"memo\": \"고쳐요\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"))
                .andExpect(jsonPath("$.field").value("isImageKept"));

        Certification saved = certificationRepository.findById(id).orElseThrow();
        assertThat(saved.getMemo()).isEqualTo("집밥 해먹었어요");
        assertThat(saved.getImageKey()).isEqualTo(before);
        assertThat(storage().contains(before)).isTrue();
    }

    private Long certifyWithPhoto(User user) throws Exception {
        String content = certify(user, uploadPhoto(user)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(content, "$.data.certificationId")).longValue();
    }

    /** 업로드 URL을 받고 그 키에 사진을 올린 것으로 만든다. */
    private String uploadPhoto(User user) throws Exception {
        String content = uploadUrl(user, "image/jpeg").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.expiresIn").value(300))
                .andReturn().getResponse().getContentAsString();
        String key = JsonPath.read(content, "$.data.imageKey");
        assertThat(key).startsWith("tmp/" + user.getId() + "/");
        storage().upload(key);
        return key;
    }

    private ResultActions uploadUrl(User user, String contentType) throws Exception {
        return mockMvc.perform(post("/api/certifications/upload-urls")
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\": \"" + contentType + "\"}"));
    }

    private ResultActions certify(User user, String imageKey) throws Exception {
        return mockMvc.perform(post("/api/rooms/{roomId}/certifications", room.getId())
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\": \"ENDURED\", \"memo\": \"집밥 해먹었어요\", \"imageKey\": \"" + imageKey + "\"}"));
    }

    private ResultActions update(User user, Long id, String photoFields) throws Exception {
        return mockMvc.perform(patch("/api/certifications/{certificationId}", id)
                .header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\": \"ENDURED\", \"memo\": \"고쳐요\", " + photoFields + "}"));
    }

    private String imageKeyOf(Long id) {
        return certificationRepository.findById(id).orElseThrow().getImageKey();
    }

    private MemoryPhotoStorage storage() {
        return (MemoryPhotoStorage) photoStorage;
    }

    private String bearer(User user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId());
    }

    private User newUser() {
        return userRepository.save(User.create("anon-photo-" + userSequence++));
    }

    private RoomMember addMember(User user, String nickname) {
        return roomMemberRepository.save(RoomMember.create(room, user, nickname, BosalColor.PINK, DEC_1.minusDays(10).atStartOfDay()));
    }
}
