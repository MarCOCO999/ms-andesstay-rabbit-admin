package com.andesstay.ms_andesstay_rabbit_admin.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Traduce los App Roles de Azure AD (claim "roles": Admin, Operador, Cliente, Auditor)
 * a GrantedAuthority con prefijo ROLE_, para usar hasRole(...)/@PreAuthorize en el servicio.
 */
public class AzureRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final String rolesClaim;

    public AzureRolesConverter(String rolesClaim) {
        this.rolesClaim = rolesClaim;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(rolesClaim);
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
    }
}
