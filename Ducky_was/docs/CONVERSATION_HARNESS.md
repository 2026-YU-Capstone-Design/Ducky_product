# 대화 하네스 계약

`ConversationRouter`가 검색 전에 DIRECT / HINT / DOCUMENT / CLARIFY를 결정한다.
`ChatService`는 인증된 대화 소유자와 문서 선택 범위를 검증하고 검색 결과를 전달한다.
`ChatResponseService`는 결정된 방식과 근거 안에서 답변을 작성한다.
검색이 필요한데 결과가 비어 있으면 외부 LLM 호출 없이 근거 부족을 반환한다.
저장된 선택 문서 ID는 의도 판정에 전달하지 않는다. 현재 요청에 명시한 비어 있지 않은 documentIds는
새 첨부/명시적 선택이라는 문서 문맥을 제공하므로 "Quasar retry budget"처럼 문서 키워드가 없어도 검색한다.
이 요청 단위 신호는 다음 요청으로 상속하지 않는다. 문서 질문 뒤 documentIds를 생략한 새 일반 질문은 DIRECT이며,
"문서와 관계없이" 같은 명시적 일반 질문은 이전 문서 문맥을 상속하지 않는다.
일반 질문 이후에도 저장된 선택 범위는 유지하여 다음 문서 질문에 적용한다.
대상/코드/이력 없는 지시어 질문과 힌트 요청은 CLARIFY이며 힌트 카운터를 소모하지 않는다.

## 요청과 저장

기존 `/api/chat/messages` 요청에 선택적 `documentIds` (`document_ids` 별칭)를 추가했다.

- 생략: 대화에 저장된 선택 범위 유지. 최초 미설정 대화는 인증 사용자의 RAG 활성 자료 전체를 검색 범위로 사용한다.
- `[1,2]`: 해당 소유 문서만 검색한다. 타인/삭제 문서 ID는 기존 문서 API 오류로 거부한다.
- `[]`: 빈 범위를 저장한다. 문서 질문에 전체 자료로 검색을 확대하지 않는다.
- 검색 후보는 기존 DocumentService의 소유자·RAG 활성·indexed 필터를 적용한다.

현재 요청에 IDs가 있어도 명시적 "문서와 관계없이"는 일반 질문으로 취급하고,
대상 없는 힌트 요청은 CLARIFY를 유지한다. []는 문서 문맥 신호가 아니며 빈 검색 범위로 저장한다.
문서 범위는 그대로 보존하므로 일반 질문 이후 명시적인 문서 후속 질문은 저장된 범위를 재사용한다.
프론트 계약은 변경하지 않는다: 첨부를 indexed까지 기다린 뒤 새 IDs와 원래 질문을 전송하고,
다음 첨부 없는 요청에서는 documentIds를 생략한다. 이 흐름은 MockMvc API 및 저장 재조회로 검증한다.

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
비어 있지 않은 검색 후보는 근거 충분을 뜻하지 않는다. 문서에 등장한 단어와 겹쳐도
질문의 답(예: 창시자의 생년월일)이 없는 후보가 실제 lexical 검색에 반환되는 반례를 검증한다.
extractive는 후보 발췌에 근거 충족 미판정 안내를 붙인다. DOCUMENT는 질문 의도이며
근거 충분 판정이나 정답 보장이 아니다. 고정 힌트와 일반 답변 미생성도 구분한다.
문서 힌트에도 같은 미판정 안내를 붙이며 후보 발췌를 정답처럼 제공하지 않는다.
DOCUMENT/HINT는 의도이고 sources는 검색 후보이다. 이 필드들의 존재 자체가 검증된 답변을 뜻하지 않는다.
Quasar 재시도 정책에 창시자 생년월일을 묻는 반례와 Zephyr API 네트워크 재시도 정책에
인증 토큰 만료 시간 힌트를 묻는 반례는 실제 lexical 후보를 반환한다.
두 경우의 안내/후보/의도 및 힌트 번호가 DB flush/clear 후에도 복원되는지 검증한다.
토큰 만료 시간이 있는 비선택 문서를 별도로 두어 후보 유무와 선택 범위 준수를 구분한다.
LLM 모드의 비어 있지 않은 후보에 대한 의미적 근거 충족 판단은 프롬프트 지침에 의존한다.
로컬 테스트는 지침 존재와 후보 검색/격리/저장을 검증하며 실제 LLM의 근거 부족 응답 성공을 증명하지 않는다.
실제 LLM에서는 동일한 저관련 후보 반례로 근거 부족 안내·일반 지식 대체 금지·가짜 출처 금지를 별도 평가해야 한다.
실제 LLM 응답 품질·운영 PostgreSQL·AWS·브라우저 검증을 로컬 테스트로 대체하지 않는다.
BM25/청킹/Lambda 선행 커밋 없이 caee492 검색 인터페이스에서 동작한다.
