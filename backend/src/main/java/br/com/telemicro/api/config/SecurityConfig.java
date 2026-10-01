package br.com.telemicro.api.config;

import br.com.telemicro.api.auth.AdminUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    SecretKey jwtKey(SecurityProperties properties, Environment environment) {
        byte[] bytes;
        if (properties.jwtSecret().isBlank()) {
            if (!environment.matchesProfiles("local")) {
                throw new IllegalStateException("Configure JWT_SECRET em Base64, com pelo menos 32 bytes aleatórios.");
            }
            bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            org.slf4j.LoggerFactory.getLogger(SecurityConfig.class)
                    .info("Chave JWT temporária local: tokens deixam de valer ao reiniciar a API.");
        } else {
            try { bytes = Base64.getDecoder().decode(properties.jwtSecret()); }
            catch (IllegalArgumentException exception) { throw new IllegalStateException("JWT_SECRET deve usar Base64 válido."); }
            if (bytes.length < 32) throw new IllegalStateException("JWT_SECRET exige pelo menos 32 bytes aleatórios.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean JwtEncoder jwtEncoder(SecretKey key) {
        return NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean JwtDecoder jwtDecoder(SecretKey key, SecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> required = jwt -> {
            if (jwt.getExpiresAt() == null || jwt.getIssuedAt() == null || jwt.getSubject() == null ||
                    jwt.getAudience() == null || !jwt.getAudience().contains(properties.audience())) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token inválido.", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ZERO),
                new JwtIssuerValidator(properties.issuer()), required));
        return decoder;
    }

    @Bean CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        List<String> origins = properties.allowedOrigins().stream().filter(origin -> !origin.isBlank()).toList();
        if (origins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException("CORS exige origens explícitas, sem wildcard.");
        }
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        cors.setExposedHeaders(List.of("Retry-After"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder decoder, AdminUserRepository users,
            SecurityProperties properties, RequestRateLimiter limiter, @Value("${app.demo-enabled:false}") boolean demoEnabled)
            throws Exception {
        var entryPoint = (org.springframework.security.web.AuthenticationEntryPoint) (request, response, exception) -> {
            response.setHeader("WWW-Authenticate", "Bearer");
            SecurityProblem.write(response, HttpStatus.UNAUTHORIZED, "Autenticação necessária ou token inválido.");
        };
        var denied = (org.springframework.security.web.access.AccessDeniedHandler) (request, response, exception) ->
                SecurityProblem.write(response, HttpStatus.FORBIDDEN, "Você não tem permissão para esta operação.");
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/api/v1/services").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/budgets").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/**").hasAnyRole("ADMIN", "DEMO")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource.authenticationEntryPoint(entryPoint).accessDeniedHandler(denied)
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(token -> {
                            UUID id;
                            try { id = UUID.fromString(token.getSubject()); }
                            catch (IllegalArgumentException exception) { throw new BadCredentialsException("Token inválido."); }
                            var user = users.findById(id).filter(candidate -> candidate.active() &&
                                    (!candidate.role().equals("DEMO") || demoEnabled))
                                    .orElseThrow(() -> new BadCredentialsException("Token inválido."));
                            // Permissões vêm do banco atual, não de um papel antigo gravado no token.
                            return new JwtAuthenticationToken(token,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + user.role())), user.id().toString());
                        })))
                .addFilterBefore(new RateLimitFilter(limiter, properties), BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
