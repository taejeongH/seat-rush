package com.seatrush.ticketservice.domain.auth.service;

import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import com.seatrush.ticketservice.domain.auth.dto.response.UserResponseDto;
import com.seatrush.ticketservice.domain.auth.entity.User;
import com.seatrush.ticketservice.domain.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증된 사용자의 정보를 조회하는 서비스입니다.
 */
@Service
@Transactional(readOnly = true)
public class UserQueryService {

    private final UserRepository userRepository;

    public UserQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 사용자 ID에 해당하는 내 사용자 정보를 조회합니다.
     */
    public UserResponseDto getMyUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return UserResponseDto.from(user);
    }
}
