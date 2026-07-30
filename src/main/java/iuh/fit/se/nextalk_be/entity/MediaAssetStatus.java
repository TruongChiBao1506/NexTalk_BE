package iuh.fit.se.nextalk_be.entity;

public enum MediaAssetStatus {
    QUARANTINED,
    BASIC_VALIDATED,
    CLEAN,
    REJECTED,
    PENDING_DELETE;

    public boolean isShareable() {
        return this == CLEAN || this == BASIC_VALIDATED;
    }
}
