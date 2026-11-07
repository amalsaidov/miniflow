package com.omernaci.miniflow.variable;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VariableRepository extends JpaRepository<VariableEntity, Long> {

    Optional<VariableEntity> findByName(String name);

    Optional<VariableEntity> findByNameIgnoreCase(String name);

    List<VariableEntity> findAllByOrderByNameAsc();
}
