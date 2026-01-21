package com.flagarchive.flags.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.flagarchive.flags.model.FlagEntity;

@Repository
public interface FlagEntityRepository extends JpaRepository<FlagEntity, Integer> {
  Optional<FlagEntity> getFlagEntityById(int id);
}
