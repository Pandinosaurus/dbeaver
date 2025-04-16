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
import org.jkiss.dbeaver.model.sql.semantics.context.SQLQueryDataContext;
import org.jkiss.dbeaver.model.sql.semantics.solver.SQLQuerySemanticEdgeDeclarator;
import org.jkiss.dbeaver.model.sql.semantics.solver.SQLQuerySemanticSolver;

public class SQLQueryDelayedDataContext {

    @NotNull
    private final SQLQueryLazyDataContext delayedContext;

    @NotNull
    private final SQLQuerySemanticEdgeDeclarator<SQLQueryDataContext> contextDeclarator;

    public SQLQueryDelayedDataContext(
        @NotNull SQLQuerySemanticSolver solver,
        @NotNull SQLQueryDataContext rootContext,
        @NotNull SQLQuerySemanticEdgeDeclarator<SQLQueryDataContext> contextDeclarator
    ) {
        this.delayedContext = new SQLQueryLazyDataContext(solver, rootContext, contextDeclarator.edge());
        this.contextDeclarator = contextDeclarator;
    }

    @NotNull
    public SQLQueryLazyDataContext getDelayedContext() {
        return this.delayedContext;
    }

    public void setRealContext(@NotNull SQLQueryLazyDataContext sourceResult) {
        this.contextDeclarator.providedBy(sourceResult.getUnderlyingEdge());

    }

}
