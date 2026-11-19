package com.rubberduck.domain.chat.service;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import com.rubberduck.domain.chat.entity.ChatMessage;

/** Decides intent before retrieval and generation; never delegates authorization to an LLM. */
@Component
public class ConversationRouter {
    public enum Mode { DIRECT, HINT, DOCUMENT, CLARIFY }
    public record Decision(Mode mode, boolean needsDocuments, String searchQuery, int hintNumber) {}

    public Decision decide(String text, List<ChatMessage> history) {
        return decide(text, history, false);
    }

    // Only a selection supplied in this request provides explicit document context.
    // Persisted search scope must never be passed as this flag.
    public Decision decide(String text, List<ChatMessage> history, boolean explicitDocumentContext) {
        String value = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        String compact = value.replaceAll("\\s+", "");
        ChatMessage last = history.stream().filter(m -> "assistant".equals(m.getSender()))
                .reduce((a, b) -> b).orElse(null);
        String subject = history.stream().filter(m -> "user".equals(m.getSender()))
                .map(ChatMessage::getMessageText).filter(t -> t != null && !missingReferent(t)
                    && (!isFollowUp(t) || hasCode(t)))
                .reduce((a, b) -> b).orElse("");
        boolean followUp = isFollowUp(value);
        boolean hint = contains(value, "힌트", "hint", "정답 말고", "답을 알려주지", "질문으로 유도")
                && !contains(compact, "힌트말고", "힌트없이", "힌트는필요없", "nohint");
        boolean general = compact.matches(".*(문서|자료|강의)(와|과|랑)?(관계없이|상관없이|무관하게).*" )
                || contains(compact, "자료말고일반", "문서말고일반", "일반지식으로", "일반적인질문");
        if (!general && followUp && last != null && "hint".equals(last.getMessageType())
                && !contains(value, "정답", "답을", "직접", "answer")) hint = true;
        boolean document = !general && (explicitDocumentContext || contains(value, "문서", "자료", "강의", "업로드", "pdf", "document")
                || (followUp && last != null && ("DOCUMENT".equals(last.getResponseStrategy())
                    || "INSUFFICIENT_EVIDENCE".equals(last.getResponseStrategy()) || !last.getSources().isEmpty())));
        int number = 1 + (int) history.stream().filter(m -> "hint".equals(m.getMessageType())).count();
        boolean unclear = value.isBlank() || (compact.length() <= 2 && compact.matches("[a-z0-9]+")
                && !List.of("hi", "ai", "os", "db", "js", "ts", "go", "c", "r", "ui", "ux", "ip", "io", "ml", "dl", "ci", "cd", "vm").contains(compact))
                || (subject.isBlank() && (missingReferent(value)
                    || compact.matches("(왜|어떻게|다음|계속|더|모르겠어|안돼|도와줘)(왜|뭐야|설명해|은|요|해|해줘|\\?|!|\\.)*")));
        if (unclear) return new Decision(Mode.CLARIFY, false, text, number);
        String query = !general && followUp && !subject.isBlank() ? subject + "\n" + text : text;
        return new Decision(hint ? Mode.HINT : document ? Mode.DOCUMENT : Mode.DIRECT, document, query, number);
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
