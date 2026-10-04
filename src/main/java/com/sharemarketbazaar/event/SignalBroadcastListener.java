package com.sharemarketbazaar.event;

import com.sharemarketbazaar.dto.TradeSignalDto;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Observer that fans domain events out to STOMP subscribers. */
@Component
@RequiredArgsConstructor
public class SignalBroadcastListener {

    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void onSignal(SignalGeneratedEvent event) {
        TradeSignalDto dto = TradeSignalDto.from(event.signal());
        messagingTemplate.convertAndSend("/topic/signals", dto);
        messagingTemplate.convertAndSend("/topic/signals/" + dto.getSymbol(), dto);
    }

    @EventListener
    public void onTick(TickReceivedEvent event) {
        messagingTemplate.convertAndSend("/topic/prices/" + event.tick().getSymbol(), event.tick());
    }
}
