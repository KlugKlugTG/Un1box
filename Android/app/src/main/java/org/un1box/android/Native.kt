package org.un1box.android

import android.view.Surface

/** Loaded only in :engine. UI never loads QEMU or constructs boot arguments. */
internal object Native {
    init { System.loadLibrary("un1box_jni") }
    external fun importBase(source: String, libraryRoot: String, displayName: String, rom: String, guest: String, sha256: String = ""): String
    /** A firmwarekit output folder in this app's storage, moved in as a new record's base (lt_library_adopt_prepared). */
    external fun adoptPrepared(source: String, libraryRoot: String, displayName: String, rom: String): String
    /** The running import's phase, bytes done and bytes total; an empty phase when none runs. */
    external fun importProgress(): Array<String>
    /** Write a stopped device to [fd] as a device file (Transfer.cpp); the error or "". Progress through importProgress. */
    external fun exportDevice(libraryRoot: String, id: String, fd: Int, app: String): String
    /** Add the device in the device file on [fd]: [error, id, name]. */
    external fun importDevice(libraryRoot: String, fd: Int): Array<String>
    /** Stop the running export or import (it cleans up after itself). */
    external fun cancelTransfer()
    external fun setGuestPackage(libraryRoot: String, id: String, path: String): String
    external fun records(libraryRoot: String): Array<Array<String>>
    external fun select(libraryRoot: String, id: String): String
    external fun rename(libraryRoot: String, id: String, name: String): String
    external fun remove(libraryRoot: String, id: String): String
    /** A device's carrier settings: [error, carrier, mccMnc, registered, simPresent, bars, latitude, longitude,
     *  lastCaller, lastSender] ("1"/"0" for flags; error "" when it worked). */
    external fun carrierGet(libraryRoot: String, id: String): Array<String>
    /** Check and save them (the same fields without the error); returns the error or "". */
    external fun carrierSet(libraryRoot: String, id: String, fields: Array<String>): String
    external fun reloadCarrier(): String
    /** A network-side event: incoming-call, remote-answer, remote-hangup, incoming-sms ("number|text"). */
    external fun modem(action: String, value: String): String
    /** The modem's state as JSON, "" when there is none yet. */
    external fun modemStatus(): String
    external fun frameTimeline(since: Long): LongArray
    external fun storageStats(): LongArray
    external fun setPresentScale(mode: Int)
    external fun setPresentVisible(visible: Boolean)
    external fun start(libraryRoot: String, id: String, headphones: Boolean): String
    external fun recoverStart(libraryRoot: String, id: String, headphones: Boolean): String
    external fun status(): Array<String>
    external fun guestFrameBuckets(): IntArray
    external fun claimDisplay(): Long
    external fun ownsDisplay(lease: Long): Boolean
    external fun displayInputGeneration(lease: Long): Long
    external fun attachDisplay(lease: Long, surface: Surface): String
    external fun detachDisplay(lease: Long)
    external fun releaseDisplay(lease: Long)
    external fun cancelDisplayInput(lease: Long, inputGeneration: Long): String
    external fun backgroundDisplay(lease: Long): String
    external fun touchDisplay(lease: Long, inputGeneration: Long, time: Long, ids: IntArray, phases: IntArray, xy: FloatArray, width: Int, height: Int, rotation: Int): String
    external fun pause(paused: Boolean): String
    external fun stop(): String
    external fun shutdownGraceMillis(): Int
    external fun debugArmQmp(): String
    external fun debugEnableTouchTrace(): String
    external fun debugArmVcpuAccounting(): String
    external fun debugSelectTcgIrCache(enabled: Boolean): String
    external fun debugSelectGraphicsProvider(choice: Int): String
    external fun debugQmpSnapshot(): Array<String>
    external fun button(button: Int, down: Boolean): String
    /** Fake drives for the fullscreen menu: "" or the engine's refusal. */
    external fun setBattery(level: Int, charging: Int): String
    external fun modemCommand(property: String, value: String): String
    external fun touch(time: Long, ids: IntArray, phases: IntArray, xy: FloatArray, width: Int, height: Int, rotation: Int): String
    external fun cancelInput()
    external fun motion(kind: Int, time: Long, xyz: FloatArray, rotation: Int): String
    external fun capture(): android.graphics.Bitmap?
    external fun observations(): LongArray
    external fun audioInfo(): IntArray
    external fun profile(path: String): String
    external fun profileStatus(): Int
    external fun guestRequest(operation: Int, argument: String, timeoutMs: Int): Array<String>
    external fun guestPoll(requestId: Long): Array<String>
    external fun guestCancel(requestId: Long)
    external fun audio(buffer: ShortArray): Int
    /** [count] stereo frames of [frames] to the guest's capture port: 0 taken, 1 dropped (not recording), else -lt_status. */
    external fun micWrite(frames: ShortArray, count: Int): Int
    /** Whether the guest has its microphone input running. */
    external fun micCapturing(): Boolean
    external fun micFlush()
    /** capturing, port rate, queued, pushed, dropped; empty when the engine has no microphone input. */
    external fun micInfo(): LongArray
    /** The guest's vibration motor: on (0 or 1) and how many times it has started; empty without a running engine. */
    external fun vibrator(): LongArray
    /** Holds or releases the engine's periodic storage sync (lt_runtime_hold_storage_sync); nothing without an engine. */
    external fun holdStorageSync(hold: Boolean)
    /** Mono 16-bit frames into the guest's capture port; returns the frames taken, 0 dropped, negative = error. */
    external fun microphone(samples: ShortArray, count: Int): Int
    external fun microphoneFlush()
    /** status, sampleRate, queuedFrames, capturing, pushedFrames, droppedFrames. */
    external fun microphoneInfo(): LongArray

    /**
     * The phone's camera as the emulated device's camera. One YUV_420_888 image's three planes, their row and
     * pixel strides, its size and the sensor it belongs to; returns 1 taken, 0 dropped (the guest is not
     * capturing), negative = status. The conversion to the device's NV12 frame is native (camera_planes.h).
     */
    external fun cameraFrame(
        luma: java.nio.ByteBuffer, lumaRow: Int, lumaPixel: Int,
        cb: java.nio.ByteBuffer, cbRow: Int, cbPixel: Int,
        cr: java.nio.ByteBuffer, crRow: Int, crPixel: Int,
        width: Int, height: Int, sensor: Int,
    ): Int
    /** Which sensor the guest's camera driver named last: [CAMERA_BACK] or [CAMERA_FRONT]. */
    external fun cameraSensor(): Int
    external fun cameraFlush()
    /** status, width, height, the guest's sensor, pushed, taken, replaced. */
    external fun cameraInfo(sensor: Int): LongArray

    /** The device's own sensors, as the engine and the ISP number them. */
    const val CAMERA_BACK = 0
    const val CAMERA_FRONT = 1
}
