package com.salessphere.backend.config;

import com.salessphere.backend.entity.Role;
import com.salessphere.backend.entity.User;
import com.salessphere.backend.repository.RoleRepository;
import com.salessphere.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AppInitializer {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationEvent() {
        log.info("Checking database and initializing system metadata...");
        
        // Seed default roles if not present
        List<String> defaultRoles = Arrays.asList("ROLE_ADMINISTRATOR", "ROLE_BUSINESS_ANALYST", "ROLE_REGIONAL_MANAGER", "ROLE_VIEWER");
        for (String roleName : defaultRoles) {
            if (roleRepository.findByName(roleName).isEmpty()) {
                log.info("Seeding role: {}", roleName);
                roleRepository.save(Role.builder().name(roleName).build());
            }
        }

        // Seed default administrator if no users exist in the system
        if (userRepository.count() == 0) {
            log.info("No accounts found. Seeding default Administrator user...");
            Role adminRole = roleRepository.findByName("ROLE_ADMINISTRATOR")
                    .orElseThrow(() -> new IllegalStateException("Admin role not found"));
            
            User defaultAdmin = User.builder()
                    .username("admin")
                    .email("admin@salessphere.com")
                    .password(passwordEncoder.encode("admin123"))
                    .role(adminRole)
                    .build();
            
            userRepository.save(defaultAdmin);
            log.info("Default administrator seeded. Username: 'admin' | Password: 'admin123'");
        }
    }
}
