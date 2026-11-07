package com.omernaci.miniflow.node.core;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SeenKeyRepository extends JpaRepository<SeenKeyEntity, Long> {

    boolean existsByWorkflowIdAndKeyValue(String workflowId, String keyValue);

    long countByWorkflowId(String workflowId);

    @Transactional
    @Modifying
    @Query("delete from SeenKeyEntity s where s.workflowId = :workflowId")
    int deleteByWorkflowId(@Param("workflowId") String workflowId);
}
