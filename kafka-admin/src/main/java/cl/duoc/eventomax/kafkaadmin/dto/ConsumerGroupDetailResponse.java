package cl.duoc.eventomax.kafkaadmin.dto;

import java.util.List;

public record ConsumerGroupDetailResponse(
        String groupId,
        String state,
        String partitionAssignor,
        int coordinatorId,
        List<MemberInfoResponse> members
) {
    public record MemberInfoResponse(
            String memberId,
            String clientId,
            String host,
            List<String> assignedPartitions
    ) {}
}

