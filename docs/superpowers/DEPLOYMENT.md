# 배포 인프라 현황 (EC2 CI/CD + DB)

2026-07-11 기준. EC2 인스턴스를 새로 만들고 CI/CD와 DB를 연결한 작업 기록.

## 인프라 구성

- **EC2**: Ubuntu 24.04 LTS, `t3.small`(2GiB), 리전 ap-northeast-2(서울)
  - Elastic IP 연결됨 (IP: `13.125.15.219`) — 인스턴스 재시작해도 고정
  - 스왑 2GB 추가 설정됨 (`/swapfile`, `vm.swappiness=10`)
  - 최초 `t3.micro`(1GiB)로 잘못 생성했다가 CI/CD 배포 중 MySQL+JVM+nginx 메모리 부족으로 인스턴스 전체가 먹통(SSH 응답 없음, ping 실패)이 되는 문제가 있어 `t3.small`로 리사이즈함
- **도메인**: `api.butilkka.site` (가비아 DNS, A레코드 → Elastic IP). 프론트는 `https://butilkka.site`
- **HTTPS**: nginx 리버스 프록시(`/etc/nginx/sites-available/butilkka`, 80/443 → `127.0.0.1:8080`) + Let's Encrypt(`certbot --nginx`), 자동 갱신 설정됨

## Docker 구성 (EC2 위)

- 네트워크: `butilkka-net` (앱-DB 통신용 전용 브리지 네트워크)
- `butilkka-mysql`: MySQL 8.0 컨테이너, `butilkka-net`에 연결, 포트는 외부/호스트에 노출 안 함 (컨테이너 이름으로만 접근). 데이터는 named volume `butilkka-mysql-data`로 영구 보존
  - **최초 1회 수동으로 띄운 뒤 계속 유지** — CD가 재배포할 때마다 새로 만들지 않음 (데이터 유실 방지)
- `butilkka-be`: 앱 컨테이너, CD가 `dev` push마다 재생성. `--network butilkka-net`으로 연결되어 `jdbc:mysql://butilkka-mysql:3306/butilkka...`로 DB 접속

세팅 명령어는 `README.md`의 "EC2 사전 준비" 절 참고.

## CI/CD (`.github/workflows/ci-cd.yml`)

- `dev` push/PR → 빌드+테스트 → (push일 때만) Docker Hub 이미지 push → SSH로 EC2에 배포
- GitHub Secrets 12개 전부 등록됨: `EC2_HOST`, `EC2_USERNAME`, `EC2_SSH_KEY`(배포 전용 별도 SSH 키페어, EC2 `~/.ssh/authorized_keys`에 등록), `DOCKER_USERNAME`, `DOCKER_PASSWORD`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`, `KAKAO_REDIRECT_URI`, `FRONTEND_URL`
- 카카오 디벨로퍼 콘솔에 프로덕션 redirect URI(`https://api.butilkka.site/api/v1/auth/kakao/callback`)와 사이트 도메인 등록 완료

## 발견/수정한 버그: FlywayMigrationListener가 DataSource를 오염시킴

배포할 때마다 `DataSourceBeanCreationException: Failed to determine suitable jdbc url` (나중엔 driver class 버전)로 크래시 반복. `DB_URL`/`SPRING_DATASOURCE_URL` 환경변수가 컨테이너에 정확히 전달되는 것까지 확인했는데도 재현되어, 로컬 PC에 Docker MySQL을 띄우고 `java -jar`로 직접 재현하며 원인을 찾음.

**원인**: `FlywayMigrationListener`(`BeanFactoryPostProcessor`)가 `BeanFactoryPostProcessor` 단계(Spring이 `ConfigurationPropertiesBindingPostProcessor`를 등록하기 *이전* 시점)에서 `beanFactory.getBeanProvider(JdbcConnectionDetails.class).getIfAvailable()`을 호출함. 이 시점에 Spring Boot의 `PropertiesJdbcConnectionDetails`(내부적으로 `DataSourceProperties`를 감싸는 빈)가 강제로 먼저 생성되는데, 아직 `@ConfigurationProperties` 바인딩이 안 된 상태라 **빈 값으로 바인딩된 `DataSourceProperties` 싱글톤이 캐시**되어 버림. 이후 진짜 JPA/Hikari `DataSource`를 만들 때도 이 오염된 캐시를 재사용해서 항상 실패했음.

**수정**: `JdbcConnectionDetails` 조회를 아예 제거하고, `spring.datasource.*` 프로퍼티를 `Environment`에서 직접 읽도록 단순화(`847f9c4`). docker-compose 기반 로컬 개발 포트 자동 감지 때문에 있던 로직이었는데, 실제로는 `compose.yml` 포트(3307)와 프로퍼티 기본값이 수동으로 맞춰져 있어서 필요 없었음.

**교훈**: `BeanFactoryPostProcessor`/`BeanDefinitionRegistryPostProcessor` 안에서 `@ConfigurationProperties` 기반 빈을 미리 조회하면 (설령 예외를 잡더라도) 그 빈이 빈 상태로 캐시되어 이후 정상 생성 시점까지 오염시킬 수 있다. 이런 빈은 절대 이 단계에서 건드리면 안 됨.

## AI 서버 배포 완료 (2026-07-13)

- **AI 서버(FastAPI)**: 새 EC2에 배포 완료
  - URL: `http://3.38.26.1:8000` (Elastic IP, 고정)
  - Health check: `GET /health` → `{"status":"ok","redis":"connected"}`
  - 리포트 생성: `POST /api/report/generate` (20~30초 소요)
- BE의 `AI_SERVER_URL` 기본값이 이미 `http://3.38.26.1:8000`으로 설정되어 있었으나, 배포 환경에서 다른 값으로 덮어써질 가능성을 차단하기 위해 CD(`ci-cd.yml`)의 `docker run`에 `AI_SERVER_URL=http://3.38.26.1:8000`을 명시적으로 추가함
- 리포트 생성 기능 정상 동작 확인됨

## 알려진 후속 작업 (미완료)

- `butilkka-mysql`의 비밀번호가 `1234`로 약함 — 운영 트래픽 늘기 전에 강한 비밀번호로 교체 권장 (GitHub Secret `DB_PASSWORD`, `SPRING_DATASOURCE_PASSWORD`도 함께 갱신 필요)
- 기존에 쓰던 Amazon Linux 인스턴스(초기 실수로 생성)는 안 쓰면 종료해서 과금 방지 필요
