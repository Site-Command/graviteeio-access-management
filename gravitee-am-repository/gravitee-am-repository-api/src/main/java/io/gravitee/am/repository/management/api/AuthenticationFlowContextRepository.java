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
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;

/**
 * Repository to store information between different phases of authentication flow.
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface AuthenticationFlowContextRepository {
    Maybe<AuthenticationFlowContext> findById(String id);
    /**
     * Find last context data for given sessionId
     *
     * @param session session id
     * @return
     */
    Maybe<AuthenticationFlowContext> findLastBySession(String session);
    /**
     * Find all contexts data for given sessionId
     *
     * @param session session id
     * @return
     */
    Flowable<AuthenticationFlowContext> findBySession(String session);

    /**
     * Create authentication context
     * @param
     * @return
     */
    Single<AuthenticationFlowContext> create(AuthenticationFlowContext context);

    /**
     * Delete all context for given session Id
     * @param session
     * @return acknowledge of the operation
     */
    Completable delete(String session);

    /**
     * Delete context for given session Id and specific version
     * @param session
     * @param version
     * @return acknowledge of the operation
     */
    Completable delete(String session, int version);

    default Completable purgeExpiredData() {
        return Completable.complete();
    }
}
