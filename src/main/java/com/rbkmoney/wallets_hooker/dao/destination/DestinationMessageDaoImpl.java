package com.rbkmoney.wallets_hooker.dao.destination;

import com.rbkmoney.mapper.RecordRowMapper;
import com.rbkmoney.wallets_hooker.dao.AbstractDao;
import com.rbkmoney.wallets_hooker.domain.tables.pojos.DestinationMessage;
import com.rbkmoney.wallets_hooker.domain.tables.records.DestinationMessageRecord;
import lombok.extern.slf4j.Slf4j;
import org.jooq.InsertReturningStep;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

import static com.rbkmoney.wallets_hooker.domain.Tables.DESTINATION_MESSAGE;

@Component
@Slf4j
public class DestinationMessageDaoImpl extends AbstractDao implements DestinationMessageDao {

    private final RowMapper<DestinationMessage> listRecordRowMapper;

    public DestinationMessageDaoImpl(DataSource dataSource) {
        super(dataSource);
        this.listRecordRowMapper = new RecordRowMapper<>(DESTINATION_MESSAGE, DestinationMessage.class);
    }

    @Override
    public void create(DestinationMessage message) {
        InsertReturningStep<DestinationMessageRecord> insertReturningStep = getDslContext()
                .insertInto(DESTINATION_MESSAGE)
                .set(getDslContext()
                        .newRecord(DESTINATION_MESSAGE, message))
                .onConflict(DESTINATION_MESSAGE.DESTINATION_ID)
                .doNothing();
        execute(insertReturningStep);

        log.info("destinationMessage has been created, destinationId={}", message.getDestinationId());
    }

    @Override
    public DestinationMessage get(String id) {
        DestinationMessage destinationMessage = fetchOne(getDslContext()
                        .select(DESTINATION_MESSAGE.DESTINATION_ID,
                                DESTINATION_MESSAGE.MESSAGE)
                        .from(DESTINATION_MESSAGE)
                        .where(DESTINATION_MESSAGE.DESTINATION_ID.eq(id)),
                listRecordRowMapper);

        if (destinationMessage != null) {
            log.info("destinationMessage has been got, destinationId={}", id);
        }

        return destinationMessage;
    }
}
