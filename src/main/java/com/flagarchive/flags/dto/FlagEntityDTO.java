package com.flagarchive.flags.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@NoArgsConstructor
public class FlagEntityDTO {
  private Long id;

  @NotBlank
  private String name;

  @NotBlank
  private String type;

  @NotBlank
  private String uniqueId;

  @Nullable
  private String altParentId;

  @Nullable
  private String description;
}
