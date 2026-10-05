package com.kerosene.auth.dto;

/** Request carrying the trusted administrator's decision for a pending access attempt. */
public class AdminAccessDecisionRequestDTO {
    /** Decision value interpreted by the approval endpoint, typically approve or deny. */
    private String decision;

    /** Returns the requested decision. @return decision code */
    public String getDecision() {
        return decision;
    }

    /** Sets the requested access decision. @param decision approval or denial code */
    public void setDecision(String decision) {
        this.decision = decision;
    }
}
