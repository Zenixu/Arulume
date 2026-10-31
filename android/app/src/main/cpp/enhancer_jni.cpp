// enhancer_jni.cpp — jembatan JNI antara Kotlin dan ncnn (Real-ESRGAN).
//
// Aturan AGENTS.md #2: seluruh inferensi terjadi di sini (native), tidak di
// JavaScript/WebView. Berkas ini hanya menyediakan empat fungsi:
//
//   nativeCreate   — muat model .param/.bin, siapkan Vulkan, kalibrasi skala
//   nativeUpscale  — perbesar citra ARGB (dengan pemotongan tile + overlap)
//   nativeInfo     — kembalikan ringkasan konfigurasi (untuk log/diagnostik)
//   nativeDestroy  — lepas model dan memori
//
// Model yang dibundel: realesr-animevideov3 (Real-ESRGAN, lisensi BSD-3-Clause).

#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

#include "ncnn/gpu.h"
#include "ncnn/net.h"

#define TAG "ArulumeNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

constexpr int kWarmupSize = 64;

struct Enhancer {
    ncnn::Net net;
    bool vulkan = false;
    int scale = 4;
    int threads = 2;
    char error[512] = {0};
};

// ARGB (0xAARRGGBB) -> ncnn Mat 3 kanal (R,G,B) bernilai 0..1.
// Piksel sumber tidak pernah diubah; buffer hasil selalu baru.
void argb_to_mat(ncnn::Mat& dst, const int* src, int w, int h, int stride) {
    dst.create(w, h, 3);
    for (int y = 0; y < h; y++) {
        float* r = dst.channel(0).row(y);
        float* g = dst.channel(1).row(y);
        float* b = dst.channel(2).row(y);
        const int* row = src + static_cast<size_t>(y) * stride;
        for (int x = 0; x < w; x++) {
            const int p = row[x];
            r[x] = ((p >> 16) & 0xff) * (1.f / 255.f);
            g[x] = ((p >> 8) & 0xff) * (1.f / 255.f);
            b[x] = (p & 0xff) * (1.f / 255.f);
        }
    }
}

// Jembatan ke Kotlin untuk melaporkan progres per tile. Objeknya boleh null
// (mis. saat uji mandiri) — dalam hal itu tidak ada yang dilaporkan.
struct ProgressBridge {
    JNIEnv* env = nullptr;
    jobject obj = nullptr;
    jmethodID onTile = nullptr;

    bool valid() const { return env && obj && onTile; }

    void report(int done, int total) {
        if (!valid()) return;
        env->CallVoidMethod(obj, onTile, static_cast<jint>(done), static_cast<jint>(total));
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
};

inline int clamp_byte(float v) {
    int i = static_cast<int>(v * 255.f + 0.5f);
    if (i < 0) return 0;
    if (i > 255) return 255;
    return i;
}

// Menjalankan satu tile. Mengembalikan false bila inferensi gagal.
bool run_tile(Enhancer* e, const int* src, int stride, int x0, int y0, int w, int h,
              ncnn::Mat& out) {
    ncnn::Mat in;
    // Bila sumber punya stride lebih besar dari lebar tile, salin baris demi baris.
    if (stride == w) {
        argb_to_mat(in, src + static_cast<size_t>(y0) * stride + x0, w, h, stride);
    } else {
        std::vector<int> tmp(static_cast<size_t>(w) * h);
        for (int y = 0; y < h; y++) {
            std::memcpy(&tmp[static_cast<size_t>(y) * w],
                        src + static_cast<size_t>(y0 + y) * stride + x0,
                        sizeof(int) * w);
        }
        argb_to_mat(in, tmp.data(), w, h, w);
    }

    ncnn::Extractor ex = e->net.create_extractor();
    if (ex.input("data", in) != 0) return false;
    if (ex.extract("output", out) != 0) return false;
    return out.w > 0 && out.h > 0 && out.elempack == 1;
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_id_aruthtale_arulume_EnhancerNative_nativeCreate(JNIEnv* env, jobject /*thiz*/,
                                                      jstring param_path, jstring bin_path,
                                                      jint threads, jboolean use_vulkan) {
    const char* param = env->GetStringUTFChars(param_path, nullptr);
    const char* bin = bin_path ? env->GetStringUTFChars(bin_path, nullptr) : nullptr;

    auto* e = new Enhancer();
    e->threads = threads > 0 ? threads : 2;
    e->vulkan = use_vulkan == JNI_TRUE;

    e->net.opt.num_threads = e->threads;
    e->net.opt.use_packing_layout = true;
    e->net.opt.use_fp16_packed = true;
    e->net.opt.use_fp16_storage = true;

    if (e->vulkan) {
        ncnn::create_gpu_instance();
        e->net.opt.use_vulkan_compute = true;
        e->net.opt.use_fp16_arithmetic = true;
    }

    int rc_param = e->net.load_param(param);
    int rc_model = bin ? e->net.load_model(bin) : 0;

    if (rc_param != 0 || rc_model != 0) {
        snprintf(e->error, sizeof(e->error), "muat model gagal (param=%d bin=%d)", rc_param,
                 rc_model);
        LOGE("%s: %s", param, e->error);
        env->ReleaseStringUTFChars(param_path, param);
        if (bin) env->ReleaseStringUTFChars(bin_path, bin);
        delete e;
        return 0;
    }

    // Kalibrasi: satu tile kecil untuk (a) memanaskan pipeline Vulkan dan
    // (b) mengetahui faktor skala sesungguhnya dari graf (x2/x3/x4).
    std::vector<int> warm(static_cast<size_t>(kWarmupSize) * kWarmupSize, 0xff7f7f7f);
    ncnn::Mat out;
    if (!run_tile(e, warm.data(), kWarmupSize, 0, 0, kWarmupSize, kWarmupSize, out)) {
        snprintf(e->error, sizeof(e->error), "warmup gagal (graf tidak menghasilkan 'output')");
        LOGE("%s", e->error);
        env->ReleaseStringUTFChars(param_path, param);
        if (bin) env->ReleaseStringUTFChars(bin_path, bin);
        delete e;
        return 0;
    }
    e->scale = out.w / kWarmupSize;
    if (e->scale < 1) e->scale = 1;

    LOGI("model siap: skala=%d vulkan=%d threads=%d", e->scale, e->vulkan ? 1 : 0, e->threads);

    env->ReleaseStringUTFChars(param_path, param);
    if (bin) env->ReleaseStringUTFChars(bin_path, bin);
    return reinterpret_cast<jlong>(e);
}

JNIEXPORT jintArray JNICALL
Java_id_aruthtale_arulume_EnhancerNative_nativeUpscale(JNIEnv* env, jobject /*thiz*/,
                                                       jlong handle, jintArray pixels, jint w,
                                                       jint h, jint tile, jint pad,
                                                       jobject progress) {
    auto* e = reinterpret_cast<Enhancer*>(handle);
    if (!e || w <= 0 || h <= 0) return nullptr;

    ProgressBridge pb;
    if (progress != nullptr) {
        pb.env = env;
        pb.obj = progress;
        jclass cls = env->GetObjectClass(progress);
        pb.onTile = env->GetMethodID(cls, "onTile", "(II)V");
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(cls);
    }

    const int s = e->scale;
    const int ow = w * s;
    const int oh = h * s;

    std::vector<int> src(static_cast<size_t>(w) * h);
    env->GetIntArrayRegion(pixels, 0, static_cast<jsize>(src.size()), src.data());
    if (env->ExceptionCheck()) return nullptr;

    std::vector<int> dst(static_cast<size_t>(ow) * oh, 0);

    if (tile <= 0 || (w <= tile && h <= tile)) {
        ncnn::Mat out;
        if (!run_tile(e, src.data(), w, 0, 0, w, h, out)) {
            LOGE("inferensi gagal (satu lintasan %dx%d)", w, h);
            return nullptr;
        }
        pb.report(1, 1);
        for (int y = 0; y < oh && y < out.h; y++) {
            const float* r = out.channel(0).row(y);
            const float* g = out.channel(1).row(y);
            const float* b = out.channel(2).row(y);
            int* row = &dst[static_cast<size_t>(y) * ow];
            for (int x = 0; x < ow && x < out.w; x++) {
                row[x] = 0xff000000 | (clamp_byte(r[x]) << 16) | (clamp_byte(g[x]) << 8) |
                         clamp_byte(b[x]);
            }
        }
    } else {
        const int cols = (w + tile - 1) / tile;
        const int rows = (h + tile - 1) / tile;
        const int total = cols * rows;
        int selesai = 0;
        for (int ty = 0; ty < h; ty += tile) {
            for (int tx = 0; tx < w; tx += tile) {
                const int tw = std::min(tile, w - tx);
                const int th = std::min(tile, h - ty);
                // Ambil konteks di sekitar tile supaya sambungan tidak terlihat.
                const int x0 = std::max(0, tx - pad);
                const int y0 = std::max(0, ty - pad);
                const int x1 = std::min(w, tx + tw + pad);
                const int y1 = std::min(h, ty + th + pad);
                const int cw = x1 - x0;
                const int ch = y1 - y0;

                ncnn::Mat out;
                if (!run_tile(e, src.data(), w, x0, y0, cw, ch, out)) {
                    LOGE("inferensi gagal (tile %d,%d)", tx, ty);
                    return nullptr;
                }

                const int ox = (tx - x0) * s;
                const int oy = (ty - y0) * s;
                for (int y = 0; y < th * s; y++) {
                    const int sy = oy + y;
                    if (sy >= out.h) break;
                    const float* r = out.channel(0).row(sy);
                    const float* g = out.channel(1).row(sy);
                    const float* b = out.channel(2).row(sy);
                    int* row = &dst[static_cast<size_t>(ty * s + y) * ow + tx * s];
                    for (int x = 0; x < tw * s; x++) {
                        const int sx = ox + x;
                        if (sx >= out.w) break;
                        row[x] = 0xff000000 | (clamp_byte(r[sx]) << 16) |
                                 (clamp_byte(g[sx]) << 8) | clamp_byte(b[sx]);
                    }
                }
                pb.report(++selesai, total);
            }
        }
    }

    jintArray result = env->NewIntArray(static_cast<jsize>(dst.size()));
    if (!result) return nullptr;
    env->SetIntArrayRegion(result, 0, static_cast<jsize>(dst.size()), dst.data());
    return result;
}

JNIEXPORT jstring JNICALL
Java_id_aruthtale_arulume_EnhancerNative_nativeInfo(JNIEnv* env, jobject /*thiz*/,
                                                    jlong handle) {
    auto* e = reinterpret_cast<Enhancer*>(handle);
    if (!e) return env->NewStringUTF("{\"ok\":false}");
    char buf[256];
    snprintf(buf, sizeof(buf),
             "{\"ok\":true,\"scale\":%d,\"vulkan\":%s,\"threads\":%d,\"error\":\"%s\"}", e->scale,
             e->vulkan ? "true" : "false", e->threads, e->error);
    return env->NewStringUTF(buf);
}

JNIEXPORT void JNICALL
Java_id_aruthtale_arulume_EnhancerNative_nativeDestroy(JNIEnv* /*env*/, jobject /*thiz*/,
                                                       jlong handle) {
    auto* e = reinterpret_cast<Enhancer*>(handle);
    if (!e) return;
    e->net.clear();
    delete e;
}

}  // extern "C"
