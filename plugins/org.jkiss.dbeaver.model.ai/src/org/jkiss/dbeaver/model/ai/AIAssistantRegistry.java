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

import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.jkiss.dbeaver.DBException;
import org.jkiss.utils.CommonUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public class AIAssistantRegistry {

    private static AIAssistantRegistry instance = null;

    public synchronized static AIAssistantRegistry getInstance() {
        if (instance == null) {
            instance = new AIAssistantRegistry(Platform.getExtensionRegistry());
        }
        return instance;
    }

    private final Map<String, AIAssistantDescriptor> descriptorMap = new LinkedHashMap<>();
    private final Map<String, String> replaceMap = new LinkedHashMap<>();

    public AIAssistantRegistry(IExtensionRegistry registry) {
        var extElements = registry.getConfigurationElementsFor("com.dbeaver.ai.assistant");
        for (var ext : extElements) {
            if ("assistant".equals(ext.getName())) {
                var descriptor = new AIAssistantDescriptor(ext);
                descriptorMap.put(descriptor.getId(), descriptor);

                var replaces = descriptor.getReplaces();
                if (!CommonUtils.isEmpty(replaces)) {
                    for (String rl : replaces.split(",")) {
                        replaceMap.put(rl, descriptor.getId());
                    }
                }
            }
        }
    }

    public AIAssistant getAssistant(String id) throws DBException {
        while (true) {
            var replace = replaceMap.get(id);
            if (replace == null) {
                break;
            }
            id = replace;
        }
        var descriptor = descriptorMap.get(id);
        if (descriptor == null) {
            throw new DBException("AI assistant '" + id + "' not found");
        }
        return descriptor.createInstance();
    }
}
