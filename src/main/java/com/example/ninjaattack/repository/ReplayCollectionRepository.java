package com.example.ninjaattack.repository;

import com.example.ninjaattack.model.entity.ReplayCollection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReplayCollectionRepository extends JpaRepository<ReplayCollection, Long> {
    List<ReplayCollection> findByUsername(String username);

    boolean existsByUsernameAndGameId(String username, String gameId);
}
