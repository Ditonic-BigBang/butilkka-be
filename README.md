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
spring.datasource.password=1234
kakao.client-id=카카오_REST_API_키
```

`kakao.client-id` 값은 팀에게 별도로 공유받으세요.

### 3. 실행

```bash
./gradlew bootRun
```

또는 IntelliJ에서 프로젝트를 열고 `ButilkkaBeApplication`을 실행합니다.

> Docker Desktop이 실행 중이면 MySQL 컨테이너는 Spring Boot가 자동으로 시작합니다.

---

## 카카오 로그인 흐름

```
1. 프론트 → 아래 URL로 브라우저 이동
   https://kauth.kakao.com/oauth/authorize
     ?client_id={KAKAO_CLIENT_ID}
     &redirect_uri=http://localhost:8080/api/v1/auth/kakao/callback
     &response_type=code

2. 카카오 인증 완료 → 백엔드 콜백 호출
   GET /api/v1/auth/kakao/callback?code=...

3. 백엔드 → 프론트로 리다이렉트
   http://localhost:3000/auth/kakao
     ?accessToken=...
     &refreshToken=...
     &isOnboarded=false
```

---

## API 문서

앱 실행 후 아래 주소에서 확인할 수 있습니다.

```
http://localhost:8080/swagger-ui.html
```
