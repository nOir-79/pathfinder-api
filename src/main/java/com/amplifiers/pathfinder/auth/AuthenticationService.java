package com.amplifiers.pathfinder.auth;

import com.amplifiers.pathfinder.config.JwtService;
import com.amplifiers.pathfinder.entity.token.Token;
import com.amplifiers.pathfinder.entity.token.TokenRepository;
import com.amplifiers.pathfinder.entity.token.TokenType;
import com.amplifiers.pathfinder.entity.user.User;
import com.amplifiers.pathfinder.entity.user.UserRepository;
import com.amplifiers.pathfinder.exception.AuthenticationException;
import com.amplifiers.pathfinder.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final UserRepository repository;
    private final TokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;


    public AuthenticationResponse register(RegisterRequest request) {
        if (request.getPassword().length() < 8) {
            throw new AuthenticationException("Password must be at least 8 characters long.");
        }

        // check if user with email already exists
        var existing_user = repository.findByEmail(request.getEmail());

        if(!existing_user.isEmpty()) {
            throw new AuthenticationException("User with this email already exists. Try another one.");
        }

        var user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .build();
        var savedUser = repository.save(user);
        var accessToken = jwtService.generateToken(user);
        var refreshToken = jwtService.generateRefreshToken(user);

        saveUserToken(user, accessToken, TokenType.ACCESS);
        saveUserToken(user, refreshToken, TokenType.REFRESH);
        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .id(savedUser.getId())
                .firstName(savedUser.getFirstName())
                .lastName(savedUser.getLastName())
                .email(savedUser.getEmail())
                .role(user.getRole())
                .build();
    }

    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        removeAllExpiredTokens();
        if(request.getEmail() == null || request.getPassword() == null)
            throw new AuthenticationException("Email and password are required.");

        var user = repository.findByEmail(request.getEmail()).orElseThrow(() -> new ResourceNotFoundException("No user found with this email. Please try again."));

        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        } catch (BadCredentialsException e) {
            throw new AuthenticationException("Incorrect email or password. Please try again.");
        } catch (AuthenticationException e) {
            throw new AuthenticationException("Authentication failed. Please try again.");
        }

        var accessToken = jwtService.generateToken(user);
        var refreshToken = jwtService.generateRefreshToken(user);
        revokeAllUserAccessTokens(user);
        saveUserToken(user, accessToken, TokenType.ACCESS);
        saveUserToken(user, refreshToken, TokenType.REFRESH);

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }

    private void saveUserToken(User user, String jwtToken, TokenType tokenType) {
        var token = Token.builder()
                .user(user)
                .token(jwtToken)
                .tokenType(tokenType)
                .revoked(false)
                .build();

        try {
            tokenRepository.save(token);
        } catch (DataIntegrityViolationException E) {
            System.out.println("Error msg is : ");
            System.out.println(E.getMessage());
        }
    }

    private void revokeAllUserAccessTokens(User user) {
        var validUserTokens = tokenRepository.findAllValidAccessTokenByUser(user.getId());
        if (validUserTokens.isEmpty())
            return;
        validUserTokens.forEach(token -> {
            token.setRevoked(true);
        });
        tokenRepository.saveAll(validUserTokens);
    }

    private void removeAllExpiredTokens() {
        var tokens = tokenRepository.findAll();
        if (tokens.isEmpty())
            return;
        tokens.forEach(token -> {
            try {
                if (jwtService.isTokenExpired(token.getToken())) {
                    tokenRepository.delete(token);
                }
            } catch (ExpiredJwtException E) {
                System.out.println(E.getMessage());
                tokenRepository.delete(token);
            }
        });
    }

    public void refreshToken(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
//        removeAllExpiredTokens();
        final String refreshToken = getCookieRefreshToken(request);
        final String userEmail;

        if (refreshToken == null) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            return;
        }

        userEmail = jwtService.extractUsername(refreshToken);
        if (userEmail != null) {
            var user = this.repository.findByEmail(userEmail).orElseThrow(() -> new ResourceNotFoundException("No user found with this email. Please try again."));
            if (jwtService.isTokenValid(refreshToken, user)) {
                var accessToken = jwtService.generateToken(user);

//                revokeAllUserAccessTokens(user);
                removeAllExpiredTokens();

                saveUserToken(user, accessToken, TokenType.ACCESS);
                var authResponse = AuthenticationResponse.builder()
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .build();
                new ObjectMapper().writeValue(response.getOutputStream(), authResponse);
            }
        }

        System.out.println("Token refreshed for " + userEmail);
    }

    public static String getCookieRefreshToken(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookie.getName().equals("refresh_token")) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
