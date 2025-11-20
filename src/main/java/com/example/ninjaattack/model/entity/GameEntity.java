package com.example.ninjaattack.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "games")
@Data
public class GameEntity {

    @Id
    private String id;

    private String p1Username;
    private String p2Username;

    private String status; // PRE_GAME, IN_PROGRESS, FINISHED

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String gameStateJson;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
