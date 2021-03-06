package com.rbkmoney.wallets_hooker.handler.poller.impl.withdrawal;

import com.rbkmoney.fistful.withdrawal.Change;
import com.rbkmoney.fistful.withdrawal.SinkEvent;
import com.rbkmoney.fistful.withdrawal.Withdrawal;
import com.rbkmoney.geck.filter.Filter;
import com.rbkmoney.geck.filter.PathConditionFilter;
import com.rbkmoney.geck.filter.condition.IsNullCondition;
import com.rbkmoney.geck.filter.rule.PathConditionRule;
import com.rbkmoney.wallets_hooker.dao.destination.DestinationReferenceDao;
import com.rbkmoney.wallets_hooker.dao.wallet.WalletReferenceDao;
import com.rbkmoney.wallets_hooker.dao.webhook.WebHookDao;
import com.rbkmoney.wallets_hooker.dao.withdrawal.WithdrawalReferenceDao;
import com.rbkmoney.wallets_hooker.domain.WebHookModel;
import com.rbkmoney.wallets_hooker.domain.enums.EventType;
import com.rbkmoney.wallets_hooker.domain.tables.pojos.DestinationIdentityReference;
import com.rbkmoney.wallets_hooker.domain.tables.pojos.WalletIdentityReference;
import com.rbkmoney.wallets_hooker.domain.tables.pojos.WithdrawalIdentityWalletReference;
import com.rbkmoney.wallets_hooker.exception.HandleEventException;
import com.rbkmoney.wallets_hooker.handler.poller.impl.model.MessageGenParams;
import com.rbkmoney.wallets_hooker.handler.poller.impl.withdrawal.generator.WithdrawalCreatedHookMessageGenerator;
import com.rbkmoney.wallets_hooker.service.WebHookMessageSenderService;
import com.rbkmoney.webhook.dispatcher.WebhookMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawalCreatedHandler extends AbstractWithdrawalEventHandler {

    @Value("${waiting.reference.period}")
    private int waitingPollPeriod;

    private final WithdrawalReferenceDao withdrawalReferenceDao;
    private final DestinationReferenceDao destinationReferenceDao;
    private final WalletReferenceDao walletReferenceDao;
    private final WebHookDao webHookDao;
    private final WithdrawalCreatedHookMessageGenerator withdrawalCreatedHookMessageGenerator;
    private final WebHookMessageSenderService webHookMessageSenderService;

    private Filter filter = new PathConditionFilter(new PathConditionRule("created", new IsNullCondition().not()));

    @Override
    public void handle(Change change, SinkEvent sinkEvent) {
        try {
            long eventId = sinkEvent.getId();
            Withdrawal withdrawal = change.getCreated().getWithdrawal();
            String withdrawalId = sinkEvent.getSource();
            String destinationId = withdrawal.getDestinationId();
            String walletId = withdrawal.getWalletId();

            log.info("Start handling withdrawal created, destinationId={}, withdrawal={}, walletId={}", destinationId, withdrawal, walletId);

            DestinationIdentityReference destinationIdentityReference = destinationReferenceDao.get(destinationId);

            WalletIdentityReference walletIdentityReference = walletReferenceDao.get(walletId);

            while (destinationIdentityReference == null || walletIdentityReference == null) {
                log.info("Waiting destination: {} or wallet: {} !", destinationId, walletId);
                try {
                    Thread.sleep(waitingPollPeriod);
                    destinationIdentityReference = destinationReferenceDao.get(destinationId);
                    walletIdentityReference = walletReferenceDao.get(walletId);
                } catch (InterruptedException e) {
                    log.error("Error when waiting destination: {} or wallet: {} e: ", destinationId, walletId, e);
                    Thread.currentThread().interrupt();
                }
            }

            createReference(withdrawal, destinationIdentityReference, sinkEvent.getPayload().getSequence(), String.valueOf(eventId), withdrawalId);

            List<WebHookModel> webHookModels = findWebhookModels(destinationIdentityReference, walletIdentityReference);

            webHookModels.stream()
                    .filter(webhook -> webhook.getWalletId() == null || webhook.getWalletId().equals(walletId))
                    .map(webhook -> generateWithdrawalCreatedHookMsg(withdrawal, webhook, withdrawalId, eventId, sinkEvent.getCreatedAt(), withdrawal.getExternalId()))
                    .forEach(webHookMessageSenderService::send);

            log.info("Finish handling withdrawal created, destinationId={}, withdrawalId={}, walletId={}", destinationId, withdrawalId, walletId);
        } catch (Exception e) {
            log.error("WithdrawalCreatedHandler error when handle change: {}, sinkEvent: {} e: ", change, sinkEvent, e);
            throw new HandleEventException("WithdrawalCreatedHandler error when handle change!", e);
        }
    }

    private List<WebHookModel> findWebhookModels(DestinationIdentityReference destinationIdentityReference, WalletIdentityReference walletIdentityReference) {
        List<WebHookModel> webHookModels = webHookDao.getByIdentityAndEventType(destinationIdentityReference.getIdentityId(), EventType.WITHDRAWAL_CREATED);
        if (!destinationIdentityReference.getIdentityId().equals(walletIdentityReference.getIdentityId())) {
            List<WebHookModel> webHookModelsWallets = webHookDao.getByIdentityAndEventType(walletIdentityReference.getIdentityId(), EventType.WITHDRAWAL_CREATED);
            webHookModels.addAll(webHookModelsWallets);
        }
        return webHookModels;
    }

    private void createReference(Withdrawal withdrawal, DestinationIdentityReference destinationIdentityReference,
                                 int sequenceId, String eventId, String withdrawalId) {
        WithdrawalIdentityWalletReference withdrawalIdentityWalletReference = new WithdrawalIdentityWalletReference();
        withdrawalIdentityWalletReference.setIdentityId(destinationIdentityReference.getIdentityId());
        withdrawalIdentityWalletReference.setWalletId(withdrawal.getWalletId());
        withdrawalIdentityWalletReference.setWithdrawalId(withdrawalId);
        withdrawalIdentityWalletReference.setEventId(eventId);
        withdrawalIdentityWalletReference.setSequenceId((long) sequenceId);
        withdrawalIdentityWalletReference.setExternalId(withdrawal.getExternalId());

        withdrawalReferenceDao.create(withdrawalIdentityWalletReference);
    }

    private WebhookMessage generateWithdrawalCreatedHookMsg(Withdrawal withdrawal, WebHookModel webhook,
                                                            String withdrawalId, long eventId, String createdAt, String externalId) {
        MessageGenParams msgGenParams = MessageGenParams.builder()
                .sourceId(withdrawalId)
                .eventId(eventId)
                .createdAt(createdAt)
                .externalId(externalId)
                .build();
        return withdrawalCreatedHookMessageGenerator.generate(withdrawal, webhook, msgGenParams);
    }

    @Override
    public Filter getFilter() {
        return filter;
    }

}
