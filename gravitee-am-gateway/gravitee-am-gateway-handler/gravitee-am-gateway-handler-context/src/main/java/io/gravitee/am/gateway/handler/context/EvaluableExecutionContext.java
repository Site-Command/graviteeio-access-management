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
package io.gravitee.am.gateway.handler.context;

import io.gravitee.am.model.AuthenticationFlowContext;

import java.util.HashMap;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EvaluableExecutionContext {

    private final ReactableExecutionContext executionContext;

    private final AuthenticationFlowContext authFlowContext;

    EvaluableExecutionContext(ReactableExecutionContext executionContext) {
        this.executionContext = executionContext;
        GraviteeContext graviteeContext = (GraviteeContext)this.executionContext.getAttribute(GraviteeContext.ROUTING_CONTEXT_FIELD_NAME);
        if (graviteeContext != null && graviteeContext.getAuthenticationFlowContext() != null) {
            this.authFlowContext = graviteeContext.getAuthenticationFlowContext();
        } else {
            this.authFlowContext = new AuthenticationFlowContext();
        }
    }

    public Map<String, Object> getAttributes() {
        // Create a Map using context attributes and authentication flow context information
        // this is to always have an up to date list of attributes if a policy add some entries
        Map<String, Object> attributes = new HashMap<>(this.executionContext.getAttributes());
        attributes.putAll(this.authFlowContext.getData());
        return attributes;
    }
}
