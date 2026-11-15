package com.chamabosal.domain.poke.service;

import com.chamabosal.domain.poke.dto.request.PokeSendRequest;
import com.chamabosal.domain.poke.entity.Poke;
import com.chamabosal.domain.poke.repository.PokeRepository;
import com.chamabosal.domain.room.entity.Room;
import com.chamabosal.domain.room.entity.RoomMember;
import com.chamabosal.domain.room.repository.RoomMemberRepository;
import com.chamabosal.domain.room.repository.RoomRepository;
import com.chamabosal.domain.round.entity.Round;
import com.chamabosal.domain.round.repository.RoundRepository;
import com.chamabosal.global.common.exception.BaseErrorCode;
import com.chamabosal.global.common.exception.BaseException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 콕 찌르기 (POKE-01). 인증 등록과 같은 방 행 락 안에서 오늘 회차 행을 만들고 횟수를 더해, 동시에 찔러도 한 행에 쌓인다.
 * 첫 조회가 락 조회라 락 뒤의 조회도 커밋된 값을 본다.
 */
@Service
@RequiredArgsConstructor
public class PokeService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoundRepository roundRepository;
    private final PokeRepository pokeRepository;
    private final Clock clock;

    /** 확인 순서: 없는 방 → 보내는 사람이 방 멤버 아님 → 본인 → 받는 사람이 이 방 현재 멤버 아님 (POKE-01 10번). */
    @Transactional
    public void send(Long userId, Long roomId, PokeSendRequest request) {
        // 회차는 요청을 받은 시각으로 정한다. 락을 기다리다 03:00을 넘겨도 회차가 바뀌지 않게 락보다 먼저 읽는다.
        LocalDateTime now = LocalDateTime.now(clock);
        Room room = roomRepository.findWithLockById(roomId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BaseException(BaseErrorCode.ROOM_NOT_FOUND));
        RoomMember me = roomMemberRepository.findByRoom_IdAndUser_IdAndLeftAtIsNull(roomId, userId)
                .orElseThrow(() -> new BaseException(BaseErrorCode.NOT_ROOM_MEMBER));
        if (me.getId().equals(request.toMemberId())) {
            throw new BaseException(BaseErrorCode.POKE_SELF);
        }
        RoomMember to = roomMemberRepository.findById(request.toMemberId())
                .filter(found -> found.getRoom().getId().equals(roomId) && found.isCurrent())
                .orElseThrow(() -> new BaseException(BaseErrorCode.MEMBER_NOT_FOUND));

        // 회차 상태와 상관없이 오늘 회차에 쌓고, 행이 없으면 만든다 (POKE-01 3·4번).
        LocalDate roundDate = Round.dateOf(now);
        Round round = roundRepository.findByRoom_IdAndRoundDate(roomId, roundDate)
                .orElseGet(() -> roundRepository.save(Round.create(room, roundDate)));
        pokeRepository.findByRound_IdAndFromMember_IdAndToMember_Id(round.getId(), me.getId(), to.getId()).ifPresentOrElse(
                poke -> poke.add(request.count()),
                () -> pokeRepository.save(Poke.create(round, me, to, request.count())));
    }
}
