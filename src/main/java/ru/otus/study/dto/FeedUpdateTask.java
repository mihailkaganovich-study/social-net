
package ru.otus.study.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedUpdateTask implements Serializable {
    private UUID postId;
    private UUID authorId;
    private List<UUID> friendIds;
    private int batchIndex;
    private int totalBatches;
    private long timestamp;
}