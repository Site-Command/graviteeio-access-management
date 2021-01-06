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

import io.gravitee.am.common.analytics.Type;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.gravitee.am.reporter.api.audit.AuditReporter;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.reporter.api.audit.model.AuditAccessPoint;
import io.gravitee.am.reporter.api.audit.model.AuditEntity;
import io.gravitee.am.reporter.file.FileReporterConfiguration;
import io.gravitee.am.reporter.file.exception.FileReporterInitializationException;
import io.gravitee.am.reporter.file.formatter.Formatter;
import io.gravitee.am.reporter.file.formatter.FormatterFactory;
import io.gravitee.am.reporter.file.spring.FileReporterSpringConfiguration;
import io.gravitee.am.reporter.file.vertx.VertxFileWriter;
import io.gravitee.common.service.AbstractService;
import io.gravitee.reporter.api.Reportable;
import io.gravitee.reporter.api.http.Metrics;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import java.io.File;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(FileReporterSpringConfiguration.class)
public class FileAuditReporter extends AbstractService implements AuditReporter, InitializingBean {
    public static final String REPORTERS_FILE_ENABLED = "reporters.file.enabled";
    public static final String REPORTERS_FILE_DIRECTORY = "reporters.file.directory";
    public static final String REPORTERS_FILE_OUTPUT = "reporters.file.output";

    private static Logger LOGGER = LoggerFactory.getLogger(FileAuditReporter.class);

    @Autowired
    private Vertx vertx;

    @Autowired
    private Environment env;

    @Autowired
    private FileReporterConfiguration config;

    @Value("${" + REPORTERS_FILE_ENABLED + ":false}")
    private boolean enabled;

    @Value("${" + REPORTERS_FILE_DIRECTORY + ":#{systemProperties['gravitee.home']}/reporter-logs/}")
    private String directory;

    @Value("${" + REPORTERS_FILE_OUTPUT + ":JSON}")
    private String outputType;

    private VertxFileWriter writer;

    @Override
    public Single<Page<Audit>> search(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria, int page, int size) {
        throw new IllegalStateException("Search method not implemented for File reporter");
    }

    @Override
    public Single<Map<Object, Object>> aggregate(ReferenceType referenceType, String referenceId, AuditReportableCriteria criteria, Type analyticsType) {
        throw new IllegalStateException("Aggregate method not implemented for File reporter");
    }

    @Override
    public Maybe<Audit> findById(ReferenceType referenceType, String referenceId, String id) {
        throw new IllegalStateException("FindById method not implemented for File reporter");
    }

    @Override
    public boolean handlesSearch() {
        return false;
    }

    @Override
    public void report(Reportable reportable) {
        LOGGER.debug("Report({})", reportable);
        if (writer != null) {
            if (reportable instanceof Audit) {
                AuditEntry entry = convert(reportable);

                writer.write(entry);
            } else {
                LOGGER.debug("Ignore reportable of type {}", reportable.getClass().getName());
            }
        } else {
            LOGGER.debug("Writer is null, ignore reportable");
        }
    }

    private AuditEntry convert(Reportable reportable) {
        AuditEntry entry = new AuditEntry();
        entry.setId(((Audit) reportable).getId());
        entry.setReferenceId(((Audit) reportable).getReferenceId());
        entry.setReferenceType(((Audit) reportable).getReferenceType());
        entry.setTimestamp(reportable.timestamp());
        entry.setTransactionId(((Audit) reportable).getTransactionId());
        entry.setType(((Audit) reportable).getType());

        // do not copy message part of the status
        entry.setStatus(((Audit) reportable).getOutcome() != null ? ((Audit) reportable).getOutcome().getStatus() : null);

        // copy access point and replace invalid IP
        AuditAccessPoint accessPoint = ((Audit) reportable).getAccessPoint();
        if (accessPoint != null) {
            entry.setAccessPoint(new AuditAccessPoint(accessPoint));
            if (!InetAddressValidator.getInstance().isValid(accessPoint.getIpAddress())) {
                entry.getAccessPoint().setIpAddress("0.0.0.0");
            }
        }

        AuditEntity actor = ((Audit) reportable).getActor();
        if (actor != null) {
            AuditEntity cloneOfActor = new AuditEntity(actor);
            cloneOfActor.setAttributes(null);
            entry.setActor(cloneOfActor);
        }

        AuditEntity target = ((Audit) reportable).getTarget();
        if (target != null) {
            AuditEntity cloneOfTarget = new AuditEntity(target);
            cloneOfTarget.setAttributes(null);
            entry.setTarget(cloneOfTarget);
        }
        
        return entry;
    }

    @Override
    public boolean canHandle(Reportable reportable) {
        return enabled;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        //this.enabled = env.getProperty(REPORTERS_FILE_ENABLED, boolean.class, false);
    }

    @Override
    protected void doStart() throws Exception {
        if (enabled) {
            // Initialize writers
            final io.gravitee.am.reporter.file.formatter.Type type = io.gravitee.am.reporter.file.formatter.Type.valueOf(outputType.toUpperCase(Locale.ENGLISH));
            Formatter formatter = FormatterFactory.getFormatter(type);
            applicationContext.getAutowireCapableBeanFactory().autowireBean(formatter);

            final String filename = Paths.get(directory, config.getFilename() + '.' + type.getExtension()).toFile().getAbsolutePath();
            this.writer = new VertxFileWriter<>(
                    vertx,
                    formatter,
                    filename);

            Future writerInitialization = writer.initialize();
            writerInitialization.onComplete(__ -> LOGGER.info("File reporter successfully started"));
            writerInitialization.onFailure((error) -> {
                LOGGER.warn("An error occurs while starting file reporter", error);
                throw new FileReporterInitializationException("An error occurs while starting file reporter [" + filename + "]");
            });
        }
    }

}
