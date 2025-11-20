package com.example.ninjaattack.repository;

import com.example.ninjaattack.model.entity.GameEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GameRepository extends JpaRepository<GameEntity, String> {
    List<GameEntity> findByStatus(String status);
}
