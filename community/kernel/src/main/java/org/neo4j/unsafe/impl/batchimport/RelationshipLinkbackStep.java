/**
 * Copyright (c) 2002-2014 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
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
package org.neo4j.unsafe.impl.batchimport;

import org.neo4j.graphdb.Direction;
import org.neo4j.kernel.impl.nioneo.store.InvalidRecordException;
import org.neo4j.kernel.impl.nioneo.store.RelationshipRecord;
import org.neo4j.kernel.impl.nioneo.store.RelationshipStore;
import org.neo4j.unsafe.impl.batchimport.cache.NodeRelationshipLink;
import org.neo4j.unsafe.impl.batchimport.staging.LonelyProcessingStep;
import org.neo4j.unsafe.impl.batchimport.staging.StageControl;

/**
 * Links the {@code previous} fields in {@link RelationshipRecord relationship records}. This is done after
 * a forward pass where the {@code next} fields are linked.
 */
public class RelationshipLinkbackStep extends LonelyProcessingStep
{
    private final RelationshipStore relStore;
    private final NodeRelationshipLink nodeRelationshipLink;

    public RelationshipLinkbackStep( StageControl control, int batchSize,
            RelationshipStore relStore, NodeRelationshipLink nodeRelationshipLink )
    {
        super( control, "LINKER", batchSize );
        this.relStore = relStore;
        this.nodeRelationshipLink = nodeRelationshipLink;
    }

    @Override
    protected void process()
    {
        long highId = relStore.getHighestPossibleIdInUse();
        RelationshipRecord heavilyReusedRecord = new RelationshipRecord( -1 );
        for ( long i = highId; i >= 0; i-- )
        {
            RelationshipRecord record;
            try
            {
                record = relStore.getRecord( i, heavilyReusedRecord );
            }
            catch ( InvalidRecordException e )
            {
                // It's OK
                continue;
            }

            boolean isLoop = record.getFirstNode() == record.getSecondNode();
            if ( isLoop )
            {
                long prevRel = nodeRelationshipLink.getAndPutRelationship( record.getFirstNode(),
                        record.getType(), Direction.BOTH, record.getId(), false );
                if ( prevRel == -1 )
                {   // First one
                    record.setFirstInFirstChain( true );
                    record.setFirstInSecondChain( true );
                    prevRel = nodeRelationshipLink.getCount( record.getFirstNode(),
                            record.getType(), Direction.BOTH );
                }
                record.setFirstPrevRel( prevRel );
                record.setSecondPrevRel( prevRel );
            }
            else
            {
                // Start node
                long firstPrevRel = nodeRelationshipLink.getAndPutRelationship( record.getFirstNode(),
                        record.getType(), Direction.OUTGOING, record.getId(), false );
                if ( firstPrevRel == -1 )
                {   // First one
                    record.setFirstInFirstChain( true );
                    firstPrevRel = nodeRelationshipLink.getCount( record.getFirstNode(),
                            record.getType(), Direction.OUTGOING );
                }
                record.setFirstPrevRel( firstPrevRel );

                // End node
                long secondPrevRel = nodeRelationshipLink.getAndPutRelationship( record.getSecondNode(),
                        record.getType(), Direction.INCOMING, record.getId(), false );
                if ( secondPrevRel == -1 )
                {   // First one
                    record.setFirstInSecondChain( true );
                    secondPrevRel = nodeRelationshipLink.getCount( record.getSecondNode(),
                            record.getType(), Direction.INCOMING );
                }
                record.setSecondPrevRel( secondPrevRel );
            }
            itemProcessed();
            relStore.updateRecord( record );
        }
        relStore.flushAll();
    }
}
