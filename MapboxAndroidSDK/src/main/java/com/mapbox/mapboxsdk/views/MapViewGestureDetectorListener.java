package com.mapbox.mapboxsdk.views;

import android.view.GestureDetector;
import android.view.MotionEvent;
import com.mapbox.mapboxsdk.tile.TileSystem;

/**
 * A custom gesture detector that processes gesture events and dispatches them
 * to the map's overlay system.
 */
public class MapViewGestureDetectorListener implements GestureDetector.OnGestureListener {

    private final MapView mapView;

    /**
     * Bind a new gesture detector to a map
     * @param mv a map view
     */
    public MapViewGestureDetectorListener(final MapView mv) {
        this.mapView = mv;
    }

    @Override
    public boolean onDown(final MotionEvent e) {

        // Stop scrolling if we are in the middle of a fling!
        if (this.mapView.mIsFlinging) {
            this.mapView.mScroller.abortAnimation();
            this.mapView.mIsFlinging = false;
        }

        if (this.mapView.getOverlayManager().onDown(e, this.mapView)) {
            return true;
        }

        return true;
    }

    @Override
    public boolean onFling(final MotionEvent e1,
                           final MotionEvent e2,
                           final float velocityX,
                           final float velocityY) {
        if (this.mapView.getOverlayManager()
                .onFling(e1, e2, velocityX, velocityY, this.mapView)) {
            return true;
        }

        final int worldSize = TileSystem.MapSize(this.mapView.getZoomLevel(false));
        this.mapView.mIsFlinging = true;
        this.mapView.mScroller.fling(
                this.mapView.getScrollX(),
                this.mapView.getScrollY(),
                (int) -velocityX,
                (int) -velocityY,
                -worldSize,
                worldSize,
                -worldSize,
                worldSize);
        return true;
    }

    @Override
    public void onLongPress(final MotionEvent e) {
    }

    @Override
    public boolean onScroll(final MotionEvent e1, final MotionEvent e2, final float distanceX,
                            final float distanceY) {
        if (this.mapView.getOverlayManager().onScroll(e1, e2, distanceX, distanceY,
                this.mapView)) {
            return true;
        }
        this.mapView.scrollBy((int) distanceX, (int) distanceY);
        return true;
    }

    @Override
    public void onShowPress(final MotionEvent e) {
        this.mapView.getOverlayManager().onShowPress(e, this.mapView);
    }

    @Override
    public boolean onSingleTapUp(final MotionEvent e) {
        return this.mapView.getOverlayManager().onSingleTapUp(e, this.mapView);
    }
}
