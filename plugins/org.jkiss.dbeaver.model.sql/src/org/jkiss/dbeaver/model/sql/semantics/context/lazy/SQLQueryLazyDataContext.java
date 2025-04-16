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
package org.jkiss.dbeaver.model.sql.semantics.context.lazy;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.model.sql.semantics.SQLQueryRecognitionContext;
import org.jkiss.dbeaver.model.sql.semantics.SQLQuerySymbol;
import org.jkiss.dbeaver.model.sql.semantics.context.SQLQueryDataContext;
import org.jkiss.dbeaver.model.sql.semantics.context.SQLQueryResultColumn;
import org.jkiss.dbeaver.model.sql.semantics.context.SQLQueryResultPseudoColumn;
import org.jkiss.dbeaver.model.sql.semantics.model.select.SQLQueryRowsSourceModel;
import org.jkiss.dbeaver.model.sql.semantics.solver.SQLQuerySemanticEdge;
import org.jkiss.dbeaver.model.sql.semantics.solver.SQLQuerySemanticSolver;
import org.jkiss.dbeaver.model.sql.semantics.solver.SQLQuerySemanticSolver.SemanticProducer1;
import org.jkiss.dbeaver.model.sql.semantics.solver.SQLQuerySemanticSolver.SemanticProducer2;
import org.jkiss.dbeaver.model.struct.DBSEntity;
import org.jkiss.utils.Pair;

import java.util.List;

/**
 * Semantic context query information about entities involved in the semantics model
 */
public class SQLQueryLazyDataContext {

    @NotNull
    private final SQLQuerySemanticSolver solver;
    @NotNull
    private final SQLQueryDataContext rootContext;
    @NotNull
    private final SQLQuerySemanticEdge<SQLQueryDataContext> contextEdge;

    public SQLQueryLazyDataContext(
        @NotNull SQLQuerySemanticSolver solver,
        @NotNull SQLQueryDataContext rootContext,
        @NotNull SQLQuerySemanticEdge<SQLQueryDataContext> contextEdge
    ) {
        this.solver = solver;
        this.rootContext = rootContext;
        this.contextEdge = contextEdge;
    }

    @NotNull
    public SQLQueryRecognitionContext getRecognitionContext() {
        return this.solver.getRecognitionContext();
    }

    @NotNull
    public SQLQueryDataContext getRootContext() {
        return this.rootContext;
    }

    @NotNull
    public SQLQuerySemanticEdge<SQLQueryDataContext> getUnderlyingEdge() {
        return this.contextEdge;
    }

    @NotNull
    public SQLQueryDelayedDataContext makeDelayedContext() {
        return new SQLQueryDelayedDataContext(this.solver, this.rootContext, this.solver.declared());
    }

    @NotNull
    public SQLQueryLazyDataContext makePreparedContext(@NotNull SQLQueryDataContext preparedContext) {
        return new SQLQueryLazyDataContext(this.solver, this.rootContext, this.solver.prepared(preparedContext));
    }

    @NotNull
    public SQLQueryLazyDataContext transform(@NotNull SemanticProducer1<SQLQueryDataContext, SQLQueryDataContext> producer) {
        return new SQLQueryLazyDataContext(this.solver, this.rootContext, this.solver.with(this.contextEdge).prepare(producer));
    }

    @NotNull
    public SQLQueryLazyDataContext transform(
        @NotNull SQLQueryLazyDataContext other,
        @NotNull SemanticProducer2<SQLQueryDataContext, SQLQueryDataContext, SQLQueryDataContext> producer
    ) {
        return new SQLQueryLazyDataContext(
            this.solver,
            this.rootContext,
            this.solver.with(this.contextEdge, other.contextEdge).prepare(producer)
        );
    }

    /**
     * Prepare new semantic context by overriding result tuple columns information
     */
    @NotNull
    public final SQLQueryLazyDataContext overrideResultTuple(
        @Nullable SQLQueryRowsSourceModel source,
        @NotNull SemanticProducer1<SQLQueryDataContext, Pair<List<SQLQueryResultColumn>, List<SQLQueryResultPseudoColumn>>> columnsAndPseudoColumnsProducer
    ) {
        return this.transform((c, s) -> c.overrideResultTuple(source, columnsAndPseudoColumnsProducer.produce(c, s)));
    }


    /**
     * Prepare new semantic context by combining this context with the other given context
     */
    @NotNull
    public final SQLQueryLazyDataContext combine(@NotNull SQLQueryLazyDataContext other) {
        return this.transform(other, (a, b, s) -> a.combineForJoin(b));
    }

    /**
     * Prepare new semantic context by combining this context with the other given context
     */
    @NotNull
    public final SQLQueryLazyDataContext combineForJoin(@NotNull SQLQueryLazyDataContext other) {
        return this.transform(other, (a, b, s) -> a.combine(b));
    }

    /**
     * Prepare new semantic context by introducing real table reference
     */
    @NotNull
    public final SQLQueryLazyDataContext extendWithRealTable(
        @NotNull SemanticProducer1<SQLQueryDataContext, DBSEntity> table,
        @NotNull SQLQueryRowsSourceModel source
    ) {
        return this.transform((c, s) -> c.extendWithRealTable(table.produce(c, s), source));
    }

    /**
     * Prepare new semantic context by introducing rows source alias (table reference, named subquery, etc)
     */
    @NotNull
    public final SQLQueryLazyDataContext extendWithTableAlias(@NotNull SQLQuerySymbol alias, @NotNull SQLQueryRowsSourceModel source) {
        return this.transform((c, s) -> c.extendWithTableAlias(alias, source));
    }

    /**
     * Prepare new semantic context by hiding all the involved rows sources such as subqueries and table references
     */
    @NotNull
    public final SQLQueryLazyDataContext hideSources() {
        return this.transform((c, s) -> c.hideSources());
    }

    /**
     * Prepare new semantic context by introducing hasUnresolvedSource flag
     */
    public final SQLQueryLazyDataContext markHasUnresolvedSource() {
        return this.transform((c, s) -> c.markHasUnresolvedSource());
    }

}
