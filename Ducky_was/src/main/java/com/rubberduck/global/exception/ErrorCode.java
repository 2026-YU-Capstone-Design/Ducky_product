package com.rubberduck.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_TOKEN(401, "유효하지 않은 토큰입니다"),
    EXPIRED_TOKEN(401, "만료된 토큰입니다"),
    UNAUTHORIZED(401, "인증이 필요합니다"),
    USER_NOT_FOUND(404, "사용자를 찾을 수 없습니다"),
    DUPLICATE_EMAIL(409, "이미 사용 중인 이메일입니다"),
    DUPLICATE_LOGIN_ID(409, "이미 사용 중인 로그인 ID입니다"),
    INVALID_PASSWORD(400, "비밀번호가 올바르지 않습니다"),
    DEVICE_NOT_FOUND(404, "디바이스를 찾을 수 없습니다"),
    INVALID_REQUEST(400, "요청 값이 올바르지 않습니다"),
    SESSION_NOT_FOUND(404, "세션을 찾을 수 없습니다"),
    DOCUMENT_NOT_FOUND(404, "문서를 찾을 수 없습니다"),
    CHAT_RESPONSE_UNAVAILABLE(503, "채팅 응답 서비스를 사용할 수 없습니다"),
    INTERNAL_SERVER_ERROR(500, "서버 오류가 발생했습니다");

    private final int status;
    private final String message;
}
