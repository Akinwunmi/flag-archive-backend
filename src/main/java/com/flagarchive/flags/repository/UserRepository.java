package com.flagarchive.flags.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.flagarchive.flags.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
  Optional<User> getUserEntityById(int id);
}
