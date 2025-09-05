package org.artificers.ingest;

@FunctionalInterface
interface FieldHandler {
    void handle(String header, String value, ConfigurableCsvReader.FieldSpec spec, RowBuilder builder);
}
