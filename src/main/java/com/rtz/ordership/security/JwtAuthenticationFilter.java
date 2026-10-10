package com.rtz.ordership.security;

import com.rtz.ordership.entity.StoreMember;
import com.rtz.ordership.entity.User;
import com.rtz.ordership.repository.StoreMemberRepository;
import com.rtz.ordership.repository.UserRepository;
import com.rtz.ordership.tenant.StoreContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Autentica con el JWT y resuelve la tienda del request (header X-Store-Id) con el rol del usuario en ella.
 * Corre antes de que se abra la sesión de Hibernate, que filtra por esa tienda.
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String STORE_HEADER = "X-Store-Id";

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;
    private final StoreMemberRepository storeMemberRepository;

    public JwtAuthenticationFilter(JwtProvider jwtProvider, UserRepository userRepository,
            StoreMemberRepository storeMemberRepository) {
        this.jwtProvider = jwtProvider;
        this.userRepository = userRepository;
        this.storeMemberRepository = storeMemberRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);
        Optional<User> user = Optional.empty();

        if (token != null && jwtProvider.validateToken(token)) {
            String email = jwtProvider.getEmailFromToken(token);
            user = userRepository.findByEmail(email).filter(User::getActive);
            if (user.isEmpty()) {
                log.warn("Token válido pero usuario no encontrado o inactivo: {}", email);
            }
        } else if (token != null) {
            log.warn("Token JWT inválido recibido en: {} {}", request.getMethod(), request.getRequestURI());
        }

        if (user.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        List<StoreMember> memberships = storeMemberRepository.findActiveByUserId(user.get().getId());
        String storeHeader = request.getHeader(STORE_HEADER);
        StoreMember member;

        if (StringUtils.hasText(storeHeader)) {
            Optional<UUID> storeId = parseUuid(storeHeader);
            if (storeId.isEmpty()) {
                JsonErrorWriter.write(response, HttpStatus.BAD_REQUEST, "La tienda indicada no es válida");
                return;
            }
            member = memberships.stream()
                    .filter(m -> m.getStore().getId().equals(storeId.get()))
                    .findFirst()
                    .orElse(null);
            if (member == null) {
                log.warn("Usuario {} sin acceso a la tienda {}", user.get().getEmail(), storeId.get());
                JsonErrorWriter.write(response, HttpStatus.FORBIDDEN, "No tenés acceso a esta tienda");
                return;
            }
        } else {
            // Sin header (ej. versiones de la app anteriores a las tiendas): si tiene una sola, se usa esa
            member = memberships.size() == 1 ? memberships.getFirst() : null;
        }

        if (member == null && requiresStore(request)) {
            if (memberships.isEmpty()) {
                JsonErrorWriter.write(response, HttpStatus.FORBIDDEN, "No tenés tiendas asignadas");
            } else {
                JsonErrorWriter.write(response, HttpStatus.BAD_REQUEST, "Elegí una tienda");
            }
            return;
        }

        var authorities = member == null
                ? List.<SimpleGrantedAuthority>of()
                : List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name()));
        var authentication = new UsernamePasswordAuthenticationToken(user.get(), null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        if (member == null) {
            filterChain.doFilter(request, response);
            return;
        }

        StoreContext.set(member.getStore().getId(), member.getRole());
        try {
            filterChain.doFilter(request, response);
        } finally {
            StoreContext.clear();
        }
    }

    // El perfil (con la lista de tiendas para elegir) y la sesión no necesitan una tienda
    private static boolean requiresStore(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/") && !uri.startsWith("/api/auth/") && !uri.equals("/api/users/me");
    }

    private static Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
