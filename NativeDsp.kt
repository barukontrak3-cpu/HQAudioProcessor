package com.hqaudio.processor

/** JNI bridge to the native stereo DSP engine. Samples are interleaved L,R float PCM. */
class NativeDsp(sampleRate: Int = 48_000) {
    private var handle: Long = nativeCreate(sampleRate)

    fun setEqBand(index: Int, frequencyHz: Float, gainDb: Float, q: Float = 1.0f) {
        if (handle != 0L) nativeSetEqBand(handle, index, frequencyHz, gainDb, q)
    }

    fun setCompressor(thresholdDb: Float, ratio: Float, attackMs: Float, releaseMs: Float) {
        if (handle != 0L) nativeSetCompressor(handle, thresholdDb, ratio, attackMs, releaseMs)
    }

    fun process(interleavedStereo: FloatArray, validSamples: Int) {
        if (handle != 0L && validSamples > 0) nativeProcess(handle, interleavedStereo, validSamples)
    }

    fun close() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }

    private external fun nativeCreate(sampleRate: Int): Long
    private external fun nativeSetEqBand(handle: Long, index: Int, frequencyHz: Float, gainDb: Float, q: Float)
    private external fun nativeSetCompressor(handle: Long, thresholdDb: Float, ratio: Float, attackMs: Float, releaseMs: Float)
    private external fun nativeProcess(handle: Long, samples: FloatArray, validSamples: Int)
    private external fun nativeDestroy(handle: Long)

    companion object {
        init { System.loadLibrary("hqdsp") }
        val EQ_FREQUENCIES = floatArrayOf(
            20f, 25f, 31.5f, 40f, 50f, 63f, 80f, 100f, 125f, 160f,
            200f, 250f, 315f, 400f, 500f, 630f, 800f, 1000f, 1250f, 1600f,
            2000f, 2500f, 3150f, 4000f, 5000f, 6300f, 8000f, 10000f, 12500f, 16000f, 20000f
        )
    }
}
