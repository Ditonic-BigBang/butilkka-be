# butilkka-be

## 기술 스택

- Java 17
- Spring Boot 4.1.0
- MySQL 8.0 (Docker)
- JPA / Hibernate
- Spring Security + JWT
- Kakao OAuth2 (서버사이드 인가 코드 방식)
- Flyway
- Swagger (springdoc-openapi)

---

## 로컬 개발 환경 세팅

### 사전 설치

- [Java 17](https://adoptium.net/)
- [Docker Desktop](https://www.docker.com/products/docker-desktop/)

### 1. 클론 및 브랜치 이동

```bash
git clone https://github.com/Ditonic-BigBang/butilkka-be.git
cd butilkka-be
git checkout dev
```

### 2. 시크릿 파일 생성

`src/main/resources/application-secret.properties` 파일을 직접 생성합니다.
(이 파일은 `.gitignore`에 포함되어 있어 저장소에 올라가지 않습니다.)

```properties
kakao.client-id=카카오_REST_API_키
```

`kakao.client-id` 값은 팀에게 별도로 공유받으세요.

### 3. 실행

```bash
# Mac / Linux / Git Bash
./gradlew bootRun

# Windows PowerShell / CMD
.\gradlew.bat bootRun
```

또는 IntelliJ에서 프로젝트를 열고 `ButilkkaBeApplication`을 실행합니다.

> Docker Desktop이 실행 중이면 MySQL 컨테이너는 Spring Boot가 자동으로 시작합니다.

---

## 인증 방식

카카오 소셜 로그인만 지원합니다. 일반 회원가입 / 로그인은 없습니다.

로그인 성공 시 **Access Token**(30분)과 **Refresh Token**(7일)을 발급합니다.
이후 모든 API 요청은 Authorization 헤더에 Access Token을 담아 보내야 합니다.

```
Authorization: Bearer {accessToken}
```

Access Token이 만료된 경우 `POST /api/v1/auth/reissue`로 토큰을 재발급받습니다.

---

## 카카오 로그인 흐름

### 카카오 디벨로퍼 콘솔 설정

[카카오 디벨로퍼](https://developers.kakao.com)에서 앱 생성 후 아래 설정이 필요합니다.

- 플랫폼 > Web > 사이트 도메인: `http://localhost:8080`
- 카카오 로그인 > 활성화: ON
- 카카오 로그인 > Redirect URI 등록:
  - 로컬: `http://localhost:8080/api/v1/auth/kakao/callback`
  - 프로덕션: `https://{서버도메인}/api/v1/auth/kakao/callback`

### 로그인 흐름

```
1. 프론트 → GET /api/v1/auth/kakao/login 호출
   → 백엔드가 카카오 인증 페이지로 리다이렉트

2. 사용자가 카카오 로그인 완료
   → 카카오가 백엔드 콜백 호출
   GET /api/v1/auth/kakao/callback?code=...

3. 백엔드 처리
   - 카카오로부터 사용자 정보 조회
   - 최초 로그인 시 자동 회원가입
   - Access Token / Refresh Token 발급

4. 백엔드 → 프론트로 리다이렉트
   {FRONTEND_URL}/auth/kakao
     ?accessToken=...
     &refreshToken=...
     &isOnboarded=true/false
```

`isOnboarded`가 `false`이면 온보딩 화면으로 이동해야 합니다.

### 토큰 재발급

```
POST /api/v1/auth/reissue
Content-Type: application/json

{ "refreshToken": "..." }
```

---

## EC2 배포 세팅 (GitHub Actions CD)

`dev` 브랜치에 push하면 CI 통과 후 자동으로 EC2에 배포됩니다.
아래 GitHub Secrets를 저장소 Settings → Secrets and variables → Actions에 등록해야 합니다.

| Secret 이름 | 설명 |
|---|---|
| `EC2_HOST` | EC2 퍼블릭 IP 또는 도메인 |
| `EC2_USERNAME` | EC2 SSH 접속 유저명 (예: `ubuntu`) |
| `EC2_SSH_KEY` | EC2 SSH 프라이빗 키 (PEM 파일 내용 전체) |
| `DOCKER_USERNAME` | Docker Hub 유저명 |
| `DOCKER_PASSWORD` | Docker Hub 비밀번호 또는 Access Token |
| `DB_URL` | 프로덕션 DB JDBC URL |
| `DB_USERNAME` | 프로덕션 DB 유저명 |
| `DB_PASSWORD` | 프로덕션 DB 비밀번호 |
| `JWT_SECRET` | JWT 서명 키 (32자 이상) |
| `KAKAO_CLIENT_ID` | 카카오 REST API 키 |

> EC2 인스턴스에 Docker가 설치되어 있어야 합니다.
> `EC2_HOST` Secret이 없으면 배포 단계는 자동으로 스킵됩니다.

---

## API 문서

앱 실행 후 아래 주소에서 확인할 수 있습니다.

```
http://localhost:8080/swagger-ui.html
```
