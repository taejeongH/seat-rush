package com.seatrush.ticketservice.domain.practice.controller;

import com.seatrush.ticketservice.common.entrytoken.EntryTokenClaims;
import com.seatrush.ticketservice.common.entrytoken.EntryTokenRequestAttribute;
import com.seatrush.ticketservice.common.entrytoken.EntryTokenRequired;
import com.seatrush.ticketservice.common.response.ApiResponse;
import com.seatrush.ticketservice.common.response.status.SuccessCode;
import com.seatrush.ticketservice.domain.practice.dto.PracticePaymentCompleteRequestDto;
import com.seatrush.ticketservice.domain.practice.dto.PracticePaymentPreparationResponseDto;
import com.seatrush.ticketservice.domain.practice.dto.PracticePaymentResponseDto;
import com.seatrush.ticketservice.domain.practice.service.PracticeReservationService;
import com.seatrush.ticketservice.domain.reservation.dto.request.ReservationCreateRequestDto;
import com.seatrush.ticketservice.domain.reservation.dto.response.PaymentRequestResponseDto;
import com.seatrush.ticketservice.domain.reservation.dto.response.ReservationResponseDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/practice")
public class PracticeController {

    private final PracticeReservationService practiceReservationService;

    public PracticeController(PracticeReservationService practiceReservationService) {
        this.practiceReservationService = practiceReservationService;
    }

    @EntryTokenRequired
    @PostMapping("/reservations")
    public ResponseEntity<ApiResponse<ReservationResponseDto>> createReservation(
            @RequestAttribute(EntryTokenRequestAttribute.CLAIMS) EntryTokenClaims claims,
            @Valid @RequestBody ReservationCreateRequestDto request
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.CREATED,
                practiceReservationService.create(request.holdId(), claims)
        );
    }

    @GetMapping("/sessions/{practiceSessionId}/reservations/{reservationId}")
    public ResponseEntity<ApiResponse<ReservationResponseDto>> getReservation(
            @PathVariable String practiceSessionId,
            @Positive @PathVariable Long reservationId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                practiceReservationService.get(practiceSessionId, reservationId, userId)
        );
    }

    @PostMapping("/sessions/{practiceSessionId}/reservations/{reservationId}/payments")
    public ResponseEntity<ApiResponse<PaymentRequestResponseDto>> requestPayment(
            @PathVariable String practiceSessionId,
            @Positive @PathVariable Long reservationId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.ACCEPTED,
                practiceReservationService.requestPayment(practiceSessionId, reservationId, userId)
        );
    }

    @GetMapping("/sessions/{practiceSessionId}/payments/{paymentId}")
    public ResponseEntity<ApiResponse<PracticePaymentPreparationResponseDto>> getPayment(
            @PathVariable String practiceSessionId,
            @PathVariable String paymentId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                practiceReservationService.getPayment(practiceSessionId, paymentId, userId)
        );
    }

    @PostMapping("/sessions/{practiceSessionId}/payments/{paymentId}/complete")
    public ResponseEntity<ApiResponse<PracticePaymentResponseDto>> completePayment(
            @PathVariable String practiceSessionId,
            @PathVariable String paymentId,
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody PracticePaymentCompleteRequestDto request
    ) {
        return ApiResponse.onSuccess(
                SuccessCode.OK,
                practiceReservationService.completePayment(
                        practiceSessionId,
                        paymentId,
                        userId,
                        request.result()
                )
        );
    }

    @DeleteMapping("/sessions/{practiceSessionId}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(
            @PathVariable String practiceSessionId
    ) {
        practiceReservationService.deleteSession(practiceSessionId);
        return ApiResponse.onSuccess(SuccessCode.OK, null);
    }
}
