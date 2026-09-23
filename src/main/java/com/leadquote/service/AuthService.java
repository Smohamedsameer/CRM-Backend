package com.leadquote.service;

import com.leadquote.dto.LoginRequest;
import com.leadquote.dto.LoginResponse;
import com.leadquote.entity.Employee;
import com.leadquote.repository.EmployeeRepository;
import com.leadquote.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final EmployeeRepository employeeRepository;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        } catch (Exception ex) {
            throw new BadCredentialsException("Invalid email or password");
        }

        Employee employee = employeeRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        String token = jwtService.generateToken(employee.getEmail(), Map.of(
                "role", employee.getRole().name(),
                "name", employee.getFullName()
        ));

        return new LoginResponse(token, employee.getFullName(), employee.getRole().name());
    }
}
