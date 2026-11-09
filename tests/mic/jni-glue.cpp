/* What the Android recorder's JNI calls do with a block of mono samples.
 *
 * The three entry points are sliced out of Android/app/src/main/cpp/un1box_jni.cpp,
 * so the duplication, the clamp and the return codes are the shipped ones; only
 * the JNI environment and the portable API behind them are stubs, and both are
 * recorded so the test can look at exactly what crossed the boundary.
 *
 * SLICE ./Android/app/src/main/cpp/un1box_jni.cpp fn Java_org_un1box_android_Native_microphone Java_org_un1box_android_Native_microphoneFlush Java_org_un1box_android_Native_microphoneInfo
 * CFLAGS -Wall -Werror -Wno-missing-field-initializers -Wno-unused-parameter
 */
#include <algorithm>
#include <cassert>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <vector>
#if defined(__aarch64__)
#include <arm_neon.h>   /* the sliced microphone path widens mono to stereo with NEON there */
#endif

#include "Portable/include/lighttouch.h"

/* The JNI surface these three functions touch, no more. */
typedef int32_t jint;
typedef int64_t jlong;
typedef int32_t jsize;
struct _jobject {};
typedef _jobject *jobject;
struct ShortArray { std::vector<int16_t> samples; };
struct LongArray { std::vector<jlong> values; };
typedef ShortArray *jshortArray;
typedef LongArray *jlongArray;
struct JNIEnv {
    jsize GetArrayLength(jshortArray array) { return jsize(array->samples.size()); }
    void GetShortArrayRegion(jshortArray array, jsize start, jsize length, int16_t *into) {
        std::memcpy(into, array->samples.data() + start, size_t(length) * sizeof(int16_t));
    }
    jlongArray NewLongArray(jsize length) {
        LongArray *array = new LongArray();
        array->values.assign(size_t(length), 0);
        return array;
    }
    void SetLongArrayRegion(jlongArray array, jsize start, jsize length, const jlong *from) {
        std::memcpy(array->values.data() + start, from, size_t(length) * sizeof(jlong));
    }
};
#define JNI(name) extern "C"
#define JNICALL

struct lt_runtime { int unused; };
static lt_runtime runtime_object;
static lt_runtime *runtime = nullptr;
static std::mutex guard;

/* What the portable layer was asked to do. */
struct MicCalls {
    int writes = 0, flushes = 0, infos = 0;
    uint32_t count = 0;
    std::vector<int16_t> frames;
    lt_status write_result = LT_OK;
    int capturing = 1;
};
static MicCalls calls;

extern "C" lt_status lt_runtime_mic_write(lt_runtime *, const int16_t *frames, uint32_t count, lt_error *) {
    calls.writes++;
    calls.count = count;
    calls.frames.assign(frames, frames + size_t(count) * 2);
    return calls.write_result;
}
extern "C" lt_status lt_runtime_mic_info(lt_runtime *, lt_mic_info *info, lt_error *) {
    calls.infos++;
    if (!info || info->size < sizeof(*info)) return LT_INVALID;
    info->sample_rate = 44100;
    info->queued_frames = 512;
    info->capturing = uint32_t(calls.capturing);
    info->pushed_frames = 123456;
    info->dropped_frames = 7;
    return LT_OK;
}
extern "C" lt_status lt_runtime_mic_flush(lt_runtime *, lt_error *) {
    calls.flushes++;
    return LT_OK;
}

extern "C" jint Java_org_un1box_android_Native_microphone(JNIEnv *, jobject, jshortArray, jint);
extern "C" void Java_org_un1box_android_Native_microphoneFlush(JNIEnv *, jobject);
extern "C" jlongArray Java_org_un1box_android_Native_microphoneInfo(JNIEnv *, jobject);

namespace {
void reset() {
    calls = MicCalls();
    runtime = &runtime_object;
}
}  // namespace

int main(void)
{
    JNIEnv env;
    {/* A 1024-frame mono block becomes 1024 interleaved stereo frames. */
        reset();
        ShortArray block;
        for (int i = 0; i < 1024; i++) block.samples.push_back(int16_t(300 + i));
        const jint taken = Java_org_un1box_android_Native_microphone(&env, nullptr, &block, 1024);
        assert(taken == 1024);
        assert(calls.writes == 1);
        assert(calls.count == 1024);
        assert(calls.frames.size() == 2048);
        for (int i = 0; i < 1024; i++) {
            assert(calls.frames[size_t(i) * 2] == int16_t(300 + i));
            assert(calls.frames[size_t(i) * 2 + 1] == int16_t(300 + i));
        }
    }
    {/* The count is clamped to the array the recorder actually filled. */
        reset();
        ShortArray block;
        block.samples.assign(100, 5);
        assert(Java_org_un1box_android_Native_microphone(&env, nullptr, &block, 4096) == 100);
        assert(calls.count == 100);
    }
    {/* An empty read is not a call at all. */
        reset();
        ShortArray block;
        assert(Java_org_un1box_android_Native_microphone(&env, nullptr, &block, 1024) == 0);
        assert(calls.writes == 0);
    }
    {/* A full ring (LT_AGAIN) is a dropped block, reported as 0. */
        reset();
        calls.write_result = LT_AGAIN;
        ShortArray block;
        block.samples.assign(64, 1);
        assert(Java_org_un1box_android_Native_microphone(&env, nullptr, &block, 64) == 0);
    }
    {/* Anything else keeps its status, negated, so the recorder can stop and say why. */
        reset();
        calls.write_result = LT_UNSUPPORTED;
        ShortArray block;
        block.samples.assign(64, 1);
        assert(Java_org_un1box_android_Native_microphone(&env, nullptr, &block, 64) == -jint(LT_UNSUPPORTED));
    }
    {/* Nothing is called before the guest's engine exists. */
        reset();
        runtime = nullptr;
        ShortArray block;
        block.samples.assign(64, 1);
        assert(Java_org_un1box_android_Native_microphone(&env, nullptr, &block, 64) == -jint(LT_AGAIN));
        assert(calls.writes == 0);
        Java_org_un1box_android_Native_microphoneFlush(&env, nullptr);
        assert(calls.flushes == 0);
    }
    {/* The counters come back in this order: status, rate, queued, capturing, pushed, dropped. */
        reset();
        LongArray *info = Java_org_un1box_android_Native_microphoneInfo(&env, nullptr);
        assert(info->values.size() == 6);
        assert(info->values[0] == jlong(LT_OK));
        assert(info->values[1] == 44100);
        assert(info->values[2] == 512);
        assert(info->values[3] == 1);
        assert(info->values[4] == 123456);
        assert(info->values[5] == 7);
        delete info;
        /* Not capturing reads as 0, whatever the engine's own flag says. */
        reset();
        calls.capturing = 0;
        info = Java_org_un1box_android_Native_microphoneInfo(&env, nullptr);
        assert(info->values[3] == 0);
        delete info;
        /* A stopped runtime answers LT_AGAIN with zeroed counters instead of inventing values. */
        reset();
        runtime = nullptr;
        info = Java_org_un1box_android_Native_microphoneInfo(&env, nullptr);
        assert(info->values[0] == jlong(LT_AGAIN));
        assert(info->values[5] == 0);
        delete info;
    }
    {/* Stopping the recorder flushes what the guest has not read. */
        reset();
        Java_org_un1box_android_Native_microphoneFlush(&env, nullptr);
        assert(calls.flushes == 1);
    }
    std::puts("JNI glue: mono is duplicated to stereo, and every status maps to the recorder's contract");
    return 0;
}
