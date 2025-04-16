/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2025 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.model.sql.semantics.solver;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.sql.semantics.SQLQueryRecognitionContext;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SQLQuerySemanticSolver {

    private static final Log log = Log.getLog(SQLQuerySemanticSolver.class);

    @NotNull
    private final AtomicInteger idCounter = new AtomicInteger();
    @NotNull
    private final LinkedBlockingQueue<DepsNode<?>> queuedItems = new LinkedBlockingQueue<>();
    @NotNull
    private final DoubleLinkedList<DepsNode<?>> awaitingItems = new DoubleLinkedList<>();
    @NotNull
    private final SQLQueryRecognitionContext context;
    @NotNull
    private final CompletableFuture<Boolean> isSolved = new CompletableFuture<>();
    @NotNull
    private final AtomicBoolean isJoining = new AtomicBoolean(false);
    @NotNull
    private final Thread solvingThread;

    public SQLQuerySemanticSolver(@NotNull SQLQueryRecognitionContext context) {
        this.context = context;
        this.solvingThread = new Thread(this::doSolve, "Semantic solver");
        this.solvingThread.setDaemon(true);
    }

    @NotNull
    public SQLQueryRecognitionContext getRecognitionContext() {
        return this.context;
    }

    @NotNull
    public <T> SQLQuerySemanticEdgeDeclarator<T> declared() {
        return new SQLQuerySemanticEdgeDeclarator<>() {
            SQLQuerySemanticEdge<T> sourceEdge = null;
            final DepsNode<T> item = new DepsNode<T>() {
                @Override
                protected T doWorkImpl() {
                    if (sourceEdge == null) {
                        throw new IllegalStateException("Value provider should be specified");
                    } else {
                        return sourceEdge.value();
                    }
                }
            };

            @NotNull
            @Override
            public SQLQuerySemanticEdge<T> edge() {
                return this.item;
            }

            @Override
            public void providedBy(@NotNull SQLQuerySemanticEdge<T> sourceEdge) {
                if (this.sourceEdge == null) {
                    this.sourceEdge = sourceEdge;
                    this.item.registerDependency(sourceEdge);
                } else {
                    throw new IllegalStateException();
                }
            }

            @Override
            public void close() {
                if (this.sourceEdge == null) {
                    throw new IllegalStateException();
                } else {
                    this.item.enqueueIfReady();
                }
            }
        };
    }

    @NotNull
    public <T> SQLQuerySemanticEdge<T> prepared(@NotNull T value) {
        return this.prepare(s -> value);
    }

    @NotNull
    public <T> SQLQuerySemanticEdge<T> prepare(SemanticProducer0<T> producer) {
        DepsNode<T> item = new DepsNode<>() {
            @Override
            protected T doWorkImpl() {
                return producer.produce(SQLQuerySemanticSolver.this.context);
            }
        };
        this.queuedItems.add(item);
        return item;
    }

    @NotNull
    public <A> FutureSemantic1<A> with(@NotNull SQLQuerySemanticEdge<A> a) {
        return new FutureSemantic1<A>() {
            @Override
            public <T> SQLQuerySemanticEdge<T> prepare(SemanticProducer1<A, T> producer) {
                return new DepsNode<T>(a) {
                    @Override
                    protected T doWorkImpl() {
                        return producer.produce(a.source().value(), SQLQuerySemanticSolver.this.context);
                    }
                };
            }
        };
    }

    @NotNull
    public <A1, A2> FutureSemantic2<A1, A2> with(@NotNull SQLQuerySemanticEdge<A1> a1, @NotNull SQLQuerySemanticEdge<A2> a2) {
        return new FutureSemantic2<A1, A2>() {
            @Override
            public <T> SQLQuerySemanticEdge<T> prepare(SemanticProducer2<A1, A2, T> producer) {
                return new DepsNode<T>(a1, a2) {
                    @Override
                    protected T doWorkImpl() {
                        return producer.produce(a1.source().value(), a2.source().value(), SQLQuerySemanticSolver.this.context);
                    }
                };
            }
        };
    }

    @NotNull
    public <A1, A2, A3> FutureSemantic3<A1, A2, A3> with(
        @NotNull SQLQuerySemanticEdge<A1> a1,
        @NotNull SQLQuerySemanticEdge<A2> a2,
        @NotNull SQLQuerySemanticEdge<A3> a3
    ) {
        return new FutureSemantic3<A1, A2, A3>() {
            @Override
            public <T> SQLQuerySemanticEdge<T> prepare(SemanticProducer3<A1, A2, A3, T> producer) {
                return new DepsNode<T>(a1, a2, a3) {
                    @Override
                    protected T doWorkImpl() {
                        return producer.produce(a1.source().value(),
                            a2.source().value(),
                            a3.source().value(),
                            SQLQuerySemanticSolver.this.context
                        );
                    }
                };
            }
        };
    }

    @NotNull
    public <A> FutureSemanticN<A> with(@NotNull SQLQuerySemanticEdge<A>... args) {
        return new FutureSemanticN<A>() {
            @Override
            public <T> SQLQuerySemanticEdge<T> prepare(SemanticProducerN<A, T> producer) {
                return new DepsNode<T>(args) {
                    @Override
                    protected T doWorkImpl() {
                        @SuppressWarnings("unchecked") A[] values = (A[]) new Object[args.length];
                        for (int i = 0; i < args.length; i++) {
                            values[i] = args[i].source().value();
                        }
                        return producer.produce(values, SQLQuerySemanticSolver.this.context);
                    }
                };
            }
        };
    }

    public void start() {
        this.solvingThread.start();
    }

    public boolean join() {
        this.isJoining.set(true);
        try {
            return this.isSolved.get();
        } catch (InterruptedException | ExecutionException e) {
            log.error(e);
            return false;
        }
    }

    private void doSolve() {
        try {
            while (!this.context.getMonitor().isCanceled() && ((!this.isJoining.get() && !this.awaitingItems.isEmpty()) || (
                this.isJoining.get() && !this.queuedItems.isEmpty()))) {
                DepsNode<?> node = this.queuedItems.poll(100, TimeUnit.MILLISECONDS);
                if (node != null) {
                    node.doWork();
                }
            }
            this.isSolved.complete(true);
        } catch (RuntimeException | InterruptedException e) {
            log.error(e);
            this.isSolved.complete(false);
        }
    }

    @FunctionalInterface
    public interface SemanticProducer0<T> {
        T produce(SQLQueryRecognitionContext ctx);
    }

    @FunctionalInterface
    public interface SemanticProducer1<A, T> {
        T produce(A arg, SQLQueryRecognitionContext ctx);
    }

    @FunctionalInterface
    public interface SemanticProducer1N<A, AN, T> {
        T produce(A arg1, AN[] args2, SQLQueryRecognitionContext ctx);
    }

    @FunctionalInterface
    public interface SemanticProducer2<A1, A2, T> {
        T produce(A1 arg1, A2 arg2, SQLQueryRecognitionContext ctx);
    }

    @FunctionalInterface
    public interface SemanticProducer3<A1, A2, A3, T> {
        T produce(A1 arg1, A2 arg2, A3 arg3, SQLQueryRecognitionContext ctx);
    }

    @FunctionalInterface
    public interface SemanticProducerN<A, T> {
        T produce(A[] args, SQLQueryRecognitionContext ctx);
    }

    public interface FutureSemantic0 extends SQLQuerySemanticNode {
        <T> SQLQuerySemanticEdge<T> prepare(SemanticProducer0<T> producer);
    }

    public interface FutureSemantic1<A> extends SQLQuerySemanticNode {
        <T> SQLQuerySemanticEdge<T> prepare(SemanticProducer1<A, T> producer);
    }

    public interface FutureSemantic1N<A, AN> extends SQLQuerySemanticNode {
        <T> SQLQuerySemanticEdge<T> prepare(SemanticProducer1N<A, AN, T> producer);
    }

    public interface FutureSemantic2<A1, A2> extends SQLQuerySemanticNode {
        <T> SQLQuerySemanticEdge<T> prepare(SemanticProducer2<A1, A2, T> producer);
    }

    public interface FutureSemantic3<A1, A2, A3> extends SQLQuerySemanticNode {
        <T> SQLQuerySemanticEdge<T> prepare(SemanticProducer3<A1, A2, A3, T> producer);
    }

    public interface FutureSemanticN<A> extends SQLQuerySemanticNode {
        <T> SQLQuerySemanticEdge<T> prepare(SemanticProducerN<A, T> producer);
    }

    abstract class DepsNode<T> extends SQLQuerySemanticEdge<T> {
        private final DoubleLinkedList.Item<DepsNode<?>> listNode = new DoubleLinkedList.Item<>(this);

        private final Set<DepsNode<?>> consumers = new HashSet<>();
        private final Set<DepsNode<?>> sources;
        private final Set<DepsNode<?>> sourcesPrepared;
        private final int id = idCounter.incrementAndGet();
        private volatile T value;
        private volatile boolean isPrepared = false;

        private DepsNode() {
            this.sources = new HashSet<>();
            this.sourcesPrepared = new HashSet<>();
            SQLQuerySemanticSolver.this.awaitingItems.addLast(this.listNode);
        }

        private DepsNode(SQLQuerySemanticEdge<?>... sourcesEdges) {
            this.sources = new HashSet<>(sourcesEdges.length);
            this.sourcesPrepared = new HashSet<>(sourcesEdges.length);
            SQLQuerySemanticSolver.this.awaitingItems.addLast(this.listNode);
            synchronized (this) { // TODO consider lock order
                for (SQLQuerySemanticEdge<?> sourceEdge : sourcesEdges) {
                    this.registerDependency(sourceEdge);
                }
                this.enqueueIfReady();
            }
        }

        @Override
        public DepsNode<T> source() {
            return this;
        }

        @Override
        public boolean isPrepared() {
            return this.isPrepared;
        }

        @Override
        public T value() {
            if (this.isPrepared) {
                return this.value;
            } else {
                throw new IllegalStateException(); // should never happen
            }
        }

        protected abstract T doWorkImpl();

        private void registerDependency(@NotNull SQLQuerySemanticEdge<?> sourceEdge) {
            System.out.println(this.id + " <-- " + sourceEdge.source().id);
            synchronized (this) {
                DepsNode<?> source = sourceEdge.source();
                if (this.sources.add(source)) {
                    source.consumers.add(this);
                    if (source.isPrepared) {
                        this.sourcesPrepared.add(source);
                    }
                } else {
                    throw new IllegalArgumentException("Duplicated dependency");
                }
            }
        }

        private void doWork() {
            if (this.isPrepared) {
                throw new IllegalStateException(); // should never happen
            }

            System.out.println("solving " + this.id);
            this.value = this.doWorkImpl();
            this.isPrepared = true;

            if (value == null && !this.consumers.isEmpty()) {
                throw new IllegalStateException("Expected materialized result to provide for consumers");
            }

            for (DepsNode<?> consumer : this.consumers) {
                consumer.promoteSource(this);
            }
        }

        private void promoteSource(@NotNull DepsNode<?> source) {
            // System.out.println("promoting " + source.id + " for " + this.id);
            if (!this.sources.contains(source)) {
                throw new IllegalStateException(); // should never happen
            }

            if (this.sourcesPrepared.add(source)) {
                //                for (DepsNode<?> consumer : source.consumers) {
                //                    consumer.promoteSource(source);
                //                }
            }

            this.enqueueIfReady();
        }

        private void enqueueIfReady() {
            if (this.sourcesPrepared.size() >= this.sources.size() && this.listNode.belongsTo(SQLQuerySemanticSolver.this.awaitingItems)) {
                System.out.println(this.id + " is ready");
                SQLQuerySemanticSolver.this.awaitingItems.remove(this.listNode);
                SQLQuerySemanticSolver.this.queuedItems.add(this);
            } else {
                // System.out.println(this.id + " is not ready yet");
            }
        }
    }
}
