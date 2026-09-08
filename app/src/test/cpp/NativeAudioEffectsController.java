package top.nekoh2o.player.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

// Host JNI contract test. Keep declarations aligned with the Kotlin controller.
public final class NativeAudioEffectsController {
    native long nativeCreate(int rate, int channels);
    native void nativeConfigure(long handle, float[] bands, int bass, int width, int wet,
        int room, int damping, int loudness, int masteringId, int masteringMix);
    native void nativeProcess(long handle, ByteBuffer buffer, int samples);
    native void nativeReset(long handle);
    native void nativeRelease(long handle);

    private static void expectFailure(Runnable operation) {
        try { operation.run(); }
        catch (IllegalStateException expected) { return; }
        throw new AssertionError("JNI should reject invalid input");
    }

    public static void main(String[] args) {
        System.load(args[0]);
        NativeAudioEffectsController controller = new NativeAudioEffectsController();
        expectFailure(() -> controller.nativeCreate(44100, 0));
        expectFailure(() -> controller.nativeCreate(0, 2));
        for (int channels : new int[] {1, 2}) {
            long handle = controller.nativeCreate(48000, channels);
            if (handle == 0) throw new AssertionError("No DSP instance");
            try {
                controller.nativeConfigure(handle, new float[10], 0, 0, 0, 50, 30, 0, 0, 100);
                ByteBuffer buffer = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder());
                buffer.putShort(0, (short)-32768);
                buffer.putShort(2, (short)32767);
                buffer.putShort(4, (short)123);
                buffer.putShort(6, (short)-123);
                controller.nativeProcess(handle, buffer, 4);
                if (buffer.getShort(0) != -32768 || buffer.getShort(2) != 32767 ||
                    buffer.getShort(4) != 123 || buffer.getShort(6) != -123)
                    throw new AssertionError("Bypass changed PCM");
                expectFailure(() -> controller.nativeProcess(handle, buffer, 5));
                expectFailure(() -> controller.nativeProcess(handle, ByteBuffer.allocate(8), 4));
                expectFailure(() -> controller.nativeProcess(handle, null, 4));
                expectFailure(() -> controller.nativeConfigure(handle, new float[9], 0, 0, 0, 0, 0, 0, 0, 100));
                for (int preset = 15; preset <= 34; preset++) {
                    controller.nativeConfigure(handle, new float[10], 0, 0, 0, 50, 30, 0, preset, 100);
                    controller.nativeProcess(handle, buffer, 4);
                    controller.nativeReset(handle);
                }
            } finally { controller.nativeRelease(handle); }
        }
        System.out.println("JNI checks passed: exports, mono/stereo, exact bypass, 20 presets, buffer bounds, lifecycle");
    }
}
