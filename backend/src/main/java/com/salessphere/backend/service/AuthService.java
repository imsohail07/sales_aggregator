package com.salessphere.backend.service;

import com.salessphere.backend.dto.JwtResponseDto;
import com.salessphere.backend.dto.LoginRequestDto;
import com.salessphere.backend.dto.RegisterRequestDto;
import com.salessphere.backend.entity.Role;
import com.salessphere.backend.entity.User;
import com.salessphere.backend.repository.RoleRepository;
import com.salessphere.backend.repository.UserRepository;
import com.salessphere.backend.security.JwtUtils;
import com.salessphere.backend.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuditLogService auditLogService;

    public JwtResponseDto authenticateUser(LoginRequestDto loginRequest) {
        log.info("Attempting authentication for user: {}", loginRequest.getUsername());
        
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        String role = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElse("ROLE_VIEWER");

        log.info("User {} successfully authenticated", loginRequest.getUsername());
        
        auditLogService.logAction(
                loginRequest.getUsername(),
                "USER_LOGIN",
                "Successfully logged into the system"
        );

        return JwtResponseDto.builder()
                .token(jwt)
                .id(userDetails.getId())
                .username(userDetails.getUsername())
                .email(userDetails.getEmail())
                .role(role)
                .build();
    }

    @Transactional
    public void registerUser(RegisterRequestDto signUpRequest) {
        log.info("Attempting registration for username: {}, email: {}", signUpRequest.getUsername(), signUpRequest.getEmail());

        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            throw new IllegalArgumentException("Error: Username is already taken!");
        }

        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            throw new IllegalArgumentException("Error: Email is already in use!");
        }

        // Map human-readable role to standard ROLE_* authority
        String targetRole = signUpRequest.getRole().trim().toUpperCase().replace(" ", "_");
        if (!targetRole.startsWith("ROLE_")) {
            targetRole = "ROLE_" + targetRole;
        }

        Role role = roleRepository.findByName(targetRole)
                .orElseThrow(() -> new IllegalArgumentException("Error: Role '" + signUpRequest.getRole() + "' is not recognized."));

        User user = User.builder()
                .username(signUpRequest.getUsername())
                .email(signUpRequest.getEmail())
                .password(passwordEncoder.encode(signUpRequest.getPassword()))
                .role(role)
                .build();

        userRepository.save(user);
        log.info("User registered successfully: {}", signUpRequest.getUsername());

        auditLogService.logAction(
                "SYSTEM",
                "USER_REGISTER",
                String.format("Registered user: %s with role: %s", signUpRequest.getUsername(), role.getName())
        );
    }
}
