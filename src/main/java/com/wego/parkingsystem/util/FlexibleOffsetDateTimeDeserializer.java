package com.wego.parkingsystem.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

public class FlexibleOffsetDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {

    private static final ZoneId SINGAPORE_ZONE_ID = ZoneId.of("Asia/Singapore");

    @Override
    public OffsetDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String dateString = p.getText();
        try {
            return OffsetDateTime.parse(dateString);
        } catch (DateTimeParseException e) {
            LocalDateTime ldt = LocalDateTime.parse(dateString);
            return ldt.atZone(SINGAPORE_ZONE_ID).toOffsetDateTime();
        }
    }
}
