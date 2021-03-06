package com.rbkmoney.wallets_hooker.handler.poller;

import com.rbkmoney.eventstock.client.EventAction;
import com.rbkmoney.eventstock.client.EventHandler;
import com.rbkmoney.fistful.withdrawal.EventSinkPayload;
import com.rbkmoney.fistful.withdrawal.SinkEvent;
import com.rbkmoney.wallets_hooker.constant.EventTopic;
import com.rbkmoney.wallets_hooker.dao.EventLogDao;
import com.rbkmoney.wallets_hooker.handler.poller.impl.withdrawal.AbstractWithdrawalEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawalEventSinkHandler implements EventHandler<SinkEvent> {

    private final List<AbstractWithdrawalEventHandler> eventHandlers;
    private final EventLogDao eventLogDao;

    @Override
    public EventAction handle(SinkEvent sinkEvent, String subsKey) {
        try {
            handleEvents(sinkEvent, sinkEvent.getPayload());
            eventLogDao.create(sinkEvent.getId(), EventTopic.WITHDRAWAL);
        } catch (RuntimeException e) {
            log.error("Error when polling withdrawal event with id={}", sinkEvent.getId(), e);
            return EventAction.DELAYED_RETRY;
        }
        return EventAction.CONTINUE;
    }

    private void handleEvents(SinkEvent sinkEvent, EventSinkPayload payload) {
        payload.getChanges().forEach(cc -> eventHandlers.forEach(ph -> {
            if (ph.accept(cc)) {
                ph.handle(cc, sinkEvent);
            }
        }));
    }
}
