package tn.isetbizerte.pfe.hrbackend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tn.isetbizerte.pfe.hrbackend.common.enums.TypeRole;
import tn.isetbizerte.pfe.hrbackend.modules.user.entity.User;
import tn.isetbizerte.pfe.hrbackend.modules.user.service.AuthenticatedUserResolver;

import java.io.IOException;
import java.util.Optional;

@Component
public class ActiveUserFilter extends OncePerRequestFilter {

    private final AuthenticatedUserResolver authenticatedUserResolver;

    public ActiveUserFilter(AuthenticatedUserResolver authenticatedUserResolver) {
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || path.startsWith("/public/")
                || path.equals("/error")
                || path.startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            Jwt jwt = jwtAuthentication.getToken();
            Optional<User> user = findLocalUser(jwt);

            if (user.isEmpty()) {
                reject(response, "Authenticated user is not registered locally");
                return;
            }

            if (isDeactivatedApplicationUser(user.get())) {
                reject(response, "Account is deactivated");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private Optional<User> findLocalUser(Jwt jwt) {
        return authenticatedUserResolver.resolve(jwt);
    }

    private boolean isDeactivatedApplicationUser(User user) {
        return !Boolean.TRUE.equals(user.getActive()) && user.getRole() != TypeRole.NEW_USER;
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
