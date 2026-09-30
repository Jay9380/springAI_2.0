# Spring Boot 실행 가이드 (실습용 문서)

Spring Boot 애플리케이션은 내장 웹 서버를 포함하므로 별도의 WAS 설치 없이 jar 하나로 실행할 수 있다.

## 개발 중 실행하기

Maven 프로젝트는 플러그인 목표로 바로 실행한다. 코드를 고치면 다시 실행해야 변경 내용이 반영된다.

```bash
./mvnw spring-boot:run
```

## 배포용 jar 만들기

package 단계에서 실행 가능한 jar가 만들어진다. 운영 서버에서는 이 jar를 java 명령으로 실행한다.

```bash
./mvnw package
java -jar target/app.jar
```

---

## 설정 우선순위

같은 설정이 여러 곳에 있으면 명령행 인자가 가장 우선하고, 그다음 환경 변수, 마지막으로 application.yml 순서로 적용된다.

> 운영 환경의 비밀번호나 API 키는 application.yml에 적지 말고 환경 변수로 주입한다.
