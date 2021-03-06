package com.rbkmoney.wallets_hooker.handler.poller.impl.destination;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbkmoney.fistful.destination.Change;
import com.rbkmoney.fistful.destination.SinkEvent;
import com.rbkmoney.geck.filter.Filter;
import com.rbkmoney.geck.filter.PathConditionFilter;
import com.rbkmoney.geck.filter.condition.IsNullCondition;
import com.rbkmoney.geck.filter.rule.PathConditionRule;
import com.rbkmoney.swag.wallets.webhook.events.model.Destination;
import com.rbkmoney.wallets_hooker.converter.DestinationToDestinationMessageConverter;
import com.rbkmoney.wallets_hooker.dao.destination.DestinationMessageDaoImpl;
import com.rbkmoney.wallets_hooker.domain.tables.pojos.DestinationMessage;
import com.rbkmoney.wallets_hooker.exception.HandleEventException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DestinationCreatedHandler extends AbstractDestinationEventHandler {

    private final DestinationMessageDaoImpl destinationMessageDao;
    private final DestinationToDestinationMessageConverter destinationToDestinationMessageConverter;
    private final ObjectMapper objectMapper;

    private Filter filter = new PathConditionFilter(new PathConditionRule("created", new IsNullCondition().not()));

    @Override
    public void handle(Change change, SinkEvent sinkEvent) {
        try {
            String destinationId = sinkEvent.getSource();
            log.info("Start handling destination created, destinationId={}", destinationId);

            Destination destination = destinationToDestinationMessageConverter.convert(change.getCreated());
            destination.setId(destinationId);

            DestinationMessage destinationMessage = new DestinationMessage();
            destinationMessage.setDestinationId(destinationId);
            destinationMessage.setMessage(objectMapper.writeValueAsString(destination));

            destinationMessageDao.create(destinationMessage);

            log.info("Finish handling destination created, destinationId={}", destinationId);
        } catch (JsonProcessingException e) {
            log.error("Error when handle DestinationCreated change: {} e: ", change, e);
            throw new HandleEventException("Error when handle DestinationCreated change", e);
        }
    }

    @Override
    public Filter getFilter() {
        return filter;
    }

}
