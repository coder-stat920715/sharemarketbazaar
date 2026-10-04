package com.sharemarketbazaar.repository;

import com.sharemarketbazaar.model.Position;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PositionRepository extends JpaRepository<Position, Long> {

    Optional<Position> findByUserIdAndSymbol(String userId, String symbol);
}
