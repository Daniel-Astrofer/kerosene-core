package com.kerosene.auth.dto;

/** Input contract for verifying the user's application PIN. */
public class VerifyAppPinRequestDTO {
    /** PIN candidate submitted for protected-operation verification */
    private String pin;

    /**
     * Returns pIN candidate submitted for protected-operation verification
     * @return PIN candidate
     */
    public String getPin() { return pin; }

    /**
     * Sets pIN candidate submitted for protected-operation verification
     * @param pin PIN candidate
     */
    public void setPin(String pin) { this.pin = pin; }

}
