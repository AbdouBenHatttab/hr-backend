package tn.isetbizerte.pfe.hrbackend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final KeycloakRoleConverter keycloakRoleConverter;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(KeycloakRoleConverter keycloakRoleConverter, CorsConfigurationSource corsConfigurationSource) {
        this.keycloakRoleConverter = keycloakRoleConverter;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/public/**").permitAll()
                        .requestMatchers("/error").permitAll()
                            .requestMatchers("/api/new-user/**").hasRole("NEW_USER")
                        .requestMatchers("/api/me", "/api/me/**").hasAnyRole("EMPLOYEE", "TEAM_LEADER", "HR_MANAGER")
                        .requestMatchers("/api/employee/**").hasAnyRole("EMPLOYEE", "TEAM_LEADER", "HR_MANAGER")
                        .requestMatchers("/api/leader/**").hasRole("TEAM_LEADER")
                        .requestMatchers("/api/hr/**").hasRole("HR_MANAGER")
                        .requestMatchers("/api/reports/**").hasAnyRole("EMPLOYEE", "TEAM_LEADER", "HR_MANAGER")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth ->
                        oauth.jwt(jwt ->
                                jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(keycloakRoleConverter);
        return converter;
    }
}
