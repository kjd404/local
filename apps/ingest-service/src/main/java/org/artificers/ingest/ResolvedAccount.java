package org.artificers.ingest;

public final class ResolvedAccount {
    private final long id;
    private final String institution;
    private final String externalId;

    public ResolvedAccount(long id, String institution, String externalId) {
        this.id = id;
        this.institution = institution;
        this.externalId = externalId;
    }

    public long id() { return id; }
    public String institution() { return institution; }
    public String externalId() { return externalId; }
}
