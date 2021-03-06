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
package io.gravitee.am.service;

import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.model.flow.Flow;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.FlowRepository;
import io.gravitee.am.service.exception.FlowNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.FlowServiceImpl;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class FlowServiceTest {

    @InjectMocks
    private FlowService flowService = new FlowServiceImpl();

    @Mock
    private EventService eventService;

    @Mock
    private FlowRepository flowRepository;

    @Mock
    private AuditService auditService;

    private final static String DOMAIN = "domain1";

    @Test
    public void shouldFindAll() {
        when(flowRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Single.just(Collections.singletonList(new Flow())));
        TestObserver testObserver = flowService.findAll(ReferenceType.DOMAIN, DOMAIN).test();

        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
    }

    @Test
    public void shouldNotFindAll_technicalException() {
        when(flowRepository.findAll(ReferenceType.DOMAIN, DOMAIN)).thenReturn(Single.error(TechnicalException::new));
        TestObserver testObserver = new TestObserver();
        flowService.findAll(ReferenceType.DOMAIN, DOMAIN).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void shouldCreate() {
        Flow newFlow = mock(Flow.class);
        when(flowRepository.create(any(Flow.class))).thenReturn(Single.just(new Flow()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = flowService.create(ReferenceType.DOMAIN, DOMAIN, newFlow).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(flowRepository, times(1)).create(any(Flow.class));
        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldCreate_technicalException() {
        Flow newFlow = mock(Flow.class);
        when(flowRepository.create(any(Flow.class))).thenReturn(Single.error(TechnicalException::new));

        TestObserver testObserver = flowService.create(ReferenceType.DOMAIN, DOMAIN, newFlow).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(eventService, never()).create(any());
    }

    @Test
    public void shouldUpdate() {
        Flow updateFlow = Mockito.mock(Flow.class);
        when(flowRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-flow")).thenReturn(Maybe.just(new Flow()));
        when(flowRepository.update(any(Flow.class))).thenReturn(Single.just(new Flow()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = flowService.update(ReferenceType.DOMAIN, DOMAIN, "my-flow", updateFlow).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(flowRepository, times(1)).findById(ReferenceType.DOMAIN, DOMAIN, "my-flow");
        verify(flowRepository, times(1)).update(any(Flow.class));
        verify(eventService, times(1)).create(any());
    }

    @Test
    public void shouldUpdate_technicalException() {
        Flow updateFlow = Mockito.mock(Flow.class);
        when(flowRepository.findById(ReferenceType.DOMAIN, DOMAIN, "my-flow")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = new TestObserver();
        flowService.update(ReferenceType.DOMAIN, DOMAIN, "my-flow", updateFlow).subscribe(testObserver);

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(flowRepository, never()).update(any(Flow.class));
        verify(eventService, never()).create(any());
    }

    @Test
    public void shouldUpdate_flowNotFound() {
        when(flowRepository.findById(ReferenceType.DOMAIN, DOMAIN,"my-flow")).thenReturn(Maybe.empty());

        TestObserver testObserver = new TestObserver();
        flowService.update(ReferenceType.DOMAIN, DOMAIN, "my-flow", new Flow()).subscribe(testObserver);

        testObserver.assertError(FlowNotFoundException.class);
        testObserver.assertNotComplete();

        verify(flowRepository, never()).update(any(Flow.class));
        verify(eventService, never()).create(any());
    }

    @Test
    public void shouldDelete_flowNotFound() {
        when(flowRepository.findById("my-flow")).thenReturn(Maybe.empty());

        TestObserver testObserver = flowService.delete("my-flow").test();

        testObserver.assertError(FlowNotFoundException.class);
        testObserver.assertNotComplete();

        verify(flowRepository, never()).delete(anyString());
        verify(eventService, never()).create(any());
    }

    @Test
    public void shouldDelete_technicalException() {
        when(flowRepository.findById("my-flow")).thenReturn(Maybe.error(TechnicalException::new));

        TestObserver testObserver = flowService.delete("my-flow").test();

        testObserver.assertError(TechnicalManagementException.class);
        testObserver.assertNotComplete();

        verify(flowRepository, never()).delete(anyString());
        verify(eventService, never()).create(any());
    }

    @Test
    public void shouldDelete() {
        when(flowRepository.findById("my-flow")).thenReturn(Maybe.just(new Flow()));
        when(flowRepository.delete("my-flow")).thenReturn(Completable.complete());
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        TestObserver testObserver = flowService.delete("my-flow").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();

        verify(flowRepository, times(1)).delete("my-flow");
        verify(eventService, times(1)).create(any());
    }
}
