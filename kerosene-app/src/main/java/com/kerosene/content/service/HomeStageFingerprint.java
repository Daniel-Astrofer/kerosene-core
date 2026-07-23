package com.kerosene.content.service;

import com.kerosene.content.dto.HomeStageDTO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Stable fingerprint for a stage content edition.
 * Same id + kind + title + body → same fingerprint (suppress re-show after read).
 * Any content change produces a new fingerprint → may show again.
 */
public final class HomeStageFingerprint {

    private HomeStageFingerprint() {}

    public static String of(HomeStageDTO stage) {
        if (stage == null) {
            return ofParts("idle", "IDLE", "", "");
        }
        String title = stage.content() != null && stage.content().title() != null
                ? stage.content().title()
                : "";
        String body = stage.content() != null && stage.content().body() != null
                ? stage.content().body()
                : "";
        return ofParts(stage.id(), stage.kind(), title, body);
    }

    public static String ofParts(String stageId, String kind, String title, String body) {
        String raw = String.join(
                "\n",
                nullToEmpty(stageId).trim(),
                nullToEmpty(kind).trim().toUpperCase(Locale.ROOT),
                nullToEmpty(title).trim(),
                nullToEmpty(body).trim());
        return sha256Hex(raw);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            // Short stable id is enough for uniqueness in practice.
            return HexFormat.of().formatHex(dig).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            // Unreachable on standard JVMs; fall back to hashCode hex.
            return Integer.toHexString(raw.hashCode());
        }
    }
}
