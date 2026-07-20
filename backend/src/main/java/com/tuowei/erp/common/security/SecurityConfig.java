package com.tuowei.erp.common.security;

import com.tuowei.erp.common.web.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.idempotency.IdempotencyFilter;
import com.tuowei.erp.common.web.MdcTraceFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            MdcTraceFilter mdcTraceFilter,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            IdempotencyFilter idempotencyFilter,
            Environment environment
    ) throws Exception {
        List<String> publicEndpoints = new ArrayList<>(List.of(
                "/api/health",
                "/api/auth/login",
                "/api/auth/refresh",
                "/api/auth/logout",
                "/actuator/health"
        ));
        if (environment.getProperty("erp.security.public-api-docs-enabled", Boolean.class, false)) {
            publicEndpoints.addAll(List.of(
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
            ));
        }

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource(environment)))
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentTypeOptions(contentTypeOptions -> {
                        })
                        .frameOptions(frameOptions -> frameOptions.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .preload(true)
                                .maxAgeInSeconds(31536000))
                        .referrerPolicy(referrerPolicy -> referrerPolicy
                                .policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            objectMapper.writeValue(response.getWriter(), new ApiResponse<>("401", "认证失败", null));
                        })
                        .accessDeniedHandler((request, response, ex) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            objectMapper.writeValue(response.getWriter(), new ApiResponse<>("403", "权限不足", null));
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicEndpoints.toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(mdcTraceFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtAuthenticationFilter, MdcTraceFilter.class)
                .addFilterAfter(idempotencyFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    FilterRegistrationBean<MdcTraceFilter> mdcTraceFilterRegistration(MdcTraceFilter filter) {
        FilterRegistrationBean<MdcTraceFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<IdempotencyFilter> idempotencyFilterRegistration(IdempotencyFilter filter) {
        FilterRegistrationBean<IdempotencyFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(Environment environment) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(parseCsvProperty(environment, "erp.security.cors.allowed-origins"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        configuration.setExposedHeaders(List.of(
                "Authorization",
                "Idempotency-Replayed",
                MdcTraceFilter.TRACE_ID_HEADER,
                HttpHeaders.CONTENT_DISPOSITION
        ));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private List<String> parseCsvProperty(Environment environment, String key) {
        String rawValue = environment.getProperty(key, "");
        if (rawValue.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(rawValue.split(",", -1))
                .map(String::trim)
                .map(value -> requireCsvValue(key, value))
                .toList();
    }

    private String requireCsvValue(String key, String value) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException(key + " 包含空配置项");
        }
        if ("*".equals(value)) {
            throw new IllegalArgumentException(key + " 禁止使用 *");
        }
        return value;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
