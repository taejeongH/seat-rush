package com.seatrush.ticketservice.common.health;

import com.seatrush.ticketservice.common.response.ApiResponse;
import com.seatrush.ticketservice.common.response.status.SuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health", description = "서비스 상태 확인 API")
@RestController
public class HealthController {

    @Operation(
            summary = "헬스 체크",
            description = "Ticket Service가 정상적으로 실행 중인지 확인합니다."
    )
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<HealthResponse>> health() {
        return ApiResponse.onSuccess(SuccessCode.OK, new HealthResponse("UP"));
    }
}
