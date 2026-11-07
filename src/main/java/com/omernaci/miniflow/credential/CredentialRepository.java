package com.omernaci.miniflow.credential;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CredentialRepository extends JpaRepository<CredentialEntity, Long> {

    Optional<CredentialEntity> findByName(String name);

    boolean existsByName(String name);

    List<CredentialEntity> findAllByOrderByNameAsc();
}
