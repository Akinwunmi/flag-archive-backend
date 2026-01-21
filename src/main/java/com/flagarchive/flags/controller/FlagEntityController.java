package com.flagarchive.flags.controller;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flagarchive.flags.dto.FlagEntityDTO;
import com.flagarchive.flags.service.FlagEntityService;

@RestController
@RequestMapping("/entities")
public class FlagEntityController {

  private final FlagEntityService flagEntityService;

  public FlagEntityController(FlagEntityService flagEntityService) {
    this.flagEntityService = flagEntityService;
  }

  @GetMapping("/{id}")
  public ResponseEntity<FlagEntityDTO> getFlagEntityById(@PathVariable int id) {
    Optional<FlagEntityDTO> flagEntity = flagEntityService.getFlagEntityById(id);

    return flagEntity.map(flagEntityDTO -> new ResponseEntity<>(flagEntityDTO, HttpStatus.OK))
        .orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
  }
}
