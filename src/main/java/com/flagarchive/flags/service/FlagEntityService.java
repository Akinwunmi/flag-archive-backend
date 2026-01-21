package com.flagarchive.flags.service;

import java.util.Optional;
import org.springframework.stereotype.Service;

import com.flagarchive.flags.dto.FlagEntityDTO;
import com.flagarchive.flags.model.FlagEntity;
import com.flagarchive.flags.repository.FlagEntityRepository;

@Service
public class FlagEntityService {
  private final FlagEntityRepository flagEntityRepository;

  public FlagEntityService(FlagEntityRepository flagEntityRepository) {
    this.flagEntityRepository = flagEntityRepository;
  }

  public Optional<FlagEntityDTO> getFlagEntityById(int id) {
    Optional<FlagEntity> flagEntityFromDb = flagEntityRepository.getFlagEntityById(id);

    return flagEntityFromDb.map(this::convertToDTO);
  }

  private FlagEntityDTO convertToDTO(FlagEntity flagEntity) {
    return FlagEntityDTO.builder()
        .id((long) flagEntity.getId())
        .name(flagEntity.getName())
        .type(flagEntity.getType())
        .uniqueId(flagEntity.getUniqueId())
        .altParentId(flagEntity.getAltParentId())
        .description(flagEntity.getDescription())
        .build();
  }
}
