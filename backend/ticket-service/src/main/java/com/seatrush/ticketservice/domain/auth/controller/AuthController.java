package com.seatrush.ticketservice.domain.auth.controller;

import com.seatrush.ticketservice.common.response.ApiResponse;
import com.seatrush.ticketservice.common.response.status.SuccessCode;
import com.seatrush.ticketservice.domain.auth.dto.request.LoginRequestDto;
import com.seatrush.ticketservice.domain.auth.dto.request.SignupRequestDto;
import com.seatrush.ticketservice.domain.auth.dto.response.LoginResponseDto;
import com.seatrush.ticketservice.domain.auth.dto.response.UserResponseDto;
import com.seatrush.ticketservice.domain.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 신규 회원가입 처리 및 로그인 세션 토큰(JWT Access Token) 발급을 수행하는 컨트롤러입니다.
 */
@Tag(name = "Auth", description = "회원가입 및 로그인 API")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 신규 사용자를 시스템에 등록(회원가입)합니다.
     * 비밀번호는 내부적으로 단방향 해시 암호화되어 보관됩니다.
     *
     * @param request 이메일, 비밀번호, 닉네임 정보 Dto
     * @return 등록 완료된 사용자 기본 정보 Dto
     */
    @Operation(summary = "회원가입", description = "사용자 계정을 생성합니다.")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponseDto>> signup(
            @Valid @RequestBody SignupRequestDto request
    ) {
        return ApiResponse.onSuccess(SuccessCode.CREATED, authService.signup(request));
    }

    /**
     * 입력된 사용자 인증정보(이메일, 비밀번호)를 대조하고 검증 성공 시 
     * 인증 수단인 JWT Access Token을 생성하여 반환합니다.
     *
     * @param request 이메일 및 비밀번호 Dto
     * @return 로그인 성공 여부 및 발급된 Access Token Dto
     */
    @Operation(summary = "로그인", description = "이메일과 비밀번호를 확인하고 accessToken을 발급합니다.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDto>> login(
            @Valid @RequestBody LoginRequestDto request
    ) {
        return ApiResponse.onSuccess(SuccessCode.OK, authService.login(request));
    }
}
