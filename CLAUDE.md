# SSU-TIME Backend

숭실대 LMS 과제/영상/퀴즈 마감 임박 알림 서비스의 백엔드. 클라이언트가 LMS를 크롤링해 정보를 보고하면 서버가 정해진 시각에 전화 UI(CallKit) 또는 FCM 푸시로 알림을 보낸다.

## 개발 방식

### 새로운 feature 개발 시
- 개발 해야 할 feature가 여러개인 경우 가능하면 claude-teams를 생성해 feature별로 병렬로 개발하십시오.
- 각 할당받은 feature마다 git branch를 새로 만들어서 작업하십시오.
- test코드도 항상 작성하고, 개발이 끝나면 test하십시오.
- 에이전트가 작업이 끝나면 해당 브랜치를 review하는 에이전트를 개발한 에이전트에 subagent로 생성하고 review하십시오.

## 기술 스택

- **Language**: Kotlin
- **Framework**: Spring Boot 3.x, Spring Data JPA, Spring Events
- **DB**: MySQL
- **External**: 숭실대 SSO, FCM, Anthropic API
- **Build**: Gradle (Kotlin DSL)

## 아키텍처 — 절대 깨면 안 되는 규칙

### 5-레이어 구조

External → API → Application → Domain → Infrastructure 의 단방향. 도메인 이벤트는 Application 레이어 안에서 발행/소비된다.

### 패키지 구조

```
com.ssutime
├── auth/              # User, UserDevice, AuthController, SSOClient
├── subject/           # Subject, Enrollment, EnrollmentController
├── todo/              # Todo, UserTodoStatus, TodoReport, ReconcileService
├── notification/      # NotificationService, FCMClient, @Scheduled
├── aisummary/         # AISummaryService, AnthropicClient
└── common/            # base entity, 공통 예외, 설정
```

각 feature 패키지 내부는 layer subpackage로:

```
com.ssutime.todo
├── domain/            # Todo, UserTodoStatus, TodoReport, *Event
├── application/       # TodoService, ReconcileService
├── infrastructure/    # *Repository, scheduler, AnthropicAdapter
└── presentation/      # TodoController, dto
```

### 의존성 방향 (역방향 import 금지)

- `todo → subject, auth`
- `subject → auth`
- `notification, aisummary → todo` (이벤트 컨슈머)
- `common ← 모두`

ArchUnit으로 강제해두는 것 권장.

## 도메인 모델

### auth

- **User**: PK는 별도(Long), `studentId: String`이 자연키. 졸업 후 학번 재사용/익명화 대비. `academicStatus`(재학/휴학/졸업) nullable.
- **UserDevice**: `(user_id, fcm_token)` unique. 한 사용자 다중 디바이스(iOS/Android 폰·태블릿). FCM은 반드시 디바이스 단위 발송.

### subject

- **Subject**: `course_id: Long`이 자연키. 학기·분반까지 unique하다고 가정(LMS의 course_id 그대로 사용).
- **Enrollment**: `(user_id, subject_id)` unique. 학기말 일괄 정리 배치 필요(SSO에서 학적상태 못 받을 경우).

### todo

- **Todo (canonical)**: `(subject_id, material_code)` 자연키. 같은 분반 학생들이 공유 → AI 요약 1회만 계산. 필드: `type: TodoType (ASSIGNMENT|VIDEO|QUIZ)`, `dueDate`, `title`, `aiSummary?`, `status: TodoStatus (PROVISIONAL|CONFIRMED)`.
- **UserTodoStatus**: `(user_id, todo_id)` unique. 사용자별 상태. `isCompleted`, `completedAt`, `thresholdMinutes`(사용자가 설정한 알림 임계), `notifyAt`(계산된 알림 시각), `notificationSent: Boolean`.
- **TodoReport (raw log)**: 사용자가 보고한 원천 데이터. quorum 다수결과 감사 추적용. `(user_id, subject_id, material_code, due_date, title, reported_at)`.

## 핵심 흐름

### 1. Todo 보고 ingestion

`TodoController.report()` → `TodoService.processReport()`는 단일 트랜잭션 안에서:

1. `TodoReport` 저장 (raw log, 항상 추가)
2. `Todo` upsert (`(subject_id, material_code)` 자연키, ON CONFLICT). 없으면 PROVISIONAL로 생성.
3. `UserTodoStatus` upsert. `notifyAt = dueDate - thresholdMinutes`로 계산.
4. `TodoReported` 이벤트 발행.

본인 알림은 항상 즉시 등록되어 막히지 않는다(quorum과 무관).

### 2. 교차검증 (Reconcile)

`ReconcileService`가 `@TransactionalEventListener(AFTER_COMMIT)` 로 `TodoReported` 소비:

1. 같은 `(subject_id, material_code)`의 TodoReport를 최근 N시간 윈도우로 조회.
2. `dueDate`, `title` 다수결.
3. quorum 충족(예: 동일 보고자 2명 이상)이면 Todo.status = CONFIRMED.
4. 다수결 결과로 `dueDate`가 바뀌었으면 영향 받은 모든 UserTodoStatus.notifyAt 재계산.
5. status가 PROVISIONAL → CONFIRMED 전이 시 `TodoConfirmed` 이벤트 발행.

### 3. AI 요약

`AISummaryService.@EventListener(TodoConfirmed)` — `type == ASSIGNMENT` 만.

- AnthropicClient 호출 → `Todo.aiSummary` 갱신.
- 트랜잭션 밖에서 호출, 실패 시 재시도(최대 N회), 그래도 실패하면 sentry/log.

### 4. 알림 발사 (1분 폴링)

`@Scheduled(fixedRate = 60_000)` (notification 패키지):

```kotlin
SELECT * FROM user_todo_status
WHERE notify_at <= NOW()
  AND notification_sent = false
  AND is_completed = false
```

각 row에 대해 `DeadlineApproaching` 이벤트 발행 → `NotificationService` → `FCMClient` (디바이스 단위) → `notificationSent = true`.

`(notify_at, notification_sent)` 인덱스 필수.

### 5. LMS 크롤링 트리거 (15분 분산)

`@Scheduled(fixedRate = 60_000)` 매분 실행하지만 hash bucket으로 분산:

```kotlin
val bucket = LocalDateTime.now().minute % 15
SELECT * FROM users
WHERE id % 15 = :bucket
  AND last_active_at > NOW() - INTERVAL '24 hours'
```

선택된 사용자에게만 silent FCM 발송 → 클라이언트가 LMS 크롤링 → `/todo/report` 호출.

## 코딩 규칙

### Kotlin

- data class, sealed class, scope function 적극 사용.
- 도메인 엔티티는 nullable 최소화, factory method(`Todo.create(...)`)로 생성.
- 검증은 도메인 레이어에서 (`require`, `check`).

### JPA

- 엔티티는 `domain/` 에 두고 얇게 유지(getter/setter 노출 최소화).
- LAZY 로딩 기본. 필요 시 `@EntityGraph` 또는 fetch join.
- `@Version` 낙관적 락: UserTodoStatus 동시 갱신(reconcile vs notify-mark) 보호.
- `@DynamicUpdate` 는 핫스팟 엔티티에만.

### 이벤트

- 모든 도메인 이벤트는 `@TransactionalEventListener(phase = AFTER_COMMIT)` 로 받는다.
- 비동기가 필요하면 `@Async` + 전용 `ThreadPoolTaskExecutor`. 외부 호출(AI, FCM)은 무조건 비동기.
- 이벤트 클래스는 발행자 패키지의 `domain/event/` 에 둔다.

### 트랜잭션

- 서비스 메서드 단위 `@Transactional`. 읽기 전용은 `readOnly = true`.
- 외부 API 호출(Anthropic, FCM, SSO)은 트랜잭션 밖.

### Idempotency

- `Todo`: `(subject_id, material_code)` unique + upsert.
- `UserTodoStatus`: `(user_id, todo_id)` unique + upsert.
- `TodoReport`: append-only. 같은 사용자가 같은 데이터 여러 번 보고해도 OK(다수결에 영향 없도록 시간 윈도우 처리).

### 테스트

- 단위 테스트: MockK로 협력 객체 mocking.
- 통합 테스트: `@SpringBootTest` + Testcontainers(PostgreSQL).
- 컨트롤러 테스트: `@WebMvcTest`.
- 이벤트 흐름은 `@RecordApplicationEvents` 또는 통합 테스트로 검증.

## 개발 명령어

```bash
./gradlew build              # 빌드
./gradlew bootRun            # 로컬 실행 (Postgres 필요)
./gradlew test               # 단위 테스트
./gradlew integrationTest    # 통합 테스트 (Testcontainers)
./gradlew ktlintCheck        # 코드 스타일 검사
./gradlew ktlintFormat       # 자동 포맷팅
```

## 환경 변수

- `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
- `FCM_CREDENTIALS_PATH` (서비스 계정 JSON)
- `ANTHROPIC_API_KEY`
- `SSO_CLIENT_ID`, `SSO_CLIENT_SECRET`, `SSO_REDIRECT_URI`
- `JWT_SECRET`, `JWT_EXPIRY_MINUTES`

## 하지 말아야 할 것

- **TodoService.save() 안에서 AnthropicClient 직접 호출** → 비동기 이벤트로 분리.
- **Scheduler에서 FCMClient 직접 호출** → `NotificationService` 경유로 정책·재시도 캡슐화.
- **Subject/Enrollment를 todo 패키지로 이동** → 패키지 경계와 의존성 방향 위반.
- **Redis ZSET, Quartz를 Phase 1에 도입** → 1천명 규모에선 단순 폴링으로 충분. 10만 넘어가면 그때.
- **Hexagonal port/adapter 전면 도입** → 1천명 규모에 오버엔지니어링. 모듈 분리 시점에 같이.
- **FCM을 사용자 단위로 발송** → 디바이스 단위(`UserDevice`)로 발송.
- **클라이언트 보고 데이터를 즉시 다른 사용자에게 전파** → 본인 알림은 즉시, 메타데이터는 quorum 다수결 후.
- **`@TransactionalEventListener` 없이 `@EventListener` 만 쓰기** → 트랜잭션 롤백 시 이벤트가 살아남는 버그.

## Phase 2 이후 확장 포인트

- **사용자 10만+**: 알림 스케줄링을 Redis ZSET delayed queue로 변경.
- **초 단위 알림 정확도**: Quartz로 todo별 트리거 등록.
- **학사정보 모듈 분리**: `subject/` 를 별도 Gradle 모듈 또는 마이크로서비스로.
- **멀티 학교 지원**: `Subject`에 `school_id`, `User`에 `school_id` 추가 (멀티테넌시).

## 결정이 미뤄진 디테일

- **Quorum 임계값**: 2명? 3명? 분반 인원의 5%? — 운영하면서 조정.
- **SSO 학적상태 가용 여부**: 받을 수 있으면 자동 정리, 못 받으면 학기말 배치 추가.
- **분반 정보 수집 경로**: LMS 크롤링에서 자동으로 떨어진다고 가정. 아니면 사용자 시간표 등록 UI 필요.