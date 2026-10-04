package com.sharemarketbazaar.event;

import com.sharemarketbazaar.model.Signal;

/** Published when the signal engine emits a signal. */
public record SignalGeneratedEvent(Signal signal) {
}
