# WeMeet 개발산출물 초안

선배 엑셀의 시트 구조를 기준으로 현재 `wemeet` 구현을 역설계해 정리한 초안이다.

## 1. 메뉴구조도

| 구분 | 1depth | 2depth | 3depth | 4depth | 5depth | Tab/Page | Page/기능구분 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Page | 랜딩 | 게스트 추천 결과 | - | - | - | Page | Program |
| Page | 로그인/회원가입 | 로그인 | - | - | - | Page | Program |
| Page | 로그인/회원가입 | 회원가입 | - | - | - | Page | Program |
| Page | 메인 | 홈 | - | - | - | Page | Program |
| Page | 메인 | 친구 관리 | 친구 목록 조회 | - | - | Page | Program |
| Page | 메인 | 친구 관리 | 친구 코드로 친구 추가 | - | - | Page | Program |
| Page | 메인 | 검색 이력 | 카테고리 필터 조회 | - | - | Tab | Program |
| Page | 메인 | 검색 이력 | 키워드 검색 | - | - | Tab | Program |
| Page | 메인 | 프로필 | 기본 출발지 수정 | - | - | Page | Program |
| Page | 메인 | 모임 생성 | 참가자 선택 | - | - | Page | Program |
| Page | 메인 | 모임 생성 | 추천 미리보기 | - | - | Page | Program |
| Page | 메인 | 추천 결과 | 중간지점 계산 | - | - | Page | Program |
| Page | 메인 | 추천 결과 | 장소 후보 조회 | - | - | Page | Program |
| Page | 메인 | 추천 결과 | 지도 포인트 표시 | - | - | Page | Program |
| API | 인증 API | 회원가입 | - | - | - | API | Program |
| API | 인증 API | 로그인 | - | - | - | API | Program |
| API | 인증 API | 비밀번호 재설정 토큰 발급 | - | - | - | API | Program |
| API | 인증 API | 비밀번호 재설정 확정 | - | - | - | API | Program |
| API | 사용자 API | 내 정보 조회 | 주소 수정 | - | - | API | Program |
| API | 친구 API | 친구 목록 조회 | 친구 추가 | - | - | API | Program |
| API | 모임 API | 모임 목록 조회 | 모임 생성 | - | - | API | Program |
| API | 추천 API | 카테고리 조회 | 추천 결과 조회 | - | - | API | Program |
| API | 기타 API | 헬스체크 | - | - | - | API | Program |

## 2. 프로그램 명세서

| NO | 업무영역 Lev1 | Lev2 | Lev3 | Lev4 | Lev5 | 프로그램명 | DB Transaction | 프로그램ID | 작업자 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 랜딩 | 랜딩 페이지 | 게스트 추천 결과 | - | - | 랜딩/게스트 추천 | R | LANDING_001 | 본인 |
| 2 | 로그인/회원가입 | 로그인 | - | - | - | 로그인 화면/처리 | R | LOGIN_001 | 본인 |
| 3 | 로그인/회원가입 | 회원가입 | - | - | - | 회원가입 화면/처리 | C | SIGNUP_001 | 본인 |
| 4 | 로그인/회원가입 | 로그아웃 | - | - | - | 로그아웃 | - | LOGOUT_001 | 본인 |
| 5 | 메인 | 홈 | - | - | - | 홈 대시보드 | R | HOME_001 | 본인 |
| 6 | 메인 | 친구 관리 | 친구 목록 조회 | - | - | 친구 목록 | R | FRIEND_001 | 본인 |
| 7 | 메인 | 친구 관리 | 친구 코드로 친구 추가 | - | - | 친구 추가 | C | FRIEND_002 | 본인 |
| 8 | 메인 | 검색 이력 | 이력 조회/필터링 | - | - | 검색 이력 | R | HISTORY_001 | 본인 |
| 9 | 메인 | 프로필 | 프로필 조회 | - | - | 프로필 조회 | R | PROFILE_001 | 본인 |
| 10 | 메인 | 프로필 | 기본 출발지 수정 | - | - | 기본 주소 수정 | U | PROFILE_002 | 본인 |
| 11 | 메인 | 모임 생성 | 모임 작성 화면 | - | - | 모임 생성 화면 | R | MEETING_001 | 본인 |
| 12 | 메인 | 모임 생성 | 추천 미리보기 | - | - | 추천 미리보기 | R | MEETING_002 | 본인 |
| 13 | 메인 | 모임 | 모임 생성 | - | - | 모임 저장 | C | MEETING_003 | 본인 |
| 14 | 메인 | 추천 결과 | 중간지점 계산 | 장소 후보 산출 | - | 추천 결과 조회 | R | RECOMMEND_001 | 본인 |
| 15 | API | 인증 API | 회원가입 | - | - | `/api/auth/signup` | C | API_AUTH_001 | 본인 |
| 16 | API | 인증 API | 로그인 | - | - | `/api/auth/login` | R | API_AUTH_002 | 본인 |
| 17 | API | 인증 API | 비밀번호 재설정 토큰 발급 | - | - | `/api/auth/password/reset-request` | C | API_AUTH_003 | 본인 |
| 18 | API | 인증 API | 비밀번호 재설정 확정 | - | - | `/api/auth/password/reset-confirm` | R, U | API_AUTH_004 | 본인 |
| 19 | API | 사용자 API | 내 정보 조회 | - | - | `/api/me` | R | API_USER_001 | 본인 |
| 20 | API | 사용자 API | 기본 주소 수정 | - | - | `/api/me/address` | U | API_USER_002 | 본인 |
| 21 | API | 친구 API | 친구 목록 조회 | - | - | `/api/friends` GET | R | API_FRIEND_001 | 본인 |
| 22 | API | 친구 API | 친구 추가 | - | - | `/api/friends` POST | C | API_FRIEND_002 | 본인 |
| 23 | API | 이력 API | 검색 이력 조회 | - | - | `/api/history` | R | API_HISTORY_001 | 본인 |
| 24 | API | 모임 API | 모임 목록 조회 | - | - | `/api/meetings` GET | R | API_MEETING_001 | 본인 |
| 25 | API | 모임 API | 모임 생성 | - | - | `/api/meetings` POST | C | API_MEETING_002 | 본인 |
| 26 | API | 추천 API | 카테고리 조회 | - | - | `/api/categories` | R | API_RECOMMEND_001 | 본인 |
| 27 | API | 추천 API | 장소 추천 조회 | - | - | `/api/recommendations` | R | API_RECOMMEND_002 | 본인 |
| 28 | API | 운영 API | 헬스체크 | - | - | `/api/health` | - | API_COMMON_001 | 본인 |

## 3. 컬렉션 정의서(NoSQL) 대체안

현재 프로젝트는 MongoDB를 사용하지 않고 Redis를 세션/추천 캐시에 사용한다. 선배 양식의 `컬렉션 정의서(NoSQL)` 시트는 아래처럼 `Redis 키 정의서`로 대체하는 것이 맞다.

| Collection ID | Collection 명 | NO | Depth | 칼럼ID | 칼럼명 | Type | Length | Decimal | PK | NOT NULL |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| REDIS_SESSION | 세션 토큰 | 1 | 1 | KEY | Redis Key (`wemeet:session:{token}`) | STRING | 255 | - | * | * |
|  |  | 2 | 1 | USER_ID | 사용자 ID | STRING | 40 | - |  | * |
|  |  | 3 | 1 | TTL_MINUTES | 세션 만료시간(분) | NUMBER | 10 | 0 |  | * |
| REDIS_RECOMMENDATION | 추천 캐시 | 1 | 1 | KEY | Redis Key (`wemeet:recommendation:{cacheKey}`) | STRING | 255 | - | * | * |
|  |  | 2 | 1 | PAYLOAD_JSON | 추천 응답 JSON | STRING | 4000+ | - |  | * |
|  |  | 3 | 1 | TTL_MINUTES | 캐시 만료시간(분) | NUMBER | 10 | 0 |  | * |

## 4. 테이블 명세서(RDBMS)

현재 코드상 실제 JPA 엔티티와 MariaDB 테이블은 `app_user`, `password_reset_token`, `friend_relation`, `search_history`, `meeting`, `meeting_participant` 여섯 개다.

### 4.1 구현 테이블

| Table ID | Table 명 | NO | Column ID | Column Name | Type | Length | Decimal | PK | NOT NULL | UNIQUE |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| APP_USER | 회원정보 | 1 | ID | 사용자 ID | VARCHAR | 40 | - | * | * |  |
|  |  | 2 | LOGIN_ID | 로그인 ID | VARCHAR | 60 | - |  | * | * |
|  |  | 3 | NICKNAME | 닉네임 | VARCHAR | 80 | - |  | * |  |
|  |  | 4 | EMAIL | 이메일 | VARCHAR | 120 | - |  | * | * |
|  |  | 5 | PASSWORD_HASH | 비밀번호 해시 | VARCHAR | 120 | - |  | * |  |
|  |  | 6 | FRIEND_CODE | 친구코드 | VARCHAR | 40 | - |  | * | * |
|  |  | 7 | BASE_ADDRESS | 기본 출발지 주소 | VARCHAR | 255 | - |  | * |  |
|  |  | 8 | CREATED_AT | 생성일시 | DATETIME | - | - |  | * |  |
|  |  | 9 | UPDATED_AT | 수정일시 | DATETIME | - | - |  | * |  |
| PASSWORD_RESET_TOKEN | 비밀번호 재설정 토큰 | 1 | ID | 토큰 ID | BIGINT | - | - | * | * |  |
|  |  | 2 | USER_ID | 사용자 ID | VARCHAR | 40 | - |  | * |  |
|  |  | 3 | TOKEN_HASH | 토큰 해시 | VARCHAR | 80 | - |  | * | * |
|  |  | 4 | EXPIRES_AT | 만료일시 | DATETIME | - | - |  | * |  |
|  |  | 5 | USED | 사용여부 | BOOLEAN | 1 | - |  | * |  |
|  |  | 6 | CREATED_AT | 생성일시 | DATETIME | - | - |  | * |  |
| FRIEND_RELATION | 친구관계 | 1 | USER_ID | 사용자 ID | VARCHAR | 40 | - | * | * |  |
|  |  | 2 | FRIEND_USER_ID | 친구 사용자 ID | VARCHAR | 40 | - | * | * |  |
|  |  | 3 | CREATED_AT | 생성일시 | DATETIME | - | - |  | * |  |
| SEARCH_HISTORY | 검색이력 | 1 | HISTORY_ID | 검색이력 ID | BIGINT | - | - | * | * |  |
|  |  | 2 | USER_ID | 사용자 ID | VARCHAR | 40 | - |  | * |  |
|  |  | 3 | QUERY | 검색어 | VARCHAR | 255 | - |  | * |  |
|  |  | 4 | CATEGORY | 카테고리 | VARCHAR | 30 | - |  | * |  |
|  |  | 5 | SEARCHED_AT | 검색일시 | DATETIME | - | - |  | * |  |
| MEETING | 모임 | 1 | MEETING_ID | 모임 ID | VARCHAR | 40 | - | * | * |  |
|  |  | 2 | TITLE | 모임명 | VARCHAR | 120 | - |  | * |  |
|  |  | 3 | DESCRIPTION | 설명 | VARCHAR | 1000 | - |  |  |  |
|  |  | 4 | MEETING_DATE | 모임일자 | DATE | - | - |  | * |  |
|  |  | 5 | CATEGORY | 카테고리 | VARCHAR | 30 | - |  | * |  |
|  |  | 6 | HOST_USER_ID | 주최자 ID | VARCHAR | 40 | - |  | * |  |
|  |  | 7 | CREATED_AT | 생성일시 | DATETIME | - | - |  | * |  |
| MEETING_PARTICIPANT | 모임참가자 | 1 | MEETING_ID | 모임 ID | VARCHAR | 40 | - | * | * |  |
|  |  | 2 | USER_ID | 사용자 ID | VARCHAR | 40 | - | * | * |  |
|  |  | 3 | ROLE | 역할(HOST/PARTICIPANT) | VARCHAR | 20 | - |  | * |  |
|  |  | 4 | CREATED_AT | 생성일시 | DATETIME | - | - |  | * |  |

## 5. 논리 데이터 모델링(ERD)

### 엔터티

- APP_USER
- PASSWORD_RESET_TOKEN
- FRIEND_RELATION
- SEARCH_HISTORY
- MEETING
- MEETING_PARTICIPANT

### 관계

- APP_USER 1 : N PASSWORD_RESET_TOKEN
- APP_USER N : N APP_USER
  `FRIEND_RELATION`으로 해소
- APP_USER 1 : N SEARCH_HISTORY
- APP_USER 1 : N MEETING
  `HOST_USER_ID`
- APP_USER N : N MEETING
  `MEETING_PARTICIPANT`으로 해소
- MEETING 1 : N MEETING_PARTICIPANT

## 6. 물리 데이터 모델링(ERD)

### PK/FK 요약

- `APP_USER.ID` PK
- `PASSWORD_RESET_TOKEN.ID` PK
- `PASSWORD_RESET_TOKEN.USER_ID` FK -> `APP_USER.ID`
- `FRIEND_RELATION.USER_ID` FK -> `APP_USER.ID`
- `FRIEND_RELATION.FRIEND_USER_ID` FK -> `APP_USER.ID`
- `SEARCH_HISTORY.HISTORY_ID` PK
- `SEARCH_HISTORY.USER_ID` FK -> `APP_USER.ID`
- `MEETING.MEETING_ID` PK
- `MEETING.HOST_USER_ID` FK -> `APP_USER.ID`
- `MEETING_PARTICIPANT.MEETING_ID` FK -> `MEETING.MEETING_ID`
- `MEETING_PARTICIPANT.USER_ID` FK -> `APP_USER.ID`

### 인덱스 제안

- `APP_USER.LOGIN_ID` Unique Index
- `APP_USER.EMAIL` Unique Index
- `APP_USER.FRIEND_CODE` Unique Index
- `PASSWORD_RESET_TOKEN.TOKEN_HASH` Unique Index
- `PASSWORD_RESET_TOKEN.USER_ID`
- `FRIEND_RELATION(USER_ID, FRIEND_USER_ID)`
- `FRIEND_RELATION.FRIEND_USER_ID`
- `SEARCH_HISTORY(USER_ID, SEARCHED_AT)`
- `MEETING.HOST_USER_ID`
- `MEETING_PARTICIPANT(MEETING_ID, USER_ID)`
- `MEETING_PARTICIPANT.USER_ID`

## 7. WBS 초안

| 태스크 | 작업자 | 상태 | 시작일 | 종료일 | 기간 | 진척률 |
| --- | --- | --- | --- | --- | --- | --- |
| 요구사항 정리 | 본인 | Finished | 2026-03-10 | 2026-03-11 | 2 | 100% |
| 화면 구조 설계 | 본인 | Finished | 2026-03-11 | 2026-03-12 | 2 | 100% |
| 메뉴 구조도 작성 | 본인 | Finished | 2026-03-11 | 2026-03-12 | 2 | 100% |
| 프로그램 명세서 작성 | 본인 | Finished | 2026-03-11 | 2026-03-12 | 2 | 100% |
| DB 설계 | 본인 | Finished | 2026-03-12 | 2026-03-20 | 9 | 100% |
| 논리 ERD 작성 | 본인 | Finished | 2026-03-12 | 2026-03-20 | 9 | 100% |
| 물리 ERD 작성 | 본인 | Finished | 2026-03-13 | 2026-03-20 | 8 | 100% |
| 테이블 명세서 작성 | 본인 | Finished | 2026-03-13 | 2026-03-20 | 8 | 100% |
| 개발환경 세팅 | 본인 | Finished | 2026-03-12 | 2026-03-13 | 2 | 100% |
| Spring Boot 세팅 | 본인 | Finished | 2026-03-12 | 2026-03-12 | 1 | 100% |
| MariaDB 세팅 | 본인 | Finished | 2026-03-12 | 2026-03-13 | 2 | 100% |
| Redis 세팅 | 본인 | Finished | 2026-03-12 | 2026-03-13 | 2 | 100% |
| 화면 구현 | 본인 | Finished | 2026-03-13 | 2026-03-20 | 8 | 100% |
| 로그인/회원가입 구현 | 본인 | Finished | 2026-03-13 | 2026-03-14 | 2 | 100% |
| 홈/친구/프로필 구현 | 본인 | Finished | 2026-03-14 | 2026-03-15 | 2 | 100% |
| 추천 결과/모임 구현 | 본인 | Finished | 2026-03-15 | 2026-03-20 | 6 | 100% |
| API 구현 | 본인 | Finished | 2026-03-13 | 2026-03-20 | 8 | 100% |
| 인증 API 구현 | 본인 | Finished | 2026-03-13 | 2026-03-14 | 2 | 100% |
| 사용자/친구 API 구현 | 본인 | Finished | 2026-03-14 | 2026-03-15 | 2 | 100% |
| 모임/추천 API 구현 | 본인 | Finished | 2026-03-15 | 2026-03-17 | 3 | 100% |
| 배포 구성 정리 | 본인 | Finished | 2026-03-13 | 2026-03-14 | 2 | 100% |
| Docker 구성 | 본인 | Finished | 2026-03-13 | 2026-03-13 | 1 | 100% |
| AWS 배포 문서 정리 | 본인 | Finished | 2026-03-13 | 2026-03-14 | 2 | 100% |
| 테스트 코드 작성 | 본인 | In Progress | 2026-03-14 | 2026-03-20 | 7 | 75% |
| 문서 보정 및 제출본 정리 | 본인 | Finished | 2026-03-17 | 2026-03-20 | 4 | 100% |

## 8. 작성 근거 파일

- 메뉴/화면 흐름: `src/main/java/com/kopo/wemeet/controller/WemeetController.java`
- API 목록: `src/main/java/com/kopo/wemeet/controller/ApiRestController.java`
- 회원/토큰 테이블: `src/main/java/com/kopo/wemeet/entity/AppUser.java`, `src/main/java/com/kopo/wemeet/entity/PasswordResetToken.java`
- 친구/이력/모임 저장 구조: `src/main/java/com/kopo/wemeet/repository/WemeetDataStore.java`
- Redis 키 구조: `src/main/java/com/kopo/wemeet/session/RefreshTokenStore.java`, `src/main/java/com/kopo/wemeet/service/impl/RecommendationCacheService.java`
- 배포 구성: `compose.yaml`, `DEPLOY_AWS.md`

## 9. 제출 전 보정 권장사항

- 선배 엑셀 양식을 그대로 쓸 경우 `컬렉션 정의서(NoSQL)` 시트명은 유지하고 내용만 Redis 키 정의로 바꾸는 편이 안전하다.
- ERD 시트에는 현재 구현 테이블 6개를 기준으로 표기하면 된다.
- 작업자명, 일정, 프로그램ID 규칙은 제출자 정보에 맞게 최종 수정해야 한다.
