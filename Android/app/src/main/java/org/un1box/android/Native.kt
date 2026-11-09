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
    external fun setGuestPackage(libraryRoot: String, id: String, path: String): String
    external fun records(libraryRoot: String): Array<Array<String>>
    external fun select(libraryRoot: String, id: String): String
    external fun rename(libraryRoot: String, id: String, name: String): String
    external fun remove(libraryRoot: String, id: String): String
    external fun frameTimeline(since: Long): LongArray
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
