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
package org.jkiss.dbeaver.model.ai;

import org.eclipse.core.runtime.IConfigurationElement;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.impl.AbstractDescriptor;
import org.jkiss.dbeaver.registry.RegistryConstants;
import org.jkiss.utils.CommonUtils;

public class AIAssistantDescriptor extends AbstractDescriptor {

    private final String id;
    private String replaces;
    private final ObjectType objectType;

    protected AIAssistantDescriptor(IConfigurationElement contributorConfig) {
        super(contributorConfig);
        this.id = contributorConfig.getAttribute(RegistryConstants.ATTR_ID);
        String replacesAttr = contributorConfig.getAttribute("replaces");
        if (!CommonUtils.isEmpty(replacesAttr)) {
            this.replaces = replacesAttr;
        }
        this.objectType = new ObjectType(contributorConfig, RegistryConstants.ATTR_CLASS);
    }

    public AIAssistant createInstance() throws DBException {
        return objectType.createInstance(AIAssistant.class);
    }

    public String getId() {
        return id;
    }

    public String getReplaces() {
        return replaces;
    }
}