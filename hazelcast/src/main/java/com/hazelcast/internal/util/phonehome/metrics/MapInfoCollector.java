/*
 * Copyright (c) 2008-2020, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hazelcast.internal.util.phonehome.metrics;

import com.hazelcast.core.DistributedObject;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static java.util.stream.Collectors.toList;

import com.hazelcast.instance.impl.Node;
import com.hazelcast.internal.util.phonehome.MetricsCollector;
import com.hazelcast.map.impl.MapService;

public class MapInfoCollector implements MetricsCollector {

    Collection<DistributedObject> maps;

    @Override
    public Map<String, String> computeMetrics(Node hazelcastNode) {

        Collection<DistributedObject> distributedObjects = hazelcastNode.hazelcastInstance.getDistributedObjects();
        maps = distributedObjects.stream().filter(distributedObject -> distributedObject.getServiceName().
                equals(MapService.SERVICE_NAME)).collect(toList());
        Map<String, String> mapInfo = new HashMap<>();

        mapInfo.put("mpct", String.valueOf(getMapCount()));

        return mapInfo;
    }

    public int getMapCount() {
        return maps.size();
    }
}
