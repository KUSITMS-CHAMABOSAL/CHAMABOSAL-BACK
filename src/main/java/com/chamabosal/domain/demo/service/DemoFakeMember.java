package com.chamabosal.domain.demo.service;

import com.chamabosal.domain.certification.entity.CertificationResult;
import com.chamabosal.domain.room.entity.BosalColor;
import java.util.Arrays;
import java.util.Optional;

/** 시연 번호마다 만드는 가짜 팀원 3명과 시연 방 1의 인증 내용 (docs/spec/demo.md 공통 표). 닉네임은 방마다 다르다. */
public enum DemoFakeMember {
    A("민서", "예나", BosalColor.PINK, CertificationResult.ENDURED, "편의점 앞에서 고민하다가 그냥 집에 왔어요", null, 3),
    B("만재", "예은", BosalColor.PURPLE, CertificationResult.FAILED, "추워서 퇴근길에 붕어빵이랑 어묵 사 먹었어요", 6_000, 3),
    C("용선", "윤지", BosalColor.MINT, CertificationResult.FAILED, "세일 문자 보고 결국 못 참고 질러버렸어요", 49_210, 4);

    private final String mainNickname;
    private final String moktakNickname;
    private final BosalColor color;
    private final CertificationResult result;
    private final String memo;
    private final Integer spentAmount;
    /** 시연 방 2에서 시연 계정에게 보낸 톡톡 수. 합계 10회 (DEMO-01 11번). */
    private final int tapCount;

    DemoFakeMember(String mainNickname, String moktakNickname, BosalColor color, CertificationResult result, String memo, Integer spentAmount,
                   int tapCount) {
        this.mainNickname = mainNickname;
        this.moktakNickname = moktakNickname;
        this.color = color;
        this.result = result;
        this.memo = memo;
        this.spentAmount = spentAmount;
        this.tapCount = tapCount;
    }

    public String tossAnonKey(int slotNumber) {
        return DemoSettings.FAKE_KEY_PREFIX + slotNumber + "-" + name();
    }

    /** 가짜 팀원의 토스 식별키(`demo-{번호}-{A|B|C}`)에서 누구인지 읽는다. 가짜 팀원이 아니면 비어 있다. */
    public static Optional<DemoFakeMember> fromTossAnonKey(String tossAnonKey) {
        if (tossAnonKey == null || !tossAnonKey.startsWith(DemoSettings.FAKE_KEY_PREFIX)) {
            return Optional.empty();
        }
        String role = tossAnonKey.substring(tossAnonKey.lastIndexOf('-') + 1);
        return Arrays.stream(values()).filter(member -> member.name().equals(role)).findFirst();
    }

    public String mainNickname() {
        return mainNickname;
    }

    public String moktakNickname() {
        return moktakNickname;
    }

    public BosalColor color() {
        return color;
    }

    public CertificationResult result() {
        return result;
    }

    public String memo() {
        return memo;
    }

    public Integer spentAmount() {
        return spentAmount;
    }

    public int tapCount() {
        return tapCount;
    }
}
