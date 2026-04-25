package blps.itmo.config;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Конфигурация Spring Security для сервиса заявок на штрафные санкции.
 *
 * <h3>Политика разграничения доступа</h3>
 * <ul>
 * <li>Аутентификация — HTTP Basic. Учётные записи хранятся в БД
 * (таблица {@code users}), пароли — BCrypt.</li>
 * <li>Авторизация — RBAC + набор привилегий (см. {@code privileges},
 * {@code role_privileges}). Разграничение выполняется на уровне
 * методов контроллеров через
 * {@link org.springframework.security.access.prepost.PreAuthorize}.</li>
 * <li>Сессии — stateless: каждый запрос несёт заголовок Basic.</li>
 * <li>CSRF выключен: это чистое REST API без cookie-based auth.</li>
 * </ul>
 *
 * <h3>Публичные endpoints</h3>
 * <ul>
 * <li>{@code /swagger-ui/**}, {@code /api-docs/**}, {@code /v3/api-docs/**} —
 * документация API;</li>
 * <li>{@code /error} — стандартный error endpoint Spring;</li>
 * <li>{@code /actuator/health} — health check.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            DaoAuthenticationProvider authenticationProvider) throws Exception {
        AuthenticationEntryPoint authenticationEntryPoint = restAuthenticationEntryPoint();
        AccessDeniedHandler accessDeniedHandler = restAccessDeniedHandler();

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/api-docs",
                                "/api-docs/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/error")
                        .permitAll()
                        .anyRequest().authenticated())
                .httpBasic(httpBasic -> httpBasic.authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));

        return http.build();
    }

    // обработчик случая “пользователь не аутентифицирован”
    private AuthenticationEntryPoint restAuthenticationEntryPoint() {
        return (HttpServletRequest request, HttpServletResponse response,
                AuthenticationException authException) -> writeJsonError(
                        response,
                        HttpStatus.UNAUTHORIZED,
                        "Authentication required: " + authException.getMessage());
    }

    // обработчик случая “пользователь аутентифицирован, но прав не хватает”
    private AccessDeniedHandler restAccessDeniedHandler() {
        return (HttpServletRequest request, HttpServletResponse response,
                org.springframework.security.access.AccessDeniedException accessDeniedException) -> writeJsonError(
                        response,
                        HttpStatus.FORBIDDEN,
                        "Access denied: " + accessDeniedException.getMessage());
    }

    private void writeJsonError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String body = "{\"status\":" + status.value()
                + ",\"error\":\"" + status.getReasonPhrase()
                + "\",\"message\":\"" + escape(message) + "\"}";
        response.getWriter().write(body);
    }

    private String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
