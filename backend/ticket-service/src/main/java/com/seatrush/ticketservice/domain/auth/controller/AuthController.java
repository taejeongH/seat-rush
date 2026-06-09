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

@Tag(name = "Auth", description = "회원가입 및 로그인 API")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "회원가입", description = "사용자 계정을 생성합니다.")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponseDto>> signup(
            @Valid @RequestBody SignupRequestDto request
    ) {
        return ApiResponse.onSuccess(SuccessCode.CREATED, authService.signup(request));
    }

    @Operation(summary = "로그인", description = "이메일과 비밀번호를 확인하고 accessToken을 발급합니다.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDto>> login(
            @Valid @RequestBody LoginRequestDto request
    ) {
        return ApiResponse.onSuccess(SuccessCode.OK, authService.login(request));
    }
}
