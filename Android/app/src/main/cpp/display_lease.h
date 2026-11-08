#pragma once
#include <algorithm>
#include <cstdint>
#include <limits>
#include <vector>
#include <utility>

// Adopt the reference returned by platform conversion. Declare outside the
// guarded scope so rejection/destruction releases outside the control guard.
template<class Window, void (*Release)(Window *)>
class ConvertedWindow {
public:
    explicit ConvertedWindow(Window *value):value_(value) {}
    ~ConvertedWindow() { if(value_)Release(value_); }
    ConvertedWindow(const ConvertedWindow &)=delete;
    ConvertedWindow &operator=(const ConvertedWindow &)=delete;
    Window *get() const { return value_; }
    Window *take() { return std::exchange(value_,nullptr); }
private:
    Window *value_;
};

// Caller holds the native control guard. Tokens belong to one worker process;
// the View also retains that worker's Binder identity, never just this number.
class DisplayLease {
public:
    struct Request { uint64_t owner=0, order=0; };
    uint64_t claim() {
        if(!input_generation_ || serial_==uint64_t(std::numeric_limits<int64_t>::max()))return 0;
        owner_=++serial_;++order_;clearContacts();return owner_;
    }
    bool owns(uint64_t token) const { return token && token==owner_; }
    uint64_t inputGeneration(uint64_t token) const { return owns(token)?input_generation_:0; }
    uint64_t currentOwner() const { return owner_; }
    bool currentInput(uint64_t token,uint64_t generation) const {
        return generation!=0 && inputGeneration(token)==generation;
    }
    Request begin(uint64_t token) { return owns(token)?Request{token,++order_}:Request{}; }
    bool current(Request request) const { return owns(request.owner) && request.order==order_; }
    bool detach(uint64_t token) {
        if(!owns(token))return false;
        ++order_;return true; // Keep authority and any failed release obligation.
    }
    bool release(uint64_t token) {
        if(!owns(token))return false;
        owner_=0;++order_;clearContacts();return true;
    }
    bool contactsHeld() const { return !contacts_.empty(); }
    bool inputPending() const { return input_pending_; }
    void inputObserved(uint64_t request) {
        if(request==input_request_)input_pending_=false;
    }
    bool pendingOwnedRelease() const {
        return owner_!=0 && (!uncertain_releases_.empty() || release_overflow_);
    }
    bool needsOwnedRelease() const { return contactsHeld() || pendingOwnedRelease(); }
    // Call only after successful full-cancellation admission. A normal completed
    // UP only discharges its release debt; it must not invalidate other fingers.
    void contactsReleased() {
        clearContacts();input_pending_=false;
        if(input_generation_ && input_generation_<uint64_t(std::numeric_limits<int64_t>::max()))++input_generation_;
        else input_generation_=0; // Exhaustion fails closed; never wrap authority.
    }
    // Start each admitted batch before updating its IDs. Completion coverage is
    // separate from accumulated release debt: latest-only runtime status cannot
    // prove the outcome of a request overwritten by a later admission.
    void beginAdmittedBatch() { batch_releases_.clear(); }
    void submittedInput(uint64_t request,uint64_t owner) {
        input_request_=request;input_owner_=owner;input_pending_=request!=0;
        input_releases_=batch_releases_;batch_releases_.clear();
    }
    bool reportsRejectedInput(uint64_t request) const {
        return input_pending_ && request==input_request_ && (input_owner_==0 || owns(input_owner_));
    }
    bool cancelsRejectedInput(uint64_t request) const {
        return reportsRejectedInput(request) && (input_owner_==0 || needsOwnedRelease());
    }
    void inputCompleted(uint64_t request) {
        if(request!=input_request_)return;
        if(input_owner_!=0 && owns(input_owner_)) {
            // Successful UP23 cannot discharge an unobserved earlier UP7.
            for(const auto id:input_releases_)
                uncertain_releases_.erase(std::remove(uncertain_releases_.begin(),uncertain_releases_.end(),id),uncertain_releases_.end());
        }
        input_pending_=false;
    }
    void admittedContact(int32_t id,int32_t phase) {
        auto existing=std::find(contacts_.begin(),contacts_.end(),id);
        // Successfully admitted public UP (2) and CANCEL (3) remove the local
        // held ID, but their guest release remains uncertain until this exact
        // batch completes successfully or a full cancellation is admitted.
        if(phase>=2) {
            const bool known=existing!=contacts_.end() || contains(uncertain_releases_,id);
            if(existing!=contacts_.end())contacts_.erase(existing);
            if(known && !appendBounded(uncertain_releases_,id))release_overflow_=true;
            if(!appendBounded(batch_releases_,id))release_overflow_=true;
        } else if(existing==contacts_.end())contacts_.push_back(id);
    }

private:
    uint64_t serial_=0,owner_=0,order_=0,input_generation_=1;
    uint64_t input_request_=0,input_owner_=0;
    bool input_pending_=false,release_overflow_=false;
    // UI contact IDs are small; generic JNI callers may use arbitrary IDs.
    // Bound accumulated unobserved releases; overflow needs a full cancellation
    // rather than unbounded allocation or an unjustified terminal-success clear.
    static constexpr size_t release_limit_=64;
    static bool contains(const std::vector<int32_t> &ids,int32_t id) {
        return std::find(ids.begin(),ids.end(),id)!=ids.end();
    }
    static bool appendBounded(std::vector<int32_t> &ids,int32_t id) {
        if(contains(ids,id))return true;
        if(ids.size()==release_limit_)return false;
        ids.push_back(id);return true;
    }
    void clearContacts() {
        contacts_.clear();uncertain_releases_.clear();batch_releases_.clear();input_releases_.clear();release_overflow_=false;
    }
    std::vector<int32_t> contacts_,uncertain_releases_,batch_releases_,input_releases_;
};
