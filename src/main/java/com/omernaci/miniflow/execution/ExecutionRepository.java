package com.omernaci.miniflow.execution;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface ExecutionRepository extends JpaRepository<ExecutionEntity, String> {

    List<ExecutionEntity> findAllByOrderByStartedAtDesc(Pageable pageable);

    List<ExecutionEntity> findByWorkflowIdOrderByStartedAtDesc(String workflowId, Pageable pageable);

    long countByStatus(String status);

    @Query("select e from ExecutionEntity e where (:workflowId is null or e.workflowId = :workflowId)"
            + " and (:status is null or e.status = :status) and (:mode is null or e.runMode = :mode)")
    Page<ExecutionEntity> search(@Param("workflowId") String workflowId, @Param("status") String status,
                                 @Param("mode") String mode, Pageable pageable);

    @Query("select e.workflowId, e.workflowName, e.status, e.runMode, e.startedAt, e.durationMs"
            + " from ExecutionEntity e where e.startedAt >= :since")
    List<Object[]> statsSince(@Param("since") LocalDateTime since);

    List<ExecutionEntity> findByStatusOrderByStartedAtDesc(String status, Pageable pageable);

    @Transactional
    @Modifying
    @Query("delete from ExecutionEntity e where e.startedAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);

    @Transactional
    @Modifying
    @Query("delete from ExecutionEntity e where e.workflowId = :workflowId")
    int deleteByWorkflowId(@Param("workflowId") String workflowId);
}
