---
name: spec-plan
description: 기능 ID의 명세를 읽고 코드 작성 전에 docs/plans 작업 계획서를 작성합니다.
argument-hint: <기능 ID>
disable-model-invocation: true
---

대상 기능: $ARGUMENTS

코드는 쓰지 않습니다. 기능 ID가 없거나 명세에서 찾을 수 없으면 멈추고 사람에게 묻습니다.

1. `CLAUDE.md`, `docs/CONVENTION.md`, `docs/spec/README.md`, `docs/spec/common.md`를 읽습니다.
2. `docs/spec/`에서 대상 기능, 관련 기능, `docs/spec/open-questions.md`의 관련 질문을 읽습니다.
3. 명세를 점검합니다: 상태 전이, 동시성, 권한, 외부 연동 실패, 제외 범위.
   빈 곳은 임의로 채우지 않고 계획서의 열린 질문에 올립니다. `open-questions.md`에 없는 질문은 다음 번호로 추가합니다.
4. `docs/plans/TEMPLATE.md` 양식으로 `docs/plans/<기능 ID>.md`를 작성합니다.
   - 시나리오는 번호만 적고 Given/When/Then을 복사하지 않습니다.
   - 범위는 기능 ID 1~3개입니다. 할 일이 10개를 넘으면 쪼개자고 제안합니다.
5. 계획서 요약과 열린 질문을 보고하고, 사람의 확인을 기다립니다.
