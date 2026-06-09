package com.seatrush.ticketservice.domain.auth.service;

import com.seatrush.ticketservice.common.exception.CustomException;
import com.seatrush.ticketservice.common.response.status.ErrorCode;
import com.seatrush.ticketservice.common.security.jwt.JwtTokenProvider;
import com.seatrush.ticketservice.domain.auth.dto.request.LoginRequestDto;
import com.seatrush.ticketservice.domain.auth.dto.request.SignupRequestDto;
import com.seatrush.ticketservice.domain.auth.dto.response.LoginResponseDto;
import com.seatrush.ticketservice.domain.auth.dto.response.UserResponseDto;
import com.seatrush.ticketservice.domain.auth.entity.User;
import com.seatrush.ticketservice.domain.auth.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * 회원가입과 로그인을 처리하는 인증 서비스입니다.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * 이메일 중복을 확인하고 비밀번호를 암호화하여 사용자를 생성합니다.
     */
    @Transactional
    public UserResponseDto signup(SignupRequestDto request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.create(
                email,
                passwordEncoder.encode(request.password()),
                request.name().trim()
        );

        try {
            return UserResponseDto.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS, exception);
        }
    }

    /**
     * 사용자 자격 증명을 확인하고 RSA 서명된 accessToken을 발급합니다.
     */
    @Transactional(readOnly = true)
    public LoginResponseDto login(LoginRequestDto request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = jwtTokenProvider.createAccessToken(user);
        return LoginResponseDto.of(accessToken, jwtTokenProvider.getAccessTokenExpirationSeconds());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
