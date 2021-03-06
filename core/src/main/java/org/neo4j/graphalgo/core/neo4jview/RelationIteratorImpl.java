/**
 * Copyright (c) 2017 "Neo4j, Inc." <http://neo4j.com>
 *
 * This file is part of Neo4j Graph Algorithms <http://github.com/neo4j-contrib/neo4j-graph-algorithms>.
 *
 * Neo4j Graph Algorithms is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.graphalgo.core.neo4jview;

import org.neo4j.cursor.Cursor;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.RelationshipCursor;
import org.neo4j.graphalgo.core.utils.IdCombiner;
import org.neo4j.graphalgo.core.utils.RawValues;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.api.ReadOperations;
import org.neo4j.kernel.api.Statement;
import org.neo4j.kernel.api.exceptions.EntityNotFoundException;
import org.neo4j.kernel.impl.api.store.RelationshipIterator;
import org.neo4j.kernel.impl.core.ThreadToStatementContextBridge;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.storageengine.api.RelationshipItem;

import java.io.Closeable;
import java.util.Iterator;

class RelationIteratorImpl implements Iterator<RelationshipCursor>, Closeable {

    private final Graph graph;
    private final Transaction transaction;
    private final Statement statement;
    private final RelationshipIterator iterator;
    private final ReadOperations read;
    private final RelationshipCursor cursor;
    private final long originalNodeId;
    private final IdCombiner relId;

    RelationIteratorImpl(Graph graph, GraphDatabaseAPI api, int sourceNodeId, Direction direction, int relationTypeId) throws EntityNotFoundException {
        this.graph = graph;
        transaction = api.beginTx();
        statement = api.getDependencyResolver()
                .resolveDependency(ThreadToStatementContextBridge.class)
                .get();
        read = statement.readOperations();
        originalNodeId = graph.toOriginalNodeId(sourceNodeId);
        if (relationTypeId == ReadOperations.ANY_RELATIONSHIP_TYPE) {
            iterator = read.nodeGetRelationships(originalNodeId, direction);
        } else {
            iterator = read.nodeGetRelationships(originalNodeId, direction, new int[]{relationTypeId});
        }
        cursor = new RelationshipCursor();
        cursor.sourceNodeId = sourceNodeId;
        relId = RawValues.combiner(direction);
    }

    @Override
    public boolean hasNext() {
        final boolean hasNext = iterator.hasNext();
        if (!hasNext) {
            close();
        }
        return hasNext;
    }

    @Override
    public RelationshipCursor next() {
        try {
            final long relationId = iterator.next();
            final Cursor<RelationshipItem> relCursor;
            relCursor = read.relationshipCursorById(relationId);
            relCursor.next();
            final RelationshipItem item = relCursor.get();
            cursor.targetNodeId = graph.toMappedNodeId(item.otherNode(originalNodeId));
            cursor.relationshipId = relId.apply(cursor);
            return cursor;
        } catch (EntityNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        statement.close();
        transaction.success();
        transaction.close();
    }
}
