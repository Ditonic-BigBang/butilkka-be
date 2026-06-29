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
kakao.client-secret=카카오_클라이언트_시크릿
```

값은 팀에게 별도로 공유받으세요.
- `kakao.client-id` → 카카오 디벨로퍼 → 앱 설정 → 앱 키 → **REST API 키**
- `kakao.client-secret` → 카카오 디벨로퍼 → 보안 → **클라이언트 시크릿 코드**

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

로그인 성공 시 **Access Token**(30분)과 **Refresh Token**(7일)을 **HttpOnly 쿠키**로 발급합니다.
토큰은 URL에 노출되지 않으며, 브라우저가 자동으로 쿠키를 전송합니다.

API 요청 시 쿠키가 자동으로 전송되므로 별도의 헤더 설정이 불필요합니다.
단, 프론트엔드에서 `fetch` 또는 `axios` 호출 시 **`credentials: 'include'`** 옵션을 반드시 설정해야 합니다.

```js
// fetch 예시
fetch('http://localhost:8080/api/v1/users/me', {
  credentials: 'include',
});
```

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
   → 백엔드가 CSRF 방지용 state 쿠키(5분) 설정 후 카카오 인증 페이지로 리다이렉트

2. 사용자가 카카오 로그인 완료
   → 카카오가 백엔드 콜백 호출
   GET /api/v1/auth/kakao/callback?code=...&state=...

3. 백엔드 처리
   - state 검증 (CSRF 방지)
   - 카카오로부터 사용자 정보 조회
   - 최초 로그인 시 자동 회원가입
   - Access Token(30분) / Refresh Token(7일) 발급
   - 토큰을 HttpOnly 쿠키로 설정

4. 백엔드 → 프론트로 리다이렉트
   http://localhost:5173/auth/kakao?success=true&isOnboarded=true/false
```

`isOnboarded`가 `false`이면 온보딩 화면으로 이동해야 합니다.

### 주요 인증 엔드포인트

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/api/v1/auth/kakao/login` | 카카오 로그인 시작 (프론트에서 redirect 또는 window.location 이용) |
| `GET` | `/api/v1/auth/kakao/callback` | 카카오 OAuth 콜백 (직접 호출 X) |
| `POST` | `/api/v1/auth/refresh` | Access Token 재발급 (refresh_token 쿠키 필요) |
| `POST` | `/api/v1/auth/logout` | 로그아웃 (쿠키 삭제 + DB에서 refresh token 제거) |
| `GET` | `/api/v1/users/me` | 로그인한 사용자 정보 조회 (인증 필요) |

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
| `KAKAO_CLIENT_SECRET` | 카카오 클라이언트 시크릿 |
| `FRONTEND_URL` | 프론트엔드 URL (예: `https://your-frontend.com`) |

> EC2 인스턴스에 Docker가 설치되어 있어야 합니다.
> `EC2_HOST` Secret이 없으면 배포 단계는 자동으로 스킵됩니다.

---

## API 문서

앱 실행 후 아래 주소에서 확인할 수 있습니다.

```
http://localhost:8080/swagger-ui.html
```
