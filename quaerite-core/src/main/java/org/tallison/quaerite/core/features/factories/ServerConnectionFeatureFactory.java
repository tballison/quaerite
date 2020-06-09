/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.tallison.quaerite.core.features.factories;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.tallison.quaerite.core.ServerConnection;
import org.tallison.quaerite.core.util.MathUtil;

public class ServerConnectionFeatureFactory
        extends AbstractFeatureFactory<ServerConnection> {
    private final static String NAME = "serverConnection";

    private List<ServerConnection> serverConnections;
    public ServerConnectionFeatureFactory(List<ServerConnection> serverConnections) {
        super(NAME);
        this.serverConnections = serverConnections;
    }

    @Override
    public List<ServerConnection> permute(int maxSize) {
        List<ServerConnection> permutations = new ArrayList<>();
        for (ServerConnection serverConnection : serverConnections) {
            permutations.add(serverConnection);
        }
        return permutations;
    }

    @Override
    public ServerConnection random() {
        return serverConnections.get(MathUtil.getRandomInt(0,
                serverConnections.size()));
    }

    @Override
    public ServerConnection mutate(ServerConnection feature,
                                   double probability, double amplitude) {
        return feature;
    }

    @Override
    public Pair<ServerConnection, ServerConnection> crossover(
            ServerConnection parentA, ServerConnection parentB) {
        if (MathUtil.RANDOM.nextFloat() < 0.5f) {
            return Pair.of(parentA, parentB);
        }
        return Pair.of(parentB, parentA);
    }
}
