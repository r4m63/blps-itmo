package blps.itmo.platform.persistence;

public enum OutboxStatus {
    NEW,
    PUBLISHED,
    FAILED,
    DEAD
}
