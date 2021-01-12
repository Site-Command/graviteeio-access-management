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
package io.gravitee.am.policy.enrich.auth;

import io.gravitee.am.gateway.handler.context.GraviteeContext;
import io.gravitee.am.policy.enrich.auth.configuration.EnrichAuthFlowPolicyConfiguration;
import io.gravitee.am.policy.enrich.auth.configuration.Property;
import io.gravitee.am.repository.management.api.AuthenticationFlowContextRepository;
import io.gravitee.am.model.AuthenticationFlowContext;
import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.PolicyResult;
import io.gravitee.policy.api.annotations.OnRequest;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static io.gravitee.am.gateway.handler.context.GraviteeContext.ROUTING_CONTEXT_FIELD_NAME;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EnrichAuthFlowPolicy {
    private static Logger LOGGER = LoggerFactory.getLogger(EnrichAuthFlowPolicy.class);
    private static final String GATEWAY_POLICY_ENRICH_AUTH_FLOW_ERROR_KEY = "GATEWAY_POLICY_ENRICH_AUTH_FLOW_ERROR";

    private EnrichAuthFlowPolicyConfiguration configuration;

    public EnrichAuthFlowPolicy(EnrichAuthFlowPolicyConfiguration configuration) {
        this.configuration = configuration;
    }

    @OnRequest
    public void onRequest(Request request, Response response, ExecutionContext context, PolicyChain policyChain) {
        LOGGER.debug("Start enrich authentication flow policy");

        if (configuration.getProperties() != null && !configuration.getProperties().isEmpty()) {

            GraviteeContext gioContext = (GraviteeContext)context.getAttribute(ROUTING_CONTEXT_FIELD_NAME);
            if (gioContext == null || gioContext.getAuthenticationFlowContext() == null) {
                // this should never happen because a default GraviteeContext with AuthFlowContext is generated
                // when there are no data into the repository. This case may happen if the GraviteeContextHandler
                // is not register on the route calling this policy in this case log a WARN but continue to process the chain
                LOGGER.warn("Enrich Authentication Flow policy required a valid GraviteeContext");
                policyChain.doNext(request, response);

            } else {

                enrichAuthFlowContext(context)
                        .subscribe(
                                success -> policyChain.doNext(request, response),
                                error -> policyChain.failWith(PolicyResult.failure(GATEWAY_POLICY_ENRICH_AUTH_FLOW_ERROR_KEY, error.getMessage()))
                        );
            }
        } else {

            LOGGER.debug("No properties configured for the Enrich Authentication Flow policy");
            policyChain.doNext(request, response);
        }
    }

    private Single<AuthenticationFlowContext> enrichAuthFlowContext(ExecutionContext executionContext) {
        Map<String, Object> data = new HashMap<>();
        TemplateEngine tplEngine = executionContext.getTemplateEngine();
        for (Property property : configuration.getProperties()) {
            Object value = tplEngine.getValue(property.getValue(), String.class);
            data.put(property.getKey(), value);
        }

        GraviteeContext gioContext = (GraviteeContext)executionContext.getAttribute(ROUTING_CONTEXT_FIELD_NAME);

        final Instant now = Instant.now();
        AuthenticationFlowContext authContext = gioContext.getAuthenticationFlowContext();
        authContext.setVersion(authContext.getVersion() + 1);
        if (authContext.getData() != null) {
            // data already present, do not remove them but override same entries
            authContext.getData().putAll(data);
        } else {
            authContext.setData(data);
        }
        authContext.setCreatedAt(new Date(now.toEpochMilli()));
        ChronoUnit unit = ChronoUnit.valueOf(configuration.getTimeUnit().toUpperCase());
        authContext.setExpireAt(new Date(now.plus(configuration.getDuration(), unit).toEpochMilli()));

        final AuthenticationFlowContextRepository authContextRepository = executionContext.getComponent(AuthenticationFlowContextRepository.class);
        return authContextRepository.create(authContext);
    }
}
