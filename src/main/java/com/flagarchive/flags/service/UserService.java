package com.flagarchive.flags.service;

import java.util.Optional;
import org.springframework.stereotype.Service;

import com.flagarchive.flags.dto.UserDTO;
import com.flagarchive.flags.model.User;
import com.flagarchive.flags.repository.UserRepository;

@Service
public class UserService {
  private final UserRepository userRepository;

  public UserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public Optional<UserDTO> getUserById(int id) {
    Optional<User> userFromDb = userRepository.getUserEntityById(id);

    return userFromDb.map(this::convertToDTO);
  }

  private UserDTO convertToDTO(User userEntity) {
    return UserDTO.builder()
        .id((long) userEntity.getId())
        .email(userEntity.getEmail())
        .firstName(userEntity.getFirst_name())
        .lastName(userEntity.getLast_name())
        .username(userEntity.getUsername())
        .build();
  }
}
