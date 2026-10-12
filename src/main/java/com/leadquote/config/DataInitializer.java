package com.leadquote.config;

import com.leadquote.entity.Employee;
import com.leadquote.entity.EmployeeRole;
import com.leadquote.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the default admin on startup if it doesn't exist yet.
 * Flyway is disabled in this project, so nothing else seeds the employees table.
 *
 * Override with Railway variables ADMIN_EMAIL / ADMIN_PASSWORD if you like.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        String email = System.getenv().getOrDefault("ADMIN_EMAIL", "admin@company.com");
        String password = System.getenv().getOrDefault("ADMIN_PASSWORD", "Admin@123");

        if (employeeRepository.existsByEmailIgnoreCase(email)) {
            return;
        }

        employeeRepository.save(Employee.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .fullName("Admin")
                .role(EmployeeRole.ADMIN)
                .active(true)
                .build());

        log.info("Default admin created: {}", email);
    }
}