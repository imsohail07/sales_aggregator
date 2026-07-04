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
        List<String> defaultRoles = Arrays.asList("ROLE_ADMINISTRATOR", "ROLE_BUSINESS_ANALYST", "ROLE_REGIONAL_MANAGER", "ROLE_VIEWER", "ROLE_CEO");
        for (String roleName : defaultRoles) {
            if (roleRepository.findByName(roleName).isEmpty()) {
                log.info("Seeding role: {}", roleName);
                roleRepository.save(Role.builder().name(roleName).build());
            }
        }

        // Seed default Administrator user
        User admin = userRepository.findByEmail("admin@salessphere.com")
                .orElseGet(() -> userRepository.findByUsername("admin").orElse(null));
        if (admin == null) {
            Role adminRole = roleRepository.findByName("ROLE_ADMINISTRATOR")
                    .orElseThrow(() -> new IllegalStateException("Admin role not found"));
            admin = User.builder()
                    .username("admin")
                    .email("admin@salessphere.com")
                    .role(adminRole)
                    .build();
        }
        admin.setPassword(passwordEncoder.encode("Admin@123"));
        userRepository.save(admin);
        log.info("Administrator user seeded: admin@salessphere.com | Admin@123");

        // Seed default Business Analyst user
        User analyst = userRepository.findByEmail("analyst@salessphere.com")
                .orElseGet(() -> userRepository.findByUsername("analyst").orElse(null));
        if (analyst == null) {
            Role analystRole = roleRepository.findByName("ROLE_BUSINESS_ANALYST")
                    .orElseThrow(() -> new IllegalStateException("Business Analyst role not found"));
            analyst = User.builder()
                    .username("analyst")
                    .email("analyst@salessphere.com")
                    .role(analystRole)
                    .build();
        }
        analyst.setPassword(passwordEncoder.encode("Analyst@123"));
        userRepository.save(analyst);
        log.info("Business Analyst user seeded: analyst@salessphere.com | Analyst@123");

        // Seed default CEO user
        User ceo = userRepository.findByEmail("ceo@salessphere.com")
                .orElseGet(() -> userRepository.findByUsername("ceo").orElse(null));
        if (ceo == null) {
            Role ceoRole = roleRepository.findByName("ROLE_CEO")
                    .orElseThrow(() -> new IllegalStateException("CEO role not found"));
            ceo = User.builder()
                    .username("ceo")
                    .email("ceo@salessphere.com")
                    .role(ceoRole)
                    .build();
        }
        ceo.setPassword(passwordEncoder.encode("CEO@123"));
        userRepository.save(ceo);
        log.info("CEO user seeded: ceo@salessphere.com | CEO@123");
    }
}
