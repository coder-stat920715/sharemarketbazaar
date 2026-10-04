package com.sharemarketbazaar.repository;

import com.sharemarketbazaar.model.Signal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SignalRepository extends JpaRepository<Signal, Long> {

    List<Signal> findTop20BySymbolOrderByGeneratedAtDesc(String symbol);

    Optional<Signal> findFirstBySymbolOrderByGeneratedAtDesc(String symbol);
}
