package com.herbrowser.app;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Камера с распознаванием QR-кода. */
@SuppressWarnings("deprecation")
class QrView extends FrameLayout implements SurfaceHolder.Callback, Camera.PreviewCallback {
    interface Listener {
        void onText(String text);

        void onError();
    }

    private final Listener listener;
    private final MultiFormatReader reader = new MultiFormatReader();
    private Camera cam;
    private int pw, ph;
    private volatile boolean busy, done;
    private long last;

    QrView(Context c, Listener l) {
        super(c);
        listener = l;
        SurfaceView sv = new SurfaceView(c);
        addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        sv.getHolder().addCallback(this);
        Map<DecodeHintType, Object> hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        reader.setHints(hints);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        open(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        release();
    }

    private void open(SurfaceHolder holder) {
        try {
            int id = 0;
            Camera.CameraInfo info = new Camera.CameraInfo();
            for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    id = i;
                    break;
                }
            }
            Camera.getCameraInfo(id, info);
            cam = Camera.open(id);
            Camera.Parameters p = cam.getParameters();
            Camera.Size best = null;
            for (Camera.Size s : p.getSupportedPreviewSizes()) {
                if (s.width <= 1280 && s.height <= 960
                        && (best == null || s.width * s.height > best.width * best.height)) {
                    best = s;
                }
            }
            if (best != null) p.setPreviewSize(best.width, best.height);
            if (p.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }
            cam.setParameters(p);
            pw = p.getPreviewSize().width;
            ph = p.getPreviewSize().height;
            int rot = ((Activity) getContext()).getWindowManager().getDefaultDisplay().getRotation();
            int deg = rot == Surface.ROTATION_90 ? 90 : rot == Surface.ROTATION_180 ? 180
                    : rot == Surface.ROTATION_270 ? 270 : 0;
            cam.setDisplayOrientation((info.orientation - deg + 360) % 360);
            cam.setPreviewDisplay(holder);
            cam.setPreviewCallback(this);
            cam.startPreview();
        } catch (Exception e) {
            release();
            listener.onError();
        }
    }

    @Override
    public void onPreviewFrame(final byte[] data, Camera c) {
        if (done || busy || System.currentTimeMillis() - last < 200) return;
        busy = true;
        last = System.currentTimeMillis();
        final int w = pw;
        final int h = ph;
        new Thread(() -> {
            try {
                PlanarYUVLuminanceSource src = new PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false);
                Result r = reader.decodeWithState(new BinaryBitmap(new HybridBinarizer(src)));
                if (r != null && !done) {
                    done = true;
                    final String t = r.getText();
                    post(() -> listener.onText(t));
                }
            } catch (Exception e) {
                // кода в кадре нет
            } finally {
                reader.reset();
                busy = false;
            }
        }).start();
    }

    void release() {
        if (cam != null) {
            try {
                cam.setPreviewCallback(null);
                cam.stopPreview();
            } catch (Exception e) {
                // уже остановлена
            }
            cam.release();
            cam = null;
        }
    }
}
