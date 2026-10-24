package com.capstone.Capstone.project.config;

import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Browsing endpoints are public; cart, wishlist, writers, orders and payments need a bearer JWT.
 * The API is stateless and uses no cookies, so CSRF protection does not apply.
 */
@Configuration
public class SecurityConfig {

	private static final String[] PUBLIC_GET = {
			"/catalog/**", "/categories", "/books/**", "/authors/**",
			"/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
	};

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth -> oauth
						.jwt(Customizer.withDefaults())
						.authenticationEntryPoint(unauthorized()))
				.exceptionHandling(e -> e
						.authenticationEntryPoint(unauthorized())
						.accessDeniedHandler(forbidden()));
		return http.build();
	}

	private static AuthenticationEntryPoint unauthorized() {
		return (request, response, ex) -> writeError(response, 401, "UNAUTHORIZED",
				"Authentication is required");
	}

	private static AccessDeniedHandler forbidden() {
		return (request, response, ex) -> writeError(response, 403, "FORBIDDEN", "Access denied");
	}

	private static void writeError(jakarta.servlet.http.HttpServletResponse response, int status, String code,
			String message) throws java.io.IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
	}
}
