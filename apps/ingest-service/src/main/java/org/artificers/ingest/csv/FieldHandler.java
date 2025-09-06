package org.artificers.ingest.csv;

@FunctionalInterface
interface FieldHandler {
    void handle(String header, String value, ConfigurableCsvReader.FieldSpec spec, RowBuilder builder);
}
