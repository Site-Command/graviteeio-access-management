/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.gateway.handler.vertx.auth.webauthn.store;

import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.service.CredentialService;
import io.reactivex.Single;
import io.vertx.ext.auth.webauthn.Authenticator;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class RepositoryCredentialStoreTest {

    @InjectMocks
    private RepositoryCredentialStore repositoryCredentialStore = new RepositoryCredentialStore();

    @Mock
    private CredentialService credentialService;

    @Mock
    private JWTBuilder jwtBuilder;

    @Mock
    private Domain domain;

    @Test
    public void shouldFetchAuthenticator_byUsername_emptyList() {
        Authenticator query = new Authenticator();
        query.setUserName("username");

        when(domain.getId()).thenReturn("domain-id");
        when(credentialService.findByUsername(ReferenceType.DOMAIN, domain.getId(), query.getUserName())).thenReturn(Single.just(Collections.emptyList()));
        when(jwtBuilder.sign(any())).thenReturn("part1.part2.part3");
        List<Authenticator> authenticators = repositoryCredentialStore.fetch(query).result();

        Assert.assertNotNull(authenticators);
        Assert.assertTrue(authenticators.size() == 1);
        Assert.assertTrue(authenticators.get(0).getCredID().equals("part3part3"));
        verify(jwtBuilder, times(2)).sign(any());

    }

    @Test
    public void shouldFetchAuthenticator_byUsername_emptyList_truncateCredId() {
        Authenticator query = new Authenticator();
        query.setUserName("username");

        when(domain.getId()).thenReturn("domain-id");
        when(credentialService.findByUsername(ReferenceType.DOMAIN, domain.getId(), query.getUserName())).thenReturn(Single.just(Collections.emptyList()));
        when(jwtBuilder.sign(any())).thenReturn("part1.part2.000000000000000000000000000000000000000000000000000000000000000000","part1.part2.000000000000000000000000000000000000000000000000000000000000000000");
        List<Authenticator> authenticators = repositoryCredentialStore.fetch(query).result();

        Assert.assertNotNull(authenticators);
        Assert.assertTrue(authenticators.size() == 1);
        Assert.assertTrue(authenticators.get(0).getCredID().equals("00000000000000000000000000000000000000000000000000000000000000000000000000000000000000"));
        Assert.assertTrue(authenticators.get(0).getCredID().length() == 86);
        verify(jwtBuilder, times(2)).sign(any());

    }

    @Test
    public void shouldFetchAuthenticator_byUsername_withValues() {
        Authenticator query = new Authenticator();
        query.setUserName("username");

        Credential credential = new Credential();
        credential.setUsername(query.getUserName());
        credential.setCredentialId("credID");

        when(domain.getId()).thenReturn("domain-id");
        when(credentialService.findByUsername(ReferenceType.DOMAIN, domain.getId(), query.getUserName())).thenReturn(Single.just(Collections.singletonList(credential)));
        List<Authenticator> authenticators = repositoryCredentialStore.fetch(query).result();

        Assert.assertNotNull(authenticators);
        Assert.assertTrue(authenticators.size() == 1);
        Assert.assertTrue(authenticators.get(0).getCredID().equals("credID"));
        Assert.assertTrue(authenticators.get(0).getUserName().equals(query.getUserName()));
        verify(jwtBuilder, never()).sign(any());
    }
}
