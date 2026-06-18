package com.seatrush.queueservice.common.response.status;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    // COMMON
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON001", "?˜ëª»???…ë ¥ê°’ì…?ˆë‹¤."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON002", "?œë²„ ?´ë? ?ëŸ¬ê°€ ë°œìƒ?ˆìŠµ?ˆë‹¤."),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON003", "ì¡´ì¬?˜ì? ?ŠëŠ” ë¦¬ì†Œ?¤ì…?ˆë‹¤."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON004", "ì§€?í•˜ì§€ ?ŠëŠ” HTTP ë©”ì„œ?œì…?ˆë‹¤."),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "COMMON005", "?´ë? ì¡´ì¬?˜ëŠ” ë¦¬ì†Œ?¤ì…?ˆë‹¤."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON006", "?˜ëª»???”ì²­?…ë‹ˆ??"),

    // AUTH
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH003", "?¸ì¦???„ìš”?©ë‹ˆ??"),

    // QUEUE
    QUEUE_ENTRY_NOT_FOUND(HttpStatus.NOT_FOUND, "QUEUE001", "?€ê¸°ì—´ ì§„ì… ?•ë³´ë¥?ì°¾ì„ ???†ìŠµ?ˆë‹¤."),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "QUEUE002", "?€ê¸°ì—´ ?Œì°¨ ?•ë³´ë¥?ì°¾ì„ ???†ìŠµ?ˆë‹¤."),
    QUEUE_NOT_OPEN(HttpStatus.CONFLICT, "QUEUE003", "?€ê¸°ì—´??ì§„ì…?????†ëŠ” ?Œì°¨ ?íƒœ?…ë‹ˆ??"),
    INVALID_PRACTICE_SESSION_TIME(HttpStatus.BAD_REQUEST, "QUEUE004", "?°ìŠµ ?¸ì…˜ ?¤í”ˆ ?œê°?€ ì¢…ë£Œ ?œê°ë³´ë‹¤ ë¹¨ë¼???©ë‹ˆ??"),

    // ENTRY_TOKEN
    ENTRY_NOT_ALLOWED(HttpStatus.CONFLICT, "ENTRY_TOKEN001", "?„ì§ ì¢Œì„ ? íƒ ?¨ê³„???…ì¥?????†ìŠµ?ˆë‹¤."),
    INVALID_ENTRY_SLOT_RELEASE_EVENT(HttpStatus.BAD_REQUEST, "ENTRY_TOKEN002", "?…ì¥ ?¬ë¡¯ ë°˜í™˜ ?´ë²¤???•ë³´ê°€ ? íš¨?˜ì? ?ŠìŠµ?ˆë‹¤.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSuccess() {
        return false;
    }
}
