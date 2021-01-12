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
package io.gravitee.am.gateway.handler.common.vertx.web.handler;

import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.CookieSession;
import io.gravitee.am.gateway.handler.context.GraviteeContext;
import io.gravitee.am.service.AuthenticationFlowContextService;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.AUTH_FLOW_CONTEXT_VERSION;
import static java.util.Optional.ofNullable;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class GraviteeContextHandler implements Handler<RoutingContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraviteeContextHandler.class);

    private AuthenticationFlowContextService authenticationFlowContextService;

    public GraviteeContextHandler(AuthenticationFlowContextService authenticationFlowContextService) {
        this.authenticationFlowContextService = authenticationFlowContextService;
    }

    @Override
    public void handle(RoutingContext context) {
        final GraviteeContext graviteeContext = new GraviteeContext();
        context.put(GraviteeContext.ROUTING_CONTEXT_FIELD_NAME, graviteeContext);

        CookieSession session = (CookieSession) context.session().getDelegate();
        if (session != null && !session.isDestroyed()) {
            final String sessionId = session.get(ConstantKeys.TRANSACTION_ID_KEY);
            final int version = ofNullable((Number) session.get(AUTH_FLOW_CONTEXT_VERSION)).map(Number::intValue).orElse(1);
            authenticationFlowContextService.loadContext(sessionId, version)
                    .subscribe(
                            ctx ->  {
                                graviteeContext.setAuthenticationFlowContext(ctx);
                                context.next();
                            },
                            error -> {
                                LOGGER.debug("AuthenticationFlowContext can't be loaded", error);
                                context.fail(error);// TODO should really fail on error or continue the flow ?
                            });
        }
    }
}
