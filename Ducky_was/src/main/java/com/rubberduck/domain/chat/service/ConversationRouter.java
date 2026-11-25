package com.rubberduck.domain.chat.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import com.rubberduck.domain.chat.entity.ChatMessage;

/** Decides intent before retrieval and generation; never delegates authorization to an LLM. */
@Component
public class ConversationRouter {
    private static final Pattern REVIEW_REQUEST = Pattern.compile(
            "(?:피드백|검토|리뷰|첨삭)(?:을|좀|만)?(?:해줘|해주세요|해줄래|부탁해|부탁드립니다|줘|주세요)");
    private static final Pattern BARE_REVIEW_REQUEST = Pattern.compile(
            "(?:(?:그럼|그러면),?)?(?:이거|그거|이것|그것)?" + REVIEW_REQUEST.pattern() + "[?!.]*");
    private static final Pattern EXERCISE_REQUEST = Pattern.compile(
            "(?:문제|연습|과제|숙제|예제)(?:를|을|도)?.*(?:풀어줘|풀어주세요|풀어줄래|해결해줘|해결해주세요)");
    private static final Pattern DEBUG_REPORT = Pattern.compile(
            "(?:오류|에러|error|exception)(?:가|이)?(?:나(?:요|는데|서|고|요)?|났|남|떠|뜹|발생)"
            + "|(?:코드|반복문|함수|프로그램|스크립트)(?:가|이|는|를|을|에서)?.{0,25}(?:작동안|동작안|실행안|안돼|안되|안나와)"
            + "|(?:러버덕(?:처럼|으로)|디버깅(?:을|좀)?(?:도와|해보|같이))");
    private static final Pattern DEBUG_REPORT_ENGLISH = Pattern.compile(
            "\\b(?:i (?:get|got|keep getting|am getting)|i'm getting|my (?:code|script|program) (?:throws|raises))\\b.*\\b\\w*(?:error|exception)\\b");
    private static final Pattern DEBUG_OBSERVATION = Pattern.compile(
            ".*(?:길이|인덱스|값|출력|반환|입력|범위|조건|줄|length|index|output|result).*(?:[0-9]|같아|달라|나와|나왔|바뀌|없어|empty|null|undefined).*"
            + "|[-+]?\\d+(?:[.,]\\d+)?(?:이야|예요|입니다|요)?[.!]?|(?:맞아|아니|아니야|모르겠어|모르겠어요)[.!]?");
    private static final Pattern CODE_EXPLANATION = Pattern.compile("^(?:이|그)코드(?:는|가|에서)?왜(?:오류|에러)(?:가|이)?나[?？]");

    public enum Mode { DIRECT, HINT, DOCUMENT, REVIEW, CLARIFY, DEBUG }
    public enum SimpleIntent { IDENTITY, GREETING, THANKS, HINT_FEATURE }
    public record Decision(Mode mode, boolean needsDocuments, String searchQuery, int hintNumber, SimpleIntent simpleIntent) {}

    public Decision decide(String text, List<ChatMessage> history) {
        return decide(text, history, false);
    }

    // Only a selection supplied in this request provides explicit document context.
    // Persisted search scope must never be passed as this flag.
    public Decision decide(String text, List<ChatMessage> history, boolean explicitDocumentContext) {
        String value = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        String compact = value.replaceAll("\\s+", "");
        int number = 1 + (int) history.stream().filter(m -> "hint".equals(m.getMessageType())).count();
        SimpleIntent simple = simpleIntent(value);
        if (simple != null) return new Decision(Mode.DIRECT, false, text, number, simple);

        ChatMessage last = history.stream().filter(m -> "assistant".equals(m.getSender()))
                .reduce((a, b) -> b).orElse(null);
        String subject = history.stream().filter(m -> "user".equals(m.getSender()))
                .map(ChatMessage::getMessageText).filter(t -> t != null && !missingReferent(t)
                    && !BARE_REVIEW_REQUEST.matcher(t.replaceAll("\\s+", "")).matches()
                    && (!isFollowUp(t) || hasCode(t)))
                .reduce((a, b) -> b).orElse("");
        boolean followUp = isFollowUp(value);
        boolean directRequested = contains(compact, "힌트말고", "힌트없이", "힌트는필요없", "nohint", "직접설명", "직접답해", "정답만알려");
        boolean review = !directRequested && REVIEW_REQUEST.matcher(compact).find();
        boolean hint = !directRequested && !review && (contains(value, "힌트", "hint", "정답 말고", "답을 알려주지", "질문으로 유도")
                || contains(compact, "단서하나만", "단서만줘", "단서만알려", "같이생각해보자")
                || EXERCISE_REQUEST.matcher(compact).find()
                || compact.matches(".*(?:스스로|혼자|직접).*(?:풀어보고싶|찾아보고싶|풀고싶|찾고싶|찾게도와|풀게도와).*"));
        boolean reviewWithoutTarget = BARE_REVIEW_REQUEST.matcher(compact).matches();
        boolean general = compact.matches(".*(문서|자료|강의)(와|과|랑)?(관계없이|상관없이|무관하게).*" )
                || contains(compact, "자료말고일반", "문서말고일반", "일반지식으로", "일반적인질문");
        boolean continuesHint = missingReferent(value)
                || compact.matches("(?:그럼|그러면)?(?:다음|계속|더|다시|아직모르겠)(?:은|요|어|어요|해|해줘|설명해줘|[?!.])*");
        if (!general && !directRequested && !review && last != null && "hint".equals(last.getMessageType()) && continuesHint) {
            hint = true;
        }
        boolean document = !general && (explicitDocumentContext || contains(value, "문서", "자료", "강의", "업로드", "pdf", "document")
                || ((followUp || reviewWithoutTarget) && last != null && ("DOCUMENT".equals(last.getResponseStrategy())
                    || "INSUFFICIENT_EVIDENCE".equals(last.getResponseStrategy()) || !last.getSources().isEmpty())));
        boolean continuesDebug = last != null && Mode.DEBUG.name().equals(last.getResponseStrategy())
                && DEBUG_OBSERVATION.matcher(compact).matches();
        boolean unclear = value.isBlank() || (compact.length() <= 2 && compact.matches("[a-z0-9]+")
                && !continuesDebug && !List.of("hi", "ai", "os", "db", "js", "ts", "go", "c", "r", "ui", "ux", "ip", "io", "ml", "dl", "ci", "cd", "vm").contains(compact))
                || (subject.isBlank() && !continuesDebug && (missingReferent(value) || (reviewWithoutTarget && !document)
                    || compact.matches("(왜|어떻게|다음|계속|더|모르겠어|안돼|도와줘)(왜|뭐야|설명해|은|요|해|해줘|\\?|!|\\.)*")));
        if (unclear) return new Decision(Mode.CLARIFY, false, text, number, null);
        String query = !general && followUp && !subject.isBlank() ? subject + "\n" + text : text;
        boolean conceptQuestion = contains(compact, "뭐야", "뭐예요", "뭔가요", "무엇", "뜻이", "의미", "왜발생", "왜나는거")
                || value.matches("^(?:what is|what does|what's|define)\\b.*") || CODE_EXPLANATION.matcher(compact).find();
        if (!directRequested && !review && !document && !conceptQuestion && (DEBUG_REPORT.matcher(compact).find()
                || DEBUG_REPORT_ENGLISH.matcher(value).find() || continuesDebug)) {
            return new Decision(Mode.DEBUG, false, text, number, null);
        }
        return new Decision(hint ? Mode.HINT : review ? Mode.REVIEW : document ? Mode.DOCUMENT : Mode.DIRECT,
                document, query, number, null);
    }

    private SimpleIntent simpleIntent(String text) {
        String compact = text.replaceFirst("^(?:(?:그럼|그러면|근데|그런데|잠깐|혹시)[, ]+)+", "")
                .replaceAll("[\\s?!.,。！？]", "");
        if (compact.matches("(?:너(?:는|가)?|당신(?:은)?|ducky|덕키|러버덕)(?:누구(?:야|예요|에요|세요|인가요)?|뭐(?:야|예요)|뭐할수있(?:어|나요)|무슨역할이야|정체가뭐야)")) {
            return SimpleIntent.IDENTITY;
        }
        if (compact.matches("안녕(?:해|하세요|하십니까)?|hi|hello|반가워(?:요)?")) return SimpleIntent.GREETING;
        if (compact.matches("(?:고마워|고마웠어|고마워요|감사해|감사해요|감사합니다|고맙습니다|thx|thanks|thankyou)")) {
            return SimpleIntent.THANKS;
        }
        if (compact.matches("힌트(?:기능|모드|버튼)?(?:이|가|은|는|을|를)?"
                + "(?:뭐야|뭐예요|뭔가요|무엇인가요|무슨기능이야|어떻게써|어떻게쓰나요|어떻게사용해|사용법알려줘|설명해줘)"
                + "(?:기능(?:자체)?만(?:짧게)?(?:설명해줘|알려줘))?")) {
            return SimpleIntent.HINT_FEATURE;
        }
        return null;
    }

    private boolean contains(String text, String... words) {
        for (String word : words) if (text.contains(word)) return true;
        return false;
    }

    private boolean isFollowUp(String text) {
        return text.replaceAll("\\s+", "").matches("(그거|그건|이거|이건|그것|이코드|그코드|그럼|그러면|왜|다음|계속|더|다시|예시|어떻게|근거|아직모르겠).*" );
    }

    private boolean missingReferent(String text) {
        // Code can supply a referent; a line break by itself cannot.
        if (hasCode(text)) return false;
        String value = text.toLowerCase(Locale.ROOT).replaceAll("[\\s?!.,。！？]", "");
        return value.matches("(이거|그거|이건|그건|이것|그것)(는|은|가|이|왜|안|돼|되|요|어떻게|설명해|뭐야|해줘|작동|하지|않아)*")
                || value.matches("(이|그)코드(는|가|에서)?(왜)?.*(오류|에러|안돼|안되).*" )
                || value.matches("(다음)?(힌트|hint)(좀|만|를|주세요|줘|줘요|주라|부탁해|요)*");
    }

    private boolean hasCode(String text) {
        return text.contains("```") || text.matches("(?s).*`[^`]+`.*")
                || text.matches("(?s).*\n.*[=;{}()].*");
    }
}
