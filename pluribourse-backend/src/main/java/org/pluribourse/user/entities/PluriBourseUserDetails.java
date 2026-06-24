package org.pluribourse.user.entities;

import org.jspecify.annotations.*;
import org.pluribourse.user.enums.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.*;
import org.springframework.security.core.userdetails.*;

import java.io.*;
import java.util.*;

@NullMarked
public class PluriBourseUserDetails implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private final User user;

    public PluriBourseUserDetails(User user) {
        this.user = user;
    }

    public Long getUserId() {
        return user.getId();
    }

    public String getRole() {
        return user.getRole().name();
    }

    public boolean isForcePasswordChange() {
        return user.isForcePasswordChange();
    }

    public Language getPreferredLanguage() {
        return user.getPreferredLanguage();
    }

    public boolean isLanguageInitialized() {
        return user.isLanguageInitialized();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    // null = old deserialized session with no enabled field → treat as enabled
    @Override
    public boolean isEnabled() {
        return user.getEnabled() == null || user.getEnabled();
    }
}
