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
package io.gravitee.am.repository.management.api;

import io.gravitee.am.model.AuthenticationFlowContext;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthenticationFlowContextRepositoryTest extends AbstractManagementTest {
    private String SESSION_ID = null;

    @Autowired
    private AuthenticationFlowContextRepository authenticationFlowContextRepository;

    @Before
    public void init() {
        SESSION_ID = "SESSION_"+ UUID.randomUUID().toString();
    }

    @Test
    public void shouldNotFindSession() {
        TestSubscriber<AuthenticationFlowContext> observer = authenticationFlowContextRepository.findBySession("unknown-sessions").test();
        observer.awaitTerminalEvent();

        observer.assertComplete();
        observer.assertValueCount(0);
        observer.assertNoErrors();
    }

    @Test
    public void shouldNotFindLastSession() {
        TestObserver<AuthenticationFlowContext> observer = authenticationFlowContextRepository.findLastBySession("unknown-sessions").test();
        observer.awaitTerminalEvent();

        observer.assertComplete();
        observer.assertValueCount(0);
        observer.assertNoErrors();
    }

    @Test
    public void shouldCreate() {
        Instant now = Instant.now();

        AuthenticationFlowContext entity = generateAuthContext();
        TestObserver<AuthenticationFlowContext> observer = authenticationFlowContextRepository.create(entity).test();
        observer.awaitTerminalEvent();

        observer.assertComplete();
        observer.assertNoErrors();
        assertSameContext(entity, observer);
    }

    private void assertSameContext(AuthenticationFlowContext entity, TestObserver<AuthenticationFlowContext> observer) {
        observer.assertValue(ctx -> ctx.getSessionId() != null && ctx.getSessionId().equals(entity.getSessionId()));
        observer.assertValue(ctx -> ctx.getVersion() == entity.getVersion());
        observer.assertValue(ctx -> ctx.getData() != null && ctx.getData().size()  == entity.getData().size());
        observer.assertValue(ctx -> ctx.getData().get("Key") != null &&  ctx.getData().get("Key").equals(entity.getData().get("Key")));
    }


    @Test
    public void shouldDelete() {
        AuthenticationFlowContext entity = generateAuthContext();
        authenticationFlowContextRepository.create(entity).blockingGet();
        entity = generateAuthContext(Instant.now(), 2);
        authenticationFlowContextRepository.create(entity).blockingGet();

        TestSubscriber<AuthenticationFlowContext> testList = authenticationFlowContextRepository.findBySession(SESSION_ID).test();
        testList.awaitTerminalEvent();
        testList.assertNoErrors();
        testList.assertValueCount(2);

        TestObserver<Void> testObserver = authenticationFlowContextRepository.delete(SESSION_ID).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();

        testList = authenticationFlowContextRepository.findBySession(SESSION_ID).test();
        testList.awaitTerminalEvent();
        testList.assertNoErrors();
        testList.assertNoValues();
    }

    @Test
    public void shouldDeleteSingle() {
        AuthenticationFlowContext entity = generateAuthContext();
        authenticationFlowContextRepository.create(entity).blockingGet();
        entity = generateAuthContext(Instant.now(), 2);
        authenticationFlowContextRepository.create(entity).blockingGet();

        TestSubscriber<AuthenticationFlowContext> testList = authenticationFlowContextRepository.findBySession(SESSION_ID).test();
        testList.awaitTerminalEvent();
        testList.assertNoErrors();
        testList.assertValueCount(2);

        TestObserver<Void> testObserver = authenticationFlowContextRepository.delete(SESSION_ID, 1).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();

        testList = authenticationFlowContextRepository.findBySession(SESSION_ID).test();
        testList.awaitTerminalEvent();
        testList.assertNoErrors();
        testList.assertValueCount(1);

        AuthenticationFlowContext readValue = authenticationFlowContextRepository.findBySession(SESSION_ID).blockingFirst();
        assertNotNull(readValue);
        assertEquals("Expected version 2 because version 1 should be deleted", 2, readValue.getVersion());
    }

    @Test
    public void shouldFind() {
        AuthenticationFlowContext entity = generateAuthContext();
        authenticationFlowContextRepository.create(entity).blockingGet();
        entity = generateAuthContext(Instant.now(), 2);
        authenticationFlowContextRepository.create(entity).blockingGet();

        TestSubscriber<AuthenticationFlowContext> testList = authenticationFlowContextRepository.findBySession(SESSION_ID).test();
        testList.awaitTerminalEvent();
        testList.assertNoErrors();
        testList.assertValueCount(2);

        TestObserver<AuthenticationFlowContext> testObserver = authenticationFlowContextRepository.findLastBySession(SESSION_ID).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        assertSameContext(entity, testObserver);
    }

    @Test
    public void shouldNotFindExpiredData() {
        AuthenticationFlowContext entity = generateAuthContext(Instant.now().minus(10, ChronoUnit.MINUTES), 1);
        authenticationFlowContextRepository.create(entity).blockingGet();
        entity = generateAuthContext(Instant.now(), 2);
        authenticationFlowContextRepository.create(entity).blockingGet();

        TestSubscriber<AuthenticationFlowContext> testList = authenticationFlowContextRepository.findBySession(SESSION_ID).test();
        testList.awaitTerminalEvent();
        testList.assertNoErrors();
        testList.assertValueCount(1);

        TestObserver<AuthenticationFlowContext> testObserver = authenticationFlowContextRepository.findLastBySession(SESSION_ID).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNoErrors();
        assertSameContext(entity, testObserver);
    }

    protected AuthenticationFlowContext generateAuthContext() {
        return generateAuthContext(Instant.now(), 1);
    }

    protected AuthenticationFlowContext generateAuthContext(Instant now, int version) {
        AuthenticationFlowContext entity = new AuthenticationFlowContext();
        entity.setVersion(version);
        entity.setSessionId(SESSION_ID);
        entity.setData(Collections.singletonMap("Key", "Value"));
        entity.setCreatedAt(new Date(now.toEpochMilli()));
        entity.setExpireAt(new Date(now.plus(2, ChronoUnit.MINUTES).toEpochMilli()));
        return entity;
    }
}
