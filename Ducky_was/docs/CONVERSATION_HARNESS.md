# 대화 하네스 계약

`ConversationRouter`가 검색 전에 DIRECT / HINT / DOCUMENT / CLARIFY를 결정한다.
`ChatService`는 인증된 대화 소유자와 문서 선택 범위를 검증하고 검색 결과를 전달한다.
`ChatResponseService`는 결정된 방식과 근거 안에서 답변을 작성한다.
검색이 필요한데 결과가 비어 있으면 외부 LLM 호출 없이 근거 부족을 반환한다.

## 요청과 저장

기존 `/api/chat/messages` 요청에 선택적 `documentIds` (`document_ids` 별칭)를 추가했다.

- 생략: 대화에 저장된 선택 범위 유지. 최초 미설정 대화는 인증 사용자의 RAG 활성 자료 전체를 검색 범위로 사용한다.
- `[1,2]`: 해당 소유 문서만 검색한다. 타인/삭제 문서 ID는 기존 문서 API 오류로 거부한다.
- `[]`: 빈 범위를 저장한다. 문서 질문에 전체 자료로 검색을 확대하지 않는다.
- 검색 후보는 기존 DocumentService의 소유자·RAG 활성·indexed 필터를 적용한다.

메시지 `sources`와 `responseMode` 계약을 유지한다. 선택 범위는 대화에 저장한다.
추가 메시지 필드 `responseStrategy`는 DIRECT / HINT / DOCUMENT / CLARIFY /
INSUFFICIENT_EVIDENCE / COMPLETION이다. 기존 저장 메시지 및 별도 완료 API는 null일 수 있다.
`responseMode`는 계속 llm / extractive / rule처럼 생성 경로를 표시한다.
일반 메시지와 `/api/chat/hints`의 힌트 번호는 동일 대화 카운터를 사용한다.
근거 부족 답변은 힌트 카운트를 증가시키지 않는다.

추가 nullable DB 컬럼은 `conversations.selected_document_ids_json` (text),
`messages.response_strategy` (varchar(40))이다. 기존 ddl-auto 설정을 따르며 운영 DDL은 별도 확인한다.
저장된 구형 출처 JSON 및 삭제 문서의 과거 출처는 그대로 유지한다.

## 검증 범위

`gradlew.bat test`의 ConversationHarnessTest는 합성 자료와 H2에서 실제 검색·DB 저장·재조회,
선택 범위·타인 격리·미색인 제외·근거 부족·후속 지시 대상·힌트 단계·완료 판정을 확인한다.
ConversationRouterTest는 사전 정책과 기존/새 요청 JSON 호환을 확인한다.

정책은 명시 키워드와 후속 지시어를 사용하는 deterministic 구현이다.
비어 있지 않은 검색 결과가 질문의 답을 포함하는지의 의미적 판단은 LLM 근거 제한 지침에 의존한다.
extractive는 문서 발췌 및 고정 힌트만 제공하고 일반 답변 미생성을 명시한다.
실제 LLM 응답 품질·운영 PostgreSQL·AWS·브라우저 검증을 로컬 테스트로 대체하지 않는다.
BM25/청킹/Lambda 선행 커밋 없이 caee492 검색 인터페이스에서 동작한다.
