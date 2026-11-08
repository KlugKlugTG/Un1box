package org.un1box.android

import android.content.Context
import android.os.RemoteException
import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/** Pointer IDs survive crossing and index changes. Pixel mapping belongs to native domain. */
private const val TAG = "Un1boxInput"

class GuestDisplay(context: Context, private val onError: (String) -> Unit = {}, private val isActiveOwner: () -> Boolean = { true }, private val ownerTicket: () -> Long = { 1L }, private val engine: () -> IEngine?) : SurfaceView(context), SurfaceHolder.Callback {
    // A newly created view cannot continue contacts owned by the previous Surface.
    private var rejectedGesture = true
    private val lease = WorkerDisplayLease()
    private var leasedWorker: IEngine? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var forwardedBatches = 0L
    private var rejectedBatches = 0L
    private var cancellationRequests = 0L
    private var suppressedContinuations = 0L
    private var inputGeneration = 0L
    private var expectedCancellationBatches = 0L
    private val contactIds = mutableSetOf<Int>()
    // Pre-allocated per-pointer-count arrays (1..8) avoid allocating three heap
    // arrays and a boxed action list on every 120-240 Hz ACTION_MOVE on the UI thread.
    private val idsScratch = Array(9) { IntArray(it) }
    private val phasesScratch = Array(9) { IntArray(it) }
    private val xyScratch = Array(9) { FloatArray(it * 2) }
    /** Main-thread observations of Android admission, never guest delivery proof. */
    internal fun inputObservations(): Bundle = Bundle().apply {
        putLong("forwardedBatches", forwardedBatches)
        putLong("rejectedBatches", rejectedBatches)
        putLong("cancellationRequests", cancellationRequests)
        putLong("suppressedContinuations", suppressedContinuations)
        putLong("inputGeneration", inputGeneration)
        putLong("expectedCancellationBatches", expectedCancellationBatches)
        putIntArray("androidContactIds", contactIds.toIntArray().sortedArray())
        putBoolean("awaitingNewDown", rejectedGesture)
    }
    init {
        contentDescription = "Guest display"
        holder.addCallback(this)
        isFocusable = true
    }
    private fun ownedWorker(): Pair<IEngine, Long>? {
        val worker = leasedWorker ?: return null
        val current = engine() ?: return null
        val token = lease.forWorker(current.asBinder())
        if (token == 0L || current.asBinder() !== worker.asBinder()) return null
        return worker to token
    }
    private fun dropLocalGesture() { rejectedGesture = true; contactIds.clear(); inputGeneration = 0L }
    private fun cancelGesture(releaseWorkerInput: Boolean = true) {
        dropLocalGesture()
        if (releaseWorkerInput) ownedWorker()?.let { (worker, token) ->
            try { cancellationRequests++; worker.cancelDisplayInput(token) } catch (_: RemoteException) { lease.clear() }
        }
    }
    /** Only an explicit current foreground/focused owner can acquire a lease. */
    internal fun attachActiveSurface() {
        if (!isActiveOwner() || !hasWindowFocus()) return
        val worker = engine() ?: return
        try {
            val ticket = ownerTicket()
            var token = lease.forOwner(worker.asBinder(), ticket)
            if (token == 0L) {
                // Only a genuine UI authority epoch/new worker may reset this
                // binding. A callback with the same epoch never remints a lease.
                releaseOwnedSurface()
                dropLocalGesture()
                token = worker.claimDisplay()
                if (token <= 0) { onError("Guest display ownership was not admitted"); return }
                leasedWorker = worker; lease.bind(worker.asBinder(), token, ticket)
            }
            if (!holder.surface.isValid) return // Authority survives Surface loss.
            val error = worker.attachDisplay(token, holder.surface)
            if (error.isNotEmpty()) { dropLocalGesture(); onError(error) }
        } catch (failure: RemoteException) {
            dropLocalGesture(); lease.clear(); onError("Guest worker unavailable; display was detached")
        }
    }
    private fun releaseOwnedSurface() {
        dropLocalGesture()
        ownedWorker()?.let { (worker, token) -> try { worker.releaseDisplay(token) } catch (_: RemoteException) { } }
        leasedWorker = null; lease.clear()
    }
    /** A late old Activity stop cannot pause the new native owner. */
    internal fun backgroundOwnedDisplay() {
        dropLocalGesture()
        ownedWorker()?.let { (worker, token) -> try { worker.backgroundDisplay(token) } catch (_: RemoteException) { } }
        releaseOwnedSurface()
    }
    internal fun retireDisplay() = releaseOwnedSurface()
    internal fun cancelOwnedGesture() = cancelGesture()
    override fun surfaceCreated(holder: SurfaceHolder) { attachActiveSurface() }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (surfaceWidth > 0 && surfaceHeight > 0 && (surfaceWidth != width || surfaceHeight != height))
            cancelGesture(releaseWorkerInput = contactIds.isNotEmpty())
        surfaceWidth = width; surfaceHeight = height
        // A callback never claims for a retired Activity/View.
        attachActiveSurface()
    }
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        dropLocalGesture()
        ownedWorker()?.let { (worker, token) -> try { worker.detachDisplay(token) } catch (_: RemoteException) { } }
        // Keep the captured authority so a following onStop can still pause.
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) cancelGesture() else attachActiveSurface()
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isActiveOwner() || ownedWorker() == null) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) android.util.Log.w(TAG, "touch dropped: owner=${isActiveOwner()} worker=${ownedWorker() != null}")
            dropLocalGesture(); suppressedContinuations++; return true
        }
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) { cancelGesture(); return true }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val (worker, token) = ownedWorker() ?: return true
            inputGeneration = try { worker.displayInputGeneration(token) } catch (_: RemoteException) { 0L }
            if (inputGeneration <= 0L) { android.util.Log.w(TAG, "touch dropped: no input generation"); dropLocalGesture(); suppressedContinuations++; return true }
            rejectedGesture = false
        }
        if (rejectedGesture) { suppressedContinuations++; return true }
        val count = event.pointerCount
        if (count > 8) { cancelGesture(); return true }
        val action = event.actionMasked
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_POINTER_DOWN &&
            action != MotionEvent.ACTION_MOVE && action != MotionEvent.ACTION_UP &&
            action != MotionEvent.ACTION_POINTER_UP) return true
        val ids = idsScratch[count]
        val phases = phasesScratch[count]
        val xy = xyScratch[count]
        for (index in 0 until count) {
            ids[index] = event.getPointerId(index)
            phases[index] = 1
            xy[index * 2] = event.getX(index)
            xy[index * 2 + 1] = event.getY(index)
        }
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> phases[event.actionIndex] = 0
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> phases[event.actionIndex] = 2
        }
        ownedWorker()?.let { (worker, token) ->
            // Preserve the source time; the dedicated worker orders millisecond ties
            // across Views without hiding a genuinely backwards input timestamp.
            val admission = try { worker.touchDisplay(token, inputGeneration, event.eventTime * 1_000_000, ids, phases, xy, width, height, display?.rotation?.times(90) ?: 0) }
                catch (_: RemoteException) { "Guest worker unavailable; input was cancelled" }
            forwardedBatches++
            if (DisplayInputAdmission.isCancellation(admission)) { android.util.Log.i(TAG, "gesture cancelled: $admission"); expectedCancellationBatches++; dropLocalGesture() }
            else if (admission.isNotEmpty()) { android.util.Log.w(TAG, "touch rejected: $admission"); rejectedBatches++; dropLocalGesture(); onError(admission) }
            else {
                for (index in ids.indices) {
                    if (phases[index] == 2) contactIds.remove(ids[index]) else contactIds.add(ids[index])
                }
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_UP) { rejectedGesture = true; performClick() }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
