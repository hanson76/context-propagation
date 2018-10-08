/*
 * Copyright 2016-2018 Talsma ICT
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
package nl.talsmasoftware.context.functions;

import nl.talsmasoftware.context.Context;
import nl.talsmasoftware.context.ContextSnapshot;
import nl.talsmasoftware.context.DummyContextManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasToString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author Sjoerd Talsma
 */
public class PredicateWithContextTest {

    private ContextSnapshot snapshot;
    private Context<Void> context;

    @Before
    @After
    public void clearDummyContext() {
        DummyContextManager.clear();
    }

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        snapshot = mock(ContextSnapshot.class);
        context = mock(Context.class);
    }

    @After
    public void verifyMocks() {
        verifyNoMoreInteractions(snapshot, context);
    }

    @Test
    public void testTest() {
        new PredicateWithContext<>(snapshot, input -> true).test("input");
        verify(snapshot).reactivate();
    }

    @Test
    public void testTestWithoutSnapshot() {
        try {
            new PredicateWithContext<>(null, input -> true);
            fail("Exception expected");
        } catch (RuntimeException expected) {
            assertThat(expected, hasToString(containsString("No context snapshot provided")));
        }
    }

    @Test
    public void testTestWithoutSnapshotSupplier() {
        try {
            new PredicateWithContext<>((Supplier<ContextSnapshot>) null, input -> true, context -> {
            });
            fail("Exception expected");
        } catch (RuntimeException expected) {
            assertThat(expected, hasToString(containsString("No context snapshot supplier provided")));
        }
    }

    @Test
    public void testTestWithSnapshotConsumer() throws InterruptedException {
        final ContextSnapshot[] snapshotHolder = new ContextSnapshot[1];
        DummyContextManager.setCurrentValue("Old value");

        Thread t = new Thread(() -> new PredicateWithContext<>(snapshot,
                input -> {
                    DummyContextManager.setCurrentValue("New value");
                    return true;
                },
                snapshot -> snapshotHolder[0] = snapshot).test("input"));
        t.start();
        t.join();

        assertThat(DummyContextManager.currentValue(), is(Optional.of("Old value")));
        try (Context<Void> reactivation = snapshotHolder[0].reactivate()) {
            assertThat(DummyContextManager.currentValue(), is(Optional.of("New value")));
        }
        assertThat(DummyContextManager.currentValue(), is(Optional.of("Old value")));

        verify(snapshot).reactivate();
    }

    @Test
    public void testCloseReactivatedContextInCaseOfException() {
        when(snapshot.reactivate()).thenReturn(context);
        final RuntimeException expectedException = new RuntimeException("Whoops!");

        try {
            new PredicateWithContext<>(snapshot, throwing(expectedException)).test("input");
            fail("Exception expected");
        } catch (RuntimeException rte) {
            assertThat(rte, is(sameInstance(expectedException)));
        }

        verify(snapshot).reactivate();
        verify(context).close();
    }

    @Test
    public void testAndOtherNull() {
        try {
            new PredicateWithContext<>(snapshot, input -> true).and(null);
            fail("Exception expected");
        } catch (NullPointerException expected) {
            assertThat(expected, hasToString(containsString("'and' <null>")));
        }
    }

    @Test
    public void testAnd_singleContextSwitch() {
        when(snapshot.reactivate()).thenReturn(context);
        Predicate<String> predicate = Objects::nonNull;
        Predicate<String> and = String::isEmpty;

        AtomicInteger consumed = new AtomicInteger(0);
        Predicate<String> combined = new PredicateWithContext<>(snapshot, predicate, snapshot -> consumed.incrementAndGet()).and(and);

        assertThat(combined.test(""), is(true));
        verify(snapshot, times(1)).reactivate();
        verify(context, times(1)).close();
        assertThat(consumed.get(), is(1));
    }

    @Test
    public void testAnd_shortCircuit() {
        when(snapshot.reactivate()).thenReturn(context);
        Predicate<String> predicate = Objects::nonNull;
        @SuppressWarnings("unchecked")
        Predicate<String> and = mock(Predicate.class);

        AtomicInteger consumed = new AtomicInteger(0);
        Predicate<String> combined = new PredicateWithContext<>(snapshot, predicate, snapshot -> consumed.incrementAndGet()).and(and);

        assertThat(combined.test(null), is(false));
        verifyNoMoreInteractions(and);

        verify(snapshot, times(1)).reactivate();
        verify(context, times(1)).close();
        assertThat(consumed.get(), is(1));
    }

    @Test
    public void testOrOtherNull() {
        try {
            new PredicateWithContext<>(snapshot, input -> true).or(null);
            fail("Exception expected");
        } catch (NullPointerException expected) {
            assertThat(expected, hasToString(containsString("'or' <null>")));
        }
    }

    @Test
    public void tesOr_singleContextSwitch() {
        when(snapshot.reactivate()).thenReturn(context);
        Predicate<String> predicate = Objects::isNull;
        Predicate<String> or = String::isEmpty;

        AtomicInteger consumed = new AtomicInteger(0);
        Predicate<String> combined = new PredicateWithContext<>(snapshot, predicate, snapshot -> consumed.incrementAndGet()).or(or);

        assertThat(combined.test(""), is(true));
        verify(snapshot, times(1)).reactivate();
        verify(context, times(1)).close();
        assertThat(consumed.get(), is(1));
    }

    @Test
    public void testOr_shortCircuit() {
        when(snapshot.reactivate()).thenReturn(context);
        Predicate<String> predicate = Objects::isNull;
        @SuppressWarnings("unchecked")
        Predicate<String> or = mock(Predicate.class);

        AtomicInteger consumed = new AtomicInteger(0);
        Predicate<String> combined = new PredicateWithContext<>(snapshot, predicate, snapshot -> consumed.incrementAndGet()).or(or);

        assertThat(combined.test(null), is(true));
        verifyNoMoreInteractions(or);

        verify(snapshot, times(1)).reactivate();
        verify(context, times(1)).close();
        assertThat(consumed.get(), is(1));
    }

    private static <T> Predicate<T> throwing(RuntimeException rte) {
        return input -> {
            throw rte;
        };
    }
}
