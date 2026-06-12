# Ducky WAS 🦆

> 음성인식 기반 러버덕 AI 학습 시스템 백엔드

## 기술 스택
- Java 17
- Spring Boot 4.0.6
- PostgreSQL 17
- Redis

## 로컬 실행 방법

### 1. PostgreSQL 설치 및 설정
```sql
CREATE USER rubberduck WITH PASSWORD 'your-local-db-password';
CREATE DATABASE rubberduck OWNER rubberduck;
GRANT ALL PRIVILEGES ON DATABASE rubberduck TO rubberduck;
```

### 2. pgvector 설치
```bash
brew install pgvector  # Mac
```
```sql
-- postgres 슈퍼유저로 접속 후
\c rubberduck
CREATE EXTENSION IF NOT EXISTS vector;
```

### 3. .env 파일 생성
프로젝트 루트에 `.env` 파일 직접 생성: