package com.vectornode.memory.entity;

import lombok.*;

import java.io.Serializable;

/**
 * Composite primary key for the Relation entity.
 * Matches PDF schema: PRIMARY KEY (source_id, target_id, relation_type)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelationId implements Serializable {
    private Integer sourceId;
    private Integer targetId;
    private String relationType;
}
