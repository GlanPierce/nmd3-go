package com.example.ninjaattack.persistence;

import com.example.ninjaattack.model.domain.Game;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GamePersistenceTest {

    @Test
    public void testLenientDeserialization() throws Exception {
        // 1. Setup ObjectMapper as configured in GameService
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // 2. Create a JSON string with a valid Game structure PLUS an unknown field
        // We'll use a minimal JSON that represents a Game
        String jsonWithExtraField = "{" +
                "\"schemaVersion\": 1," +
                "\"gameId\": \"test-game-id\"," +
                "\"currentRound\": 1," +
                "\"unknownField\": \"this should be ignored\"," + // The extra field
                "\"anotherUnknown\": 123" +
                "}";

        // 3. Attempt to deserialize
        Game game = objectMapper.readValue(jsonWithExtraField, Game.class);

        // 4. Verify
        assertNotNull(game);
        assertEquals("test-game-id", game.getGameId());
        assertEquals(1, game.getCurrentRound());
        assertEquals(1, game.getSchemaVersion()); // Verify schema version is set

        System.out.println("Successfully deserialized Game with unknown fields!");
    }

    @Test
    public void testSchemaVersionDefault() {
        Game game = new Game();
        assertEquals(1, game.getSchemaVersion(), "Default schema version should be 1");
    }
}
