package com.eduvision.security;

import com.eduvision.domain.User;
import com.eduvision.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Resolves the authenticated {@link User} from the security context.
 *
 * <p>Controllers use this instead of trusting ids or emails supplied by the client, so a student can
 * never read or write another student's data by editing a request body.
 */
@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public User get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null
                || "anonymousUser".equals(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return users.findByEmailIgnoreCase(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }

    public Long id() {
        return get().id;
    }

    public static SimpleGrantedAuthority authority(User user) {
        return new SimpleGrantedAuthority("ROLE_" + user.role.name());
    }
}