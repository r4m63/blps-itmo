package blps.itmo.security;

/**
 * Константы кодов привилегий — единая точка правды для аннотаций
 * {@code @PreAuthorize("hasAuthority(T(blps.itmo.security.Privileges).CLAIM_CREATE)")}.
 * <p>
 * Набор привилегий согласован с содержимым таблицы {@code privileges}
 * из {@code sql/init.sql}. Любое изменение здесь должно синхронно
 * отражаться в SQL-скриптах инициализации.
 */
public final class Privileges {

    public static final String CLAIM_CREATE = "CLAIM_CREATE";
    public static final String CLAIM_READ_OWN = "CLAIM_READ_OWN";
    public static final String CLAIM_READ_ANY = "CLAIM_READ_ANY";
    public static final String CLAIM_INTAKE_DECISION = "CLAIM_INTAKE_DECISION";
    public static final String CLAIM_PROVIDE_ADDITIONAL_INFO = "CLAIM_PROVIDE_ADDITIONAL_INFO";
    public static final String CLAIM_ASSESS = "CLAIM_ASSESS";
    public static final String CLAIM_TENANT_RESPOND = "CLAIM_TENANT_RESPOND";
    public static final String CLAIM_SUPPORT_DECISION = "CLAIM_SUPPORT_DECISION";
    public static final String STORAGE_UPLOAD = "STORAGE_UPLOAD";

    private Privileges() {
        // utility class
    }
}
