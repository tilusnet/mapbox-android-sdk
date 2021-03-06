package com.mapbox.mapboxsdk.views;

import android.view.ScaleGestureDetector;
import android.util.Log;

/**
 * https://developer.android.com/training/gestures/scale.html
 * A custom gesture detector that processes gesture events and dispatches them
 * to the map's overlay system.
 */
public class MapViewScaleGestureDetectorListener implements ScaleGestureDetector.OnScaleGestureListener {
    /**
     * This is the active focal point in terms of the viewport. Could be a local
     * variable but kept here to minimize per-frame allocations.
     */
    private float lastSpanX;
    private float lastSpanY;

    private float lastFocusX;
    private float lastFocusY;
    private float firstSpan;
    private final MapView mapView;

    /**
     * Bind a new gesture detector to a map
     * @param mv a map view
     */
    public MapViewScaleGestureDetectorListener(final MapView mv) {
        this.mapView = mv;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        lastSpanX = detector.getCurrentSpanX();
        lastSpanY = detector.getCurrentSpanY();
        lastFocusX = detector.getFocusX();
        lastFocusY = detector.getFocusY();
        firstSpan = detector.getCurrentSpan();
        this.mapView.mMultiTouchScalePoint.set(
                (int) lastFocusX +  this.mapView.getScrollX() - ( this.mapView.getWidth() / 2),
                (int) lastFocusY +  this.mapView.getScrollY() - ( this.mapView.getHeight() / 2));
        return true;
    }

    @Override
    public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
        float spanX = scaleGestureDetector.getCurrentSpanX();
        float spanY = scaleGestureDetector.getCurrentSpanY();

        float focusX = scaleGestureDetector.getFocusX();
        float focusY = scaleGestureDetector.getFocusY();

        float scale = scaleGestureDetector.getCurrentSpan() / firstSpan;
        this.mapView.mMultiTouchScale = scale;
        this.mapView.panBy((int) (lastFocusX - focusX), (int) (lastFocusY - focusY));
        this.mapView.invalidate();

        lastSpanX = spanX;
        lastSpanY = spanY;
        lastFocusX = focusX;
        lastFocusY = focusY;
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        float scale = this.mapView.mMultiTouchScale;
        int preZoom = this.mapView.getZoomLevel();
        this.mapView.getController().onAnimationEnd();
        Log.i(TAG, "scale" + Math.round((Math.log((double) scale) / Math.log(2d))));
        this.mapView.setZoom((int) Math.round((Math.log((double) scale) / Math.log(2d)) + preZoom));
    }
    private static String TAG = "detector";
}
