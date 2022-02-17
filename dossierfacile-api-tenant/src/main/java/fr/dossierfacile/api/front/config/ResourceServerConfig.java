package fr.dossierfacile.api.front.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;


@EnableWebSecurity
@RequiredArgsConstructor
public class ResourceServerConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .headers()
                .contentTypeOptions()
                .and()
                .xssProtection()
                .and()
                .cacheControl()
                .and()
                .httpStrictTransportSecurity()
                .maxAgeInSeconds(31536000)
                .includeSubDomains(true)
                .and()
                .contentSecurityPolicy("script-src 'self'")
                .and()
                .and()
                .formLogin()
                .disable()
                .httpBasic()
                .disable()
                .csrf()
                .disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .cors()
                .and()
                .authorizeRequests()
                .antMatchers("/swagger-ui/**", "/swagger-resources/**", "/v2/api-docs", "/api/register/account", "/api/register/confirmAccount/**",
                        "/api/auth/**", "/api/user/forgotPassword", "/api/user/createPassword/**",
                        "/api/document/**", "/api/file/download/**", "/api/application/full/**", "/api/application/light/**", "/api/application/fullPdf/**", "/api/tenant/property/**","/actuator/health")
                .permitAll()
                .antMatchers("/api-partner/**").hasAuthority("SCOPE_api-partner")
                .antMatchers("/dfc/tenant/profile").hasAuthority("SCOPE_dfc")
                .anyRequest().hasAuthority("SCOPE_dossier")
                .and()
                .oauth2ResourceServer()
                .jwt();
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Collections.singletonList("*"));
        configuration.setAllowCredentials(true);
        configuration.setAllowedHeaders(Arrays.asList("Access-Control-Allow-Headers", "Access-Control-Allow-Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers", "Origin", "Cache-Control", "Content-Type", "Authorization"));
        configuration.setAllowedMethods(Arrays.asList("DELETE", "GET", "POST", "PATCH", "PUT"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}