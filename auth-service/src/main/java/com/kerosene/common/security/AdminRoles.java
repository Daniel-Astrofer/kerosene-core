package com.kerosene.common.security;

/**
 * Role constants for Admin API access control.
 *
 * <p>Used in {@code @PreAuthorize} annotations on admin controllers
 * and in the security filter chain to centralise role names.</p>
 *
 * <h3>Role semantics</h3>
 * <ul>
 *   <li><b>ADMIN</b> — Full access to all administrative functions.</li>
 *   <li><b>OPERATOR</b> — Operational monitoring, health checks, logs,
 *       metrics, and non-sensitive data views.</li>
 *   <li><b>AUDITOR</b> — Read-only access to financial records and
 *       reconciliation status.</li>
 * </ul>
 */
public final class AdminRoles {

    private AdminRoles() {}

    /** System administrator — unrestricted. */
    public static final String ADMIN = "ADMIN";

    /** Operational monitor — health, logs, metrics, operational overview. */
    public static final String OPERATOR = "OPERATOR";

    /** Financial auditor — read-only reconciliation, ledger, and order data. */
    public static final String AUDITOR = "AUDITOR";

    /** SpEL: any of the three admin-tier roles. */
    public static final String HAS_ANY_ADMIN_ROLE =
            "hasAnyRole('" + ADMIN + "', '" + OPERATOR + "', '" + AUDITOR + "')";

    /** SpEL: admin or operator (operational tasks). */
    public static final String HAS_ADMIN_OR_OPERATOR =
            "hasAnyRole('" + ADMIN + "', '" + OPERATOR + "')";

    /** SpEL: admin or auditor (read-only financial access). */
    public static final String HAS_ADMIN_OR_AUDITOR =
            "hasAnyRole('" + ADMIN + "', '" + AUDITOR + "')";
}
