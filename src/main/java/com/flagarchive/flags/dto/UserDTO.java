package com.flagarchive.flags.dto;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@NoArgsConstructor
public class UserDTO {
  private Long id;

  @Email
  @NotBlank
  private String email;

  @Nullable
  private String firstName;

  @Nullable
  private String lastName;

  @NotBlank
  private String username;
}
