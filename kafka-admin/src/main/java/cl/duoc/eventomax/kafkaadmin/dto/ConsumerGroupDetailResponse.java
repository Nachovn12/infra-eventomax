package cl.duoc.eventomax.kafkaadmin.dto;

import java.util.List;

public record ConsumerGroupDetailResponse(
        String groupId,
        String state,
        String partitionAssignor,
        int coordinatorId,
        List<MemberInfoResponse> members,
        long totalLag,
        List<PartitionOffsetResponse> partitionOffsets
) {
    public ConsumerGroupDetailResponse(
            String groupId,
            String state,
            String partitionAssignor,
            int coordinatorId,
            List<MemberInfoResponse> members
    ) {
        this(groupId, state, partitionAssignor, coordinatorId, members, 0L, List.of());
    }

    public record MemberInfoResponse(
            String memberId,
            String clientId,
            String host,
            List<String> assignedPartitions
    ) {}

    public record PartitionOffsetResponse(
            String topic,
            int partition,
            long currentOffset,
            long endOffset,
            long lag
    ) {}
}

