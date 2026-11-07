package com.omernaci.miniflow.workflow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowRepository extends JpaRepository<WorkflowEntity, String> {

    List<WorkflowEntity> findByActiveTrue();
}
