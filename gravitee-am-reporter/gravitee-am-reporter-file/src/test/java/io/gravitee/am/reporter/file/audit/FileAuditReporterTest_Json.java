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
package io.gravitee.am.reporter.file.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.file.formatter.Type;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FileAuditReporterTest_Json extends FileAuditReporterTest {
    static {
        System.setProperty(FileAuditReporter.REPORTERS_FILE_ENABLED, "true");
        System.setProperty(FileAuditReporter.REPORTERS_FILE_OUTPUT, "json");
        System.setProperty(FileAuditReporter.REPORTERS_FILE_DIRECTORY, "target");
    }

    @Override
    protected  void checkAuditLogs(List<Audit> reportables, int loop) throws IOException {
        String filename = System.getProperty(FileAuditReporter.REPORTERS_FILE_DIRECTORY) + File.separator + "FileAuditReporterTest." +  Type.valueOf(System.getProperty(FileAuditReporter.REPORTERS_FILE_OUTPUT).toUpperCase()).getExtension();
        List<String> lines = Files.readAllLines(Paths.get(filename));
        final ObjectMapper objectMapper = new ObjectMapper();
        for (int i = 0; i < loop; ++i) {
            Audit readAudit = objectMapper.readValue(lines.get(i), Audit.class);
            assertReportEqualsTo(reportables.get(i), readAudit);
        }
    }

}
