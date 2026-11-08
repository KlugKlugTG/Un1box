#pragma once
#include <android/choreographer.h>
#include <android/looper.h>
#include <algorithm>
#include <array>
#include <atomic>
#include <cstdint>
#include <thread>
#include <utility>
#include "display_priority.h"

// The display's vsync, from Choreographer on a thread of its own (it needs a looper): the
// latest vsync time and the refresh period, the shortest of the recent gaps between callbacks
// (one is requested every frame, so most gaps are exactly one period).
class DisplayVsync {
public:
    void start() { std::thread([this] { run(); }).detach(); }
    // Latest vsync and period, or {0, 0} when not known or a second stale.
    // Choreographer callbacks sleep automatically once presentation has been idle
    // for 500 ms and wake on demand here so paused/static screens burn zero CPU.
    std::pair<uint64_t,uint64_t> get(uint64_t now) {
        demand_ns_.store(now,std::memory_order_relaxed);
        if(!armed_.load(std::memory_order_acquire)) {
            if(ALooper *looper=looper_.load(std::memory_order_acquire))ALooper_wake(looper);
        }
        const uint64_t vsync=vsync_.load(std::memory_order_acquire), period=period_.load(std::memory_order_relaxed);
        return vsync && period && now>=vsync && now-vsync<1000000000ull ? std::make_pair(vsync,period) : std::make_pair(uint64_t(0),uint64_t(0));
    }
private:
    static constexpr uint64_t kKeepAliveNs=500000000ull;
    static void frame(int64_t frame_time_ns, void *opaque) {
        auto *self=static_cast<DisplayVsync *>(opaque);
        const uint64_t t=uint64_t(frame_time_ns), last=self->vsync_.load(std::memory_order_relaxed);
        if(last && t>last && t-last>2000000) {
            self->gaps_[self->count_++%self->gaps_.size()]=t-last;
            uint64_t shortest=UINT64_MAX;
            for(uint64_t gap:self->gaps_)if(gap)shortest=std::min(shortest,gap);
            self->period_.store(shortest,std::memory_order_relaxed);
        }
        self->vsync_.store(t,std::memory_order_release);
        const uint64_t demand=self->demand_ns_.load(std::memory_order_relaxed);
        if(self->count_<8 || (demand && (t<=demand || t-demand<kKeepAliveNs))) {
            AChoreographer_postFrameCallback64(self->choreographer_,frame,self);
        } else {
            self->armed_.store(false,std::memory_order_release);
            // Re-check in case get() raced with disarming before ALooper_wake.
            const uint64_t recheck=self->demand_ns_.load(std::memory_order_acquire);
            if(recheck && (t<=recheck || t-recheck<kKeepAliveNs)) {
                self->armed_.store(true,std::memory_order_release);
                AChoreographer_postFrameCallback64(self->choreographer_,frame,self);
            }
        }
    }
    void run() {
        request_display_priority();
        ALooper *looper=ALooper_prepare(0);
        choreographer_=AChoreographer_getInstance();
        if(!looper || !choreographer_)return;
        ALooper_acquire(looper);
        armed_.store(true,std::memory_order_release);
        looper_.store(looper,std::memory_order_release);
        AChoreographer_postFrameCallback64(choreographer_,frame,this);
        for(;;) {
            ALooper_pollOnce(-1,nullptr,nullptr,nullptr);
            if(!armed_.load(std::memory_order_relaxed) && demand_ns_.load(std::memory_order_acquire)) {
                armed_.store(true,std::memory_order_release);
                AChoreographer_postFrameCallback64(choreographer_,frame,this);
            }
        }
    }
    AChoreographer *choreographer_=nullptr;
    std::atomic<ALooper *> looper_{nullptr};
    std::array<uint64_t,32> gaps_{};  // looper thread only
    uint64_t count_=0;
    std::atomic<uint64_t> vsync_{0}, period_{0}, demand_ns_{0};
    std::atomic<bool> armed_{false};
};

// Posts guest frames on the guest's own 60 Hz grid. Each frame carries the time its frame
// interrupt was due (the engine's ipod_lcd_vsync_due), but reaches the presenter some
// milliseconds later by an amount that jitters (+/-2 ms on a phone: main-loop dispatch, the
// copy, the scale). Posting on arrival hands that jitter to SurfaceFlinger, and a 120 Hz panel
// then shows one frame for one refresh, the next for three. Posting at due + a steady latency
// (the recent 90th percentile of lateness plus a margin) spaces the posts evenly; a frame later
// than that is posted at once, as a late frame always was.
//
// Evenly spaced is not yet enough at 120 Hz: a post that lands near the moment SurfaceFlinger
// latches still goes to one refresh or the next on a fraction of a millisecond. So the post is
// also moved to just after a display vsync (DisplayVsync), where it has the whole refresh to be
// latched: then every frame is held for the same number of refreshes, two at 120 Hz. The guest's
// 16.667 ms is two 8.333 ms refreshes, so that phase holds instead of drifting.
class FramePacer {
public:
    static constexpr uint64_t min_latency_ns=1000000, max_latency_ns=12000000, margin_ns=1000000;
    static constexpr int64_t vsync_lead_ns=500000;
    // When to post a frame due at due_ns that is ready to post at ready_ns, given the display's
    // latest vsync and period (0 if unknown); 0 means now.
    uint64_t post_at(uint64_t due_ns, uint64_t ready_ns, uint64_t vsync_ns=0, uint64_t period_ns=0) {
        // No grid time (an older engine, a display poll), or a stall the grid cannot absorb.
        if(!due_ns || ready_ns<due_ns || ready_ns-due_ns>50000000) return 0;
        late_[count_++%late_.size()]=uint32_t(ready_ns-due_ns);
        if(count_%late_.size()==0) {  // re-estimated once per window, so it does not chase each frame
            auto sorted=late_;
            std::sort(sorted.begin(),sorted.end());
            latency_ns_=std::clamp<uint64_t>(sorted[sorted.size()*9/10]+margin_ns,min_latency_ns,max_latency_ns);
        }
        uint64_t at=due_ns+latency_ns_;
        if(vsync_ns && period_ns>=4000000 && period_ns<=50000000) {  // the first vsync + lead not before it
            const int64_t base=int64_t(vsync_ns)+vsync_lead_ns, p=int64_t(period_ns), d=int64_t(at)-base;
            at=uint64_t(base+(d>=0?(d+p-1)/p:-((-d)/p))*p);
        }
        return at>ready_ns?at:0;
    }
    uint64_t latency_ns() const { return latency_ns_; }
private:
    std::array<uint32_t,64> late_{};
    uint64_t count_=0, latency_ns_=4000000;
};
