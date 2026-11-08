package org.un1box.android;
import android.os.Bundle;
import android.view.Surface;
interface IEngine {
    Bundle start(String recordId);
    String debugArmQmp();
    String debugSelectGraphicsProvider(int choice);
    Bundle debugQmpSnapshot();
    Bundle[] records();
    String select(String recordId);
    String importBase(String source, String displayName);
    String importPrepared(String source, String displayName, String rom);
    /** importPrepared for this app's own firmwarekit output: moved in as the base, not copied or hashed again. */
    String adoptPrepared(String source, String displayName, String rom);
    Bundle importProgress();
    String setGuestPackage(String recordId, String path);
    Bundle status();
    Bundle profile(boolean recording);
    long claimDisplay();
    String attachDisplay(long lease, in Surface surface);
    void detachDisplay(long lease);
    void releaseDisplay(long lease);
    void cancelDisplayInput(long lease);
    void backgroundDisplay(long lease);
    String touchDisplay(long lease, long inputGeneration, long timestampNanos, in int[] ids, in int[] phases, in float[] coordinates, int width, int height, int rotation);
    void pause(boolean paused);
    void stop();
    void button(int button, boolean down);
    /** Fake battery and cellular drives: "" or the refusal to show. */
    String setBattery(int level, int charging);
    String modemCommand(String property, String value);
    /** "" when the last modemCommand was accepted, else the modem's own refusal. */
    String modemError();
    String touch(long timestampNanos, in int[] ids, in int[] phases, in float[] coordinates, int width, int height, int rotation);
    void cancelInput();
    oneway void motion(int kind, long timestampNanos, in float[] xyz, int rotation);
    String screenshot(String path);
    Bundle guestRequest(int operation, String argument, int timeoutMs);
    Bundle guestPoll(long requestId);
    void guestCancel(long requestId);
    String debugEnableTouchTrace();
    Bundle recoverStart(String recordId);
    long displayInputGeneration(long lease);
    Bundle performance(long observationEpoch);
    String debugArmVcpuAccounting();
    Bundle debugShutdownProgress();
    String debugSelectTcgIrCache(boolean enabled);
    String rename(String recordId, String name);
    String remove(String recordId);
    /** sha256: the archive's checksum when the caller hashed it while copying; empty to hash it here. */
    String importDevice(String source, String displayName, String rom, String guest, String sha256);
    Bundle frameStats(long sinceSequence);
    void setPresentScale(int mode);
    void setPresentVisible(boolean visible);
    void setDetailedTiming(boolean enabled);
    Bundle hitchSnapshot();
    /** The iOS app in front: bundleId and appName, both "" when the guest tools cannot say (a paused guest). */
    Bundle frontmostApp();
}
