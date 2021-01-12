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
package io.gravitee.am.service.impl;

import io.gravitee.am.model.AuthenticationFlowContext;
import io.gravitee.am.repository.management.api.AuthenticationFlowContextRepository;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.gravitee.am.service.exception.AuthenticationFlowConsistencyException;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Function;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class AuthenticationFlowContextServiceImpl implements AuthenticationFlowContextService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationFlowContextServiceImpl.class);

    @Lazy
    @Autowired
    private AuthenticationFlowContextRepository authContextRepository;

    @Override
    public Completable clearContext(final String sessionId) {
        if (sessionId == null) {
            return Completable.complete();
        }
        return authContextRepository.delete(sessionId);
    }

    @Override
    public Maybe<AuthenticationFlowContext> loadContext(final String sessionId, final int expectedVersion) {
        return authContextRepository.findLastBySession(sessionId).switchIfEmpty(Maybe.fromCallable(() -> {
            AuthenticationFlowContext context = new AuthenticationFlowContext();
            context.setSessionId(sessionId);
            context.setVersion(0);
            context.setCreatedAt(new Date());
            return context;
        })).map(context -> {
            if (context.getVersion() > 0 && context.getVersion() < expectedVersion) {
                LOGGER.debug("Authentication Flow Context read with version '{}' but '{}' was expected", context.getVersion(), expectedVersion);
                throw new AuthenticationFlowConsistencyException();
            }
            return context;
        }).retryWhen(new RetryWithDelay());
    }

    /**
     * Retry load context with delay
     * from : https://stackoverflow.com/a/25292833
     */
    private class RetryWithDelay implements Function<Flowable<Throwable>, Publisher<?>> {
        private final int maxRetries;
        private final int retryDelayMillis;
        private int retryCount;

        public RetryWithDelay() {
            this.maxRetries = 2;
            this.retryDelayMillis = 250;
            this.retryCount = 0;
        }

        @Override
        public Publisher<?> apply(@NonNull Flowable<Throwable> attempts) throws Exception {
            return attempts
                    .flatMap((throwable) -> {
                        // perform retry only on Consistency exception
                        if (throwable instanceof AuthenticationFlowConsistencyException) {
                            if (++retryCount < maxRetries) {
                                // When this Observable calls onNext, the original
                                // Observable will be retried (i.e. re-subscribed).
                                return Flowable.timer(retryDelayMillis * (retryCount + 1),
                                        TimeUnit.MILLISECONDS);
                            }
                        }
                        // Max retries hit. Just pass the error along.
                        return Flowable.error(throwable);
                    });
        }
    }
}
