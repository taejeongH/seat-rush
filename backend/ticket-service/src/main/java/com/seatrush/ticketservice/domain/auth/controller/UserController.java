package com.seatrush.ticketservice.domain.auth.controller;

import com.seatrush.ticketservice.common.response.ApiResponse;
import com.seatrush.ticketservice.common.response.status.SuccessCode;
import com.seatrush.ticketservice.domain.auth.dto.response.UserResponseDto;
import com.seatrush.ticketservice.domain.auth.service.UserQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인한 사용자의 개인 정보 조회를 처리하는 컨트롤러입니다.
 */
@Tag(name = "User", description = "사용자 정보 API")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserQueryService userQueryService;

    public UserController(UserQueryService userQueryService) {
        this.userQueryService = userQueryService;
    }

    /**
     * 현재 요청을 보낸 본인의 회원 기본 정보(이메일, 닉네임, 권한 등)를 조회합니다.
     * API Gateway가 토큰에서 해독하여 헤더에 태워준 사용자 ID(X-User-Id)를 이용합니다.
     *
     * @param userId API Gateway 헤더(X-User-Id)로부터 주입받은 사용자 식별 ID
     * @return 사용자 기본 프로필 정보 Dto
     */
    @Operation(summary = "내 사용자 정보 조회", description = "accessToken으로 인증된 사용자 정보를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponseDto>> getMyUser(
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                userQueryService.getMyUser(userId)
        );
    }
}

