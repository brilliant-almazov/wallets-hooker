package com.rbkmoney.wallets_hooker.converter;

import com.rbkmoney.fistful.destination.Destination;
import com.rbkmoney.fistful.destination.Resource;
import com.rbkmoney.swag.wallets.webhook.events.model.BankCard;
import com.rbkmoney.swag.wallets.webhook.events.model.CryptoWallet;
import com.rbkmoney.swag.wallets.webhook.events.model.DestinationResource;
import com.rbkmoney.wallets_hooker.exception.UnknownResourceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DestinationToDestinationMessageConverter implements Converter<Destination, com.rbkmoney.swag.wallets.webhook.events.model.Destination> {

    @Override
    public com.rbkmoney.swag.wallets.webhook.events.model.Destination convert(Destination event) {
        var destination = new com.rbkmoney.swag.wallets.webhook.events.model.Destination();
        destination.setExternalID(event.getExternalId());
        // todo metadata null?
        destination.setMetadata(null);
        destination.setName(event.getName());
        DestinationResource destinationResource = initDestinationResource(event.getResource());
        destination.setResource(destinationResource);

        log.info("destinationDamsel has been converted, destination={}", destination.toString());

        return destination;
    }

    private DestinationResource initDestinationResource(Resource resource) {
        DestinationResource destinationResource;
        if (resource.isSetBankCard()) {
            BankCard bankCard = new BankCard();
            bankCard.bin(resource.getBankCard().bin);
            bankCard.cardNumberMask(resource.getBankCard().masked_pan);
            if (resource.getBankCard().isSetPaymentSystem()) {
                bankCard.paymentSystem(BankCard.PaymentSystemEnum.fromValue(resource.getBankCard().payment_system.name()));
            }
            destinationResource = bankCard;
        } else if (resource.isSetCryptoWallet()) {
            CryptoWallet cryptoWallet = new CryptoWallet();
            cryptoWallet.setCryptoWalletId(resource.getCryptoWallet().id);
            cryptoWallet.setCurrency(CryptoWallet.CurrencyEnum.fromValue(resource.getCryptoWallet().currency.name()));
            destinationResource = cryptoWallet;
        } else {
            throw new UnknownResourceException("Can't init destination with unknown resource");
        }
        return destinationResource;
    }
}