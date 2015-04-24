/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tinkerpop.gremlin.structure.io.graphml;

import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.io.GraphReader;
import org.apache.tinkerpop.gremlin.structure.io.Io;
import org.apache.tinkerpop.gremlin.structure.util.Attachable;
import org.apache.tinkerpop.gremlin.structure.util.batch.BatchGraph;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * GraphMLReader writes the data from a GraphML stream to a graph.  Note that this format is lossy, in the sense that data
 * types and features of Gremlin Structure not supported by GraphML are not serialized.  This format is meant for
 * external export of a graph to tools outside of Gremlin Structure graphs.  Note that GraphML does not support
 * the notion of multi-properties or properties on properties.
 *
 * @author Marko A. Rodriguez (http://markorodriguez.com)
 * @author Alex Averbuch (alex.averbuch@gmail.com)
 * @author Joshua Shinavier (http://fortytwo.net)
 * @author Stephen Mallette (http://stephen.genoprime.com)
 */
public class GraphMLReader implements GraphReader {
    private final XMLInputFactory inputFactory = XMLInputFactory.newInstance();

    private final String vertexIdKey;
    private final String edgeIdKey;
    private final String edgeLabelKey;
    private final String vertexLabelKey;
    private final long batchSize;

    private GraphMLReader(final String vertexIdKey, final String edgeIdKey,
                          final String edgeLabelKey, final String vertexLabelKey,
                          final long batchSize) {
        this.vertexIdKey = vertexIdKey;
        this.edgeIdKey = edgeIdKey;
        this.edgeLabelKey = edgeLabelKey;
        this.batchSize = batchSize;
        this.vertexLabelKey = vertexLabelKey;
    }

    @Override
    public Iterator<Vertex> readVertices(final InputStream inputStream,
                                         final Function<Attachable<Vertex>, Vertex> vertexMaker,
                                         final Function<Attachable<Edge>, Edge> edgeMaker,
                                         final Direction attachEdgesOfThisDirection) throws IOException {
        throw Io.Exceptions.readerFormatIsForFullGraphSerializationOnly(this.getClass());
    }

    @Override
    public Edge readEdge(final InputStream inputStream, final Function<Attachable<Edge>, Edge> edgeMaker) throws IOException {
        throw Io.Exceptions.readerFormatIsForFullGraphSerializationOnly(this.getClass());
    }

    @Override
    public Vertex readVertex(final InputStream inputStream, final Function<Attachable<Vertex>, Vertex> vertexMaker) throws IOException {
        throw Io.Exceptions.readerFormatIsForFullGraphSerializationOnly(this.getClass());
    }

    @Override
    public Vertex readVertex(final InputStream inputStream, final Function<Attachable<Vertex>, Vertex> vertexMaker,
                             final Function<Attachable<Edge>, Edge> edgeMaker,
                             final Direction attachEdgesOfThisDirection) throws IOException {
        throw Io.Exceptions.readerFormatIsForFullGraphSerializationOnly(this.getClass());
    }

    @Override
    public <C> C readObject(final InputStream inputStream, final Class<? extends C> clazz) throws IOException {
        throw Io.Exceptions.readerFormatIsForFullGraphSerializationOnly(this.getClass());
    }

    @Override
    public void readGraph(final InputStream graphInputStream, final Graph graphToWriteTo) throws IOException {
        final BatchGraph graph;
        try {
            // will throw an exception if not constructed properly
            graph = BatchGraph.build(graphToWriteTo)
                    .vertexIdKey(vertexIdKey)
                    .bufferSize(batchSize).create();
        } catch (Exception ex) {
            throw new IOException("Could not instantiate BatchGraph wrapper", ex);
        }

        try {
            final XMLStreamReader reader = inputFactory.createXMLStreamReader(graphInputStream);
            final Map<String, String> keyIdMap = new HashMap<>();
            final Map<String, String> keyTypesMaps = new HashMap<>();

            // Buffered Vertex Data
            String vertexId = null;
            String vertexLabel = null;
            Map<String, Object> vertexProps = null;
            boolean isInVertex = false;

            // Buffered Edge Data
            String edgeId = null;
            String edgeLabel = null;
            Vertex edgeInVertex = null;
            Vertex edgeOutVertex = null;
            Map<String, Object> edgeProps = null;
            boolean isInEdge = false;

            while (reader.hasNext()) {
                final Integer eventType = reader.next();
                if (eventType.equals(XMLEvent.START_ELEMENT)) {
                    final String elementName = reader.getName().getLocalPart();

                    switch (elementName) {
                        case GraphMLTokens.KEY:
                            final String id = reader.getAttributeValue(null, GraphMLTokens.ID);
                            final String attributeName = reader.getAttributeValue(null, GraphMLTokens.ATTR_NAME);
                            final String attributeType = reader.getAttributeValue(null, GraphMLTokens.ATTR_TYPE);
                            keyIdMap.put(id, attributeName);
                            keyTypesMaps.put(id, attributeType);
                            break;
                        case GraphMLTokens.NODE:
                            vertexId = reader.getAttributeValue(null, GraphMLTokens.ID);
                            isInVertex = true;
                            vertexProps = new HashMap<>();
                            break;
                        case GraphMLTokens.EDGE:
                            edgeId = reader.getAttributeValue(null, GraphMLTokens.ID);

                            final String vertexIdOut = reader.getAttributeValue(null, GraphMLTokens.SOURCE);
                            final String vertexIdIn = reader.getAttributeValue(null, GraphMLTokens.TARGET);

                            // graphml allows edges and vertices to be mixed in terms of how they are positioned
                            // in the xml therefore it is possible that an edge is created prior to its definition
                            // as a vertex.
                            Iterator<Vertex> iterator = graph.vertices(vertexIdOut);
                            edgeOutVertex = iterator.hasNext() ? iterator.next() : graph.addVertex(T.id, vertexIdOut);
                            iterator = graph.vertices(vertexIdIn);
                            edgeInVertex = iterator.hasNext() ? iterator.next() : graph.addVertex(T.id, vertexIdIn);

                            isInEdge = true;
                            edgeProps = new HashMap<>();

                            break;
                        case GraphMLTokens.DATA:
                            final String key = reader.getAttributeValue(null, GraphMLTokens.KEY);
                            final String dataAttributeName = keyIdMap.get(key);

                            if (dataAttributeName != null) {
                                final String value = reader.getElementText();

                                if (isInVertex) {
                                    if (key.equals(vertexLabelKey))
                                        vertexLabel = value;
                                    else
                                        vertexProps.put(dataAttributeName, typeCastValue(key, value, keyTypesMaps));
                                } else if (isInEdge) {
                                    if (key.equals(edgeLabelKey))
                                        edgeLabel = value;
                                    else if (key.equals(edgeIdKey))
                                        edgeId = value;
                                    else
                                        edgeProps.put(dataAttributeName, typeCastValue(key, value, keyTypesMaps));
                                }
                            }

                            break;
                    }
                } else if (eventType.equals(XMLEvent.END_ELEMENT)) {
                    final String elementName = reader.getName().getLocalPart();

                    if (elementName.equals(GraphMLTokens.NODE)) {
                        final String currentVertexId = vertexId;
                        final String currentVertexLabel = Optional.ofNullable(vertexLabel).orElse(Vertex.DEFAULT_LABEL);
                        final Object[] propsAsArray = vertexProps.entrySet().stream().flatMap(e -> Stream.of(e.getKey(), e.getValue())).toArray();

                        // if incremental loading is on in batchgraph it handles graphml spec where it states that
                        // order of edges/vertices may be mixed such that an edge may be created before an vertex.
                        graph.addVertex(Stream.concat(Stream.of(T.id, currentVertexId, T.label, currentVertexLabel),
                                Stream.of(propsAsArray)).toArray());

                        vertexId = null;
                        vertexLabel = null;
                        vertexProps = null;
                        isInVertex = false;
                    } else if (elementName.equals(GraphMLTokens.EDGE)) {
                        final Object[] propsAsArray = edgeProps.entrySet().stream().flatMap(e -> Stream.of(e.getKey(), e.getValue())).toArray();
                        edgeOutVertex.addEdge(edgeLabel, edgeInVertex, Stream.concat(Stream.of(T.id, edgeId),
                                Stream.of(propsAsArray)).toArray());

                        edgeId = null;
                        edgeLabel = null;
                        edgeOutVertex = null;
                        edgeInVertex = null;
                        edgeProps = null;
                        isInEdge = false;
                    }

                }
            }

            graph.tx().commit();
        } catch (XMLStreamException xse) {
            // rollback whatever portion failed
            graph.tx().rollback();
            throw new IOException(xse);
        }
    }

    private static Object typeCastValue(final String key, final String value, final Map<String, String> keyTypes) {
        final String type = keyTypes.get(key);
        if (null == type || type.equals(GraphMLTokens.STRING))
            return value;
        else if (type.equals(GraphMLTokens.FLOAT))
            return Float.valueOf(value);
        else if (type.equals(GraphMLTokens.INT))
            return Integer.valueOf(value);
        else if (type.equals(GraphMLTokens.DOUBLE))
            return Double.valueOf(value);
        else if (type.equals(GraphMLTokens.BOOLEAN))
            return Boolean.valueOf(value);
        else if (type.equals(GraphMLTokens.LONG))
            return Long.valueOf(value);
        else
            return value;
    }

    public static Builder build() {
        return new Builder();
    }

    /**
     * Allows configuration and construction of the GraphMLReader instance.
     */
    public static final class Builder implements ReaderBuilder<GraphMLReader> {
        private String vertexIdKey = T.id.getAccessor();
        private String edgeIdKey = T.id.getAccessor();
        private String edgeLabelKey = GraphMLTokens.LABEL_E;
        private String vertexLabelKey = GraphMLTokens.LABEL_V;
        private long batchSize = BatchGraph.DEFAULT_BUFFER_SIZE;

        private Builder() {
        }

        /**
         * The name of the key to supply to
         * {@link org.apache.tinkerpop.gremlin.structure.util.batch.BatchGraph.Builder#vertexIdKey} when reading data into
         * the {@link Graph}.
         */
        public Builder vertexIdKey(final String vertexIdKey) {
            this.vertexIdKey = vertexIdKey;
            return this;
        }

        /**
         * The name of the key to supply to
         * {@link org.apache.tinkerpop.gremlin.structure.util.batch.BatchGraph.Builder#edgeIdKey} when reading data into
         * the {@link Graph}.
         */
        public Builder edgeIdKey(final String edgeIdKey) {
            this.edgeIdKey = edgeIdKey;
            return this;
        }

        /**
         * The key to use as the edge label.
         */
        public Builder edgeLabelKey(final String edgeLabelKey) {
            this.edgeLabelKey = edgeLabelKey;
            return this;
        }

        /**
         * the key to use as the vertex label.
         */
        public Builder vertexLabelKey(final String vertexLabelKey) {
            this.vertexLabelKey = vertexLabelKey;
            return this;
        }

        /**
         * Number of mutations to perform before a commit is executed.
         */
        public Builder batchSize(final long batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public GraphMLReader create() {
            return new GraphMLReader(vertexIdKey, edgeIdKey, edgeLabelKey, vertexLabelKey, batchSize);
        }
    }
}
