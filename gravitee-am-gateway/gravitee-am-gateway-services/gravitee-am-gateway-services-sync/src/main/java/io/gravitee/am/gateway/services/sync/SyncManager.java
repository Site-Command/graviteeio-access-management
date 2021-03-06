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
package io.gravitee.am.gateway.services.sync;

import io.gravitee.am.common.event.Action;
import io.gravitee.am.gateway.certificate.DefaultCertificateManager;
import io.gravitee.am.gateway.core.manager.EntityManager;
import io.gravitee.am.gateway.reactor.SecurityDomainManager;
import io.gravitee.am.gateway.reactor.impl.DefaultClientManager;
import io.gravitee.am.model.Certificate;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.management.api.ApplicationRepository;
import io.gravitee.am.repository.management.api.CertificateRepository;
import io.gravitee.am.repository.management.api.DomainRepository;
import io.gravitee.am.repository.management.api.EventRepository;
import io.gravitee.common.event.EventManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;

import java.text.Collator;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toMap;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SyncManager implements InitializingBean {

    private final Logger logger = LoggerFactory.getLogger(SyncManager.class);
    private static final String SHARDING_TAGS_SYSTEM_PROPERTY = "tags";
    private static final String SHARDING_TAGS_SEPARATOR = ",";

    @Autowired
    private EventManager eventManager;

    @Autowired
    private SecurityDomainManager securityDomainManager;

    @Autowired
    private EntityManager<Client> clientManager;

    @Autowired
    private EntityManager<Certificate> certificateManager;

    @Autowired
    private Environment environment;

    @Lazy
    @Autowired
    private DomainRepository domainRepository;

    @Lazy
    @Autowired
    private EventRepository eventRepository;

    @Lazy
    @Autowired
    private ApplicationRepository applicationRepository;

    @Lazy
    @Autowired
    private CertificateRepository certificateRepository;

    private Optional<List<String>> shardingTags;

    private long lastRefreshAt = -1;

    private long lastDelay = 0;

    @Override
    public void afterPropertiesSet() throws Exception {
        this.initShardingTags();
    }

    public void refresh() {
        logger.debug("Refreshing sync state...");
        long nextLastRefreshAt = System.currentTimeMillis();

        try {
            if (lastRefreshAt == -1) {
                logger.debug("Initial synchronization");
                deployDomains();
                deployClients();
                deployCertificates();
            } else {
                // search for events and compute them
                logger.debug("Events synchronization");
                List<Event> events = eventRepository.findByTimeFrame(lastRefreshAt - lastDelay, nextLastRefreshAt).blockingGet();

                if (events != null && !events.isEmpty()) {
                    // Extract only the latest events by type and id
                    Map<AbstractMap.SimpleEntry, Event> sortedEvents = events
                            .stream()
                            .collect(
                                    toMap(
                                            event -> new AbstractMap.SimpleEntry<>(event.getType(), event.getPayload().getId()),
                                            event -> event, BinaryOperator.maxBy(comparing(Event::getCreatedAt)), LinkedHashMap::new));
                    computeEvents(sortedEvents.values());
                }

            }
            lastRefreshAt = nextLastRefreshAt;
            lastDelay = System.currentTimeMillis() - nextLastRefreshAt;
        } catch (Exception ex) {
            logger.error("An error occurs while synchronizing the security domains", ex);
        }
    }

    private void deployDomains() {
        logger.info("Starting security domains initialization ...");
        Set<Domain> domains = domainRepository.findAll()
                // remove disabled domains
                .map(registeredDomains -> {
                    if (registeredDomains != null) {
                        return registeredDomains
                                .stream()
                                .filter(Domain::isEnabled)
                                .collect(Collectors.toSet());
                    }
                    return Collections.<Domain>emptySet();
                })
                .blockingGet();

        domains.stream()
                .forEach(domain -> {
                    // Does the security domain have a matching sharding tags ?
                    if (hasMatchingTags(domain)) {
                        securityDomainManager.deploy(domain);
                    }
                });
        logger.info("Security domains initialization done");
    }

    private void deployClients() {
        logger.info("Starting clients initialization ...");
        List<Application> applications = applicationRepository.findAll().blockingGet();
        if (applications != null) {
            ((DefaultClientManager) clientManager).init(applications.stream().map(Application::convert).collect(Collectors.toList()));
        }
        logger.info("Clients initialization done");
    }

    private void deployCertificates() {
        logger.info("Starting certificates initialization ...");
        Set<Certificate> certificates = certificateRepository.findAll().blockingGet();
        ((DefaultCertificateManager) certificateManager).init(certificates);
        logger.info("Certificates initialization done");
    }

    private void computeEvents(Collection<Event> events) {
        events.forEach(event -> {
            logger.debug("Compute event id : {}, with type : {} and timestamp : {} and payload : {}", event.getId(), event.getType(), event.getCreatedAt(), event.getPayload());
            switch(event.getType()) {
                case DOMAIN:
                    synchronizeDomain(event);
                    break;
                case APPLICATION:
                    synchronizeApplication(event);
                    break;
                case CERTIFICATE:
                    synchronizeCertificate(event);
                    break;
                default:
                    eventManager.publishEvent(io.gravitee.am.common.event.Event.valueOf(event.getType(), event.getPayload().getAction()), event.getPayload());
            }
        });
    }

    private void synchronizeDomain(Event event) {
        final String domainId = event.getPayload().getId();
        final Action action = event.getPayload().getAction();
        switch (action) {
            case CREATE:
            case UPDATE:
                Domain domain = domainRepository.findById(domainId).blockingGet();
                if (domain != null) {
                    // Get deployed domain
                    Domain deployedDomain = securityDomainManager.get(domain.getId());
                    // Does the security domain have a matching sharding tags ?
                    if (hasMatchingTags(domain)) {
                        // domain is not yet deployed, so let's do it !
                        if (deployedDomain == null) {
                            securityDomainManager.deploy(domain);
                        } else if (deployedDomain.getUpdatedAt().before(domain.getUpdatedAt())) {
                            securityDomainManager.update(domain);
                        }
                    } else {
                        // Check that the security domain was not previously deployed with other tags
                        // In that case, we must undeploy it
                        if (deployedDomain != null) {
                            securityDomainManager.undeploy(domainId);
                        }
                    }
                }
                break;
            case DELETE:
                securityDomainManager.undeploy(domainId);
                break;
        }
    }

    private void synchronizeApplication(Event event) {
        final String applicationId = event.getPayload().getId();
        final Action action = event.getPayload().getAction();
        switch (action) {
            case CREATE:
            case UPDATE:
                Application application = applicationRepository.findById(applicationId).blockingGet();
                if (application != null) {
                    // Get deployed client
                    Client client = Application.convert(application);
                    Client deployedClient = clientManager.get(application.getId());
                    // client is not yet deployed, so let's do it !
                    if (deployedClient == null) {
                        clientManager.deploy(client);
                    } else if (deployedClient.getUpdatedAt().before(application.getUpdatedAt())) {
                        clientManager.update(client);
                    }
                }
                break;
            case DELETE:
                clientManager.undeploy(applicationId);
                break;
        }
    }

    private void synchronizeCertificate(Event event) {
        final String certificateId = event.getPayload().getId();
        final Action action = event.getPayload().getAction();
        switch (action) {
            case CREATE:
            case UPDATE:
                Certificate certificate = certificateRepository.findById(certificateId).blockingGet();
                if (certificate != null) {
                    // Get deployed certificate
                    Certificate deployedCertificate = certificateManager.get(certificate.getId());
                    // certificate is not yet deployed, so let's do it !
                    if (deployedCertificate == null) {
                        certificateManager.deploy(certificate);
                    } else if (deployedCertificate.getUpdatedAt().before(certificate.getUpdatedAt())) {
                        certificateManager.update(certificate);
                    }
                }
                break;
            case DELETE:
                certificateManager.undeploy(certificateId);
                break;
        }
    }

    private void initShardingTags() {
        String systemPropertyTags = System.getProperty(SHARDING_TAGS_SYSTEM_PROPERTY);
        String tags = systemPropertyTags == null ?
                environment.getProperty(SHARDING_TAGS_SYSTEM_PROPERTY) : systemPropertyTags;
        if (tags != null && ! tags.isEmpty()) {
            shardingTags = Optional.of(Arrays.asList(tags.split(SHARDING_TAGS_SEPARATOR)));
        } else {
            shardingTags = Optional.empty();
        }
    }

    private boolean hasMatchingTags(Domain domain) {
        if (shardingTags.isPresent()) {
            List<String> tagList = shardingTags.get();
            if (domain.getTags() != null) {
                final List<String> inclusionTags = tagList.stream()
                        .map(String::trim)
                        .filter(tag -> !tag.startsWith("!"))
                        .collect(Collectors.toList());

                final List<String> exclusionTags = tagList.stream()
                        .map(String::trim)
                        .filter(tag -> tag.startsWith("!"))
                        .map(tag -> tag.substring(1))
                        .collect(Collectors.toList());

                if (inclusionTags.stream().anyMatch(exclusionTags::contains)) {
                    throw new IllegalArgumentException("You must not configure a tag to be included and excluded");
                }

                final boolean hasMatchingTags =
                        inclusionTags.stream()
                                .anyMatch(tag -> domain.getTags().stream()
                                        .anyMatch(apiTag -> {
                                            final Collator collator = Collator.getInstance();
                                            collator.setStrength(Collator.NO_DECOMPOSITION);
                                            return collator.compare(tag, apiTag) == 0;
                                        })
                                ) || (!exclusionTags.isEmpty() &&
                                exclusionTags.stream()
                                        .noneMatch(tag -> domain.getTags().stream()
                                                .anyMatch(apiTag -> {
                                                    final Collator collator = Collator.getInstance();
                                                    collator.setStrength(Collator.NO_DECOMPOSITION);
                                                    return collator.compare(tag, apiTag) == 0;
                                                })
                                        ));

                if (!hasMatchingTags) {
                    logger.debug("The security domain {} has been ignored because not in configured tags {}", domain.getName(), tagList);
                }
                return hasMatchingTags;
            }
            logger.debug("Tags {} are configured on gateway instance but not found on the security domain {}", tagList, domain.getName());
            return false;
        }
        // no tags configured on this gateway instance
        return true;
    }
}
