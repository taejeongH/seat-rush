package com.seatrush.ticketservice.common.entrytoken;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 요청 처리 전에 entryToken 검증이 필요한 Controller 또는 메서드에 사용합니다.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface EntryTokenRequired {
}
