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
package io.gravitee.am.reporter.file.formatter.csv;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.file.audit.AuditEntry;
import io.gravitee.reporter.api.log.Log;
import io.vertx.core.buffer.Buffer;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuditFormatter extends SingleValueFormatter<AuditEntry> {

    private final ObjectMapper mapper = new ObjectMapper();

    {
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Override
    public Buffer format0(AuditEntry entry) {
        final Buffer buffer = Buffer.buffer();

        appendLong(buffer, entry.getTimestamp().toEpochMilli());
        appendString(buffer, entry.getId());
        appendString(buffer, entry.getTransactionId());
        appendString(buffer, entry.getType());
        appendString(buffer, entry.getReferenceType().name());
        appendString(buffer, entry.getReferenceId());
        appendString(buffer, entry.getStatus());

        try {
            appendString(buffer, mapper.writeValueAsString(entry.getAccessPoint()));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        try {
            appendString(buffer, mapper.writeValueAsString(entry.getActor()));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        try {
            appendString(buffer, mapper.writeValueAsString(entry.getTarget()));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        return buffer;
    }
}
