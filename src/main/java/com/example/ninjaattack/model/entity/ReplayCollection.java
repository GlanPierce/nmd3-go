package com.example.ninjaattack.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "replay_collections")
@Data
public class ReplayCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username; // Owner of this collection item
    private String gameId; // The game being saved
    private String note; // Optional user note

    @CreationTimestamp
    private LocalDateTime savedAt;
}
