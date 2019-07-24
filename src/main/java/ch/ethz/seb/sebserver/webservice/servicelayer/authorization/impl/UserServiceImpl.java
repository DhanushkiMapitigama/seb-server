/*
 * Copyright (c) 2018 ETH Zürich, Educational Development and Technology (LET)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ch.ethz.seb.sebserver.webservice.servicelayer.authorization.impl;

import java.beans.PropertyEditorSupport;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.WebDataBinder;

import ch.ethz.seb.sebserver.gbl.model.user.UserInfo;
import ch.ethz.seb.sebserver.gbl.model.user.UserRole;
import ch.ethz.seb.sebserver.gbl.profile.WebServiceProfile;
import ch.ethz.seb.sebserver.webservice.servicelayer.authorization.UserService;

@Lazy
@Service
@WebServiceProfile
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    public interface ExtractUserFromAuthenticationStrategy {
        SEBServerUser extract(Principal principal);
    }

    private final Collection<ExtractUserFromAuthenticationStrategy> extractStrategies;

    public UserServiceImpl(final Collection<ExtractUserFromAuthenticationStrategy> extractStrategies) {

        this.extractStrategies = extractStrategies;
    }

    @Override
    public SEBServerUser getCurrentUser() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new IllegalStateException("No Authentication found within Springs SecurityContextHolder");
        }

        return extractFromPrincipal(authentication);
    }

    @Override
    public SEBServerUser extractFromPrincipal(final Principal principal) {
        for (final ExtractUserFromAuthenticationStrategy extractStrategie : this.extractStrategies) {
            try {
                final SEBServerUser user = extractStrategie.extract(principal);
                if (user != null) {
                    return user;
                }
            } catch (final Exception e) {
                log.error("Unexpected error while trying to extract user form principal: ", e);
            }
        }

        throw new IllegalArgumentException("Unable to extract internal user from Principal: " + principal);
    }

    @Override
    public SEBServerUser getAnonymousUser() {
        return ANONYMOUS_USER;
    }

    @Override
    public SEBServerUser getSuperUser() {
        return SUPER_USER;
    }

    @Override
    public void addUsersInstitutionDefaultPropertySupport(final WebDataBinder binder) {
        final PropertyEditorSupport usersInstitutionDefaultEditor = new PropertyEditorSupport() {
            @Override
            public void setAsText(final String text) throws IllegalArgumentException {
                if (UserService.USERS_INSTITUTION_AS_DEFAULT.equals(text)) {
                    setValue(getCurrentUser().institutionId());
                } else {
                    setValue((text == null) ? null : Long.decode(text));
                }
            }
        };
        binder.registerCustomEditor(Long.class, usersInstitutionDefaultEditor);

    }

    // 1. OAuth2Authentication strategy
    @Lazy
    @Component
    public static class DefaultUserExtractStrategy implements ExtractUserFromAuthenticationStrategy {

        @Override
        public SEBServerUser extract(final Principal principal) {
            if (principal instanceof OAuth2Authentication) {
                final Authentication userAuthentication = ((OAuth2Authentication) principal).getUserAuthentication();
                if (userAuthentication instanceof UsernamePasswordAuthenticationToken) {
                    final Object userPrincipal =
                            ((UsernamePasswordAuthenticationToken) userAuthentication).getPrincipal();
                    if (userPrincipal instanceof SEBServerUser) {
                        return (SEBServerUser) userPrincipal;
                    }
                }
            }

            return null;
        }
    }

    private static final SEBServerUser SUPER_USER = new SEBServerUser(
            -1L,
            new UserInfo("SEB_SERVER_SUPER_USER", -1L, "superUser", "superUser", null, false, null, null,
                    Arrays.asList(UserRole.values())
                            .stream()
                            .map(ur -> ur.name())
                            .collect(Collectors.toSet())),
            null);

    private static final SEBServerUser ANONYMOUS_USER = new SEBServerUser(
            -1L,
            new UserInfo("SEB_SERVER_ANONYMOUS_USER", -2L, "anonymous", "anonymous", null, false, null, null,
                    Arrays.asList(UserRole.values())
                            .stream()
                            .map(ur -> ur.name())
                            .collect(Collectors.toSet())),
            null);

}