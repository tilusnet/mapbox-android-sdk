package com.mapbox.mapboxsdk.overlay;

import com.mapbox.mapboxsdk.ResourceProxy;
import com.mapbox.mapboxsdk.tileprovider.MapTile;
import com.mapbox.mapboxsdk.tileprovider.MapTileLayerBase;
import com.mapbox.mapboxsdk.tileprovider.ReusableBitmapDrawable;
import com.mapbox.mapboxsdk.util.TileLooper;
import com.mapbox.mapboxsdk.tile.TileSystem;
import com.mapbox.mapboxsdk.views.MapView;
import com.mapbox.mapboxsdk.views.safecanvas.ISafeCanvas;
import android.util.Log;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import com.mapbox.mapboxsdk.views.util.Projection;

/**
 * These objects are the principle consumer of map tiles.
 * <p/>
 * see {@link MapTile} for an overview of how tiles are acquired by this overlay.
 */

public class TilesOverlay
        extends SafeDrawOverlay {

    public static final int MENU_OFFLINE = getSafeMenuId();

    /**
     * Current tile source
     */
    protected final MapTileLayerBase mTileProvider;

    /* to avoid allocations during draw */
    protected final Paint mDebugPaint = new Paint();
    private final Rect mTileRect = new Rect();
    private final Rect mViewPort = new Rect();

    private boolean mOptionsMenuEnabled = true;

    private int mWorldSize_2;

    /**
     * A drawable loading tile *
     */
    private BitmapDrawable mLoadingTile = null;
    private int mLoadingBackgroundColor = Color.rgb(216, 208, 208);
    private int mLoadingLineColor = Color.rgb(200, 192, 192);

    /**
     * For overshooting the tile cache *
     */
    private int mOvershootTileCache = 0;

    public TilesOverlay(final MapTileLayerBase aTileProvider, final ResourceProxy pResourceProxy) {
        super(pResourceProxy);
        if (aTileProvider == null) {
            throw new IllegalArgumentException(
                    "You must pass a valid tile provider to the tiles overlay.");
        }
        this.mTileProvider = aTileProvider;
    }

    @Override
    public void onDetach(final MapView pMapView) {
        this.mTileProvider.detach();
    }

    public int getMinimumZoomLevel() {
        return mTileProvider.getMinimumZoomLevel();
    }

    public int getMaximumZoomLevel() {
        return mTileProvider.getMaximumZoomLevel();
    }

    /**
     * Whether to use the network connection if it's available.
     */
    public boolean useDataConnection() {
        return mTileProvider.useDataConnection();
    }

    /**
     * Set whether to use the network connection if it's available.
     *
     * @param aMode if true use the network connection if it's available. if false don't use the
     *              network connection even if it's available.
     */
    public void setUseDataConnection(final boolean aMode) {
        mTileProvider.setUseDataConnection(aMode);
    }

    @Override
    protected void drawSafe(final ISafeCanvas c, final MapView mapView, final boolean shadow) {

        if (shadow) {
            return;
        }

        // Calculate the half-world size
        final Projection pj = mapView.getProjection();
        final int zoomLevel = pj.getZoomLevel();
        mWorldSize_2 = TileSystem.MapSize(zoomLevel) >> 1;

        // Get the area we are drawing to
        mViewPort.set(pj.getScreenRect());

        // Translate the Canvas coordinates into Mercator coordinates
        mViewPort.offset(mWorldSize_2, mWorldSize_2);

        // Draw the tiles!
        drawTiles(c.getSafeCanvas(), pj.getZoomLevel(), TileSystem.getTileSize(), mViewPort);
    }

    /**
     * This is meant to be a "pure" tile drawing function that doesn't take into account
     * osmdroid-specific characteristics (like osmdroid's canvas's having 0,0 as the center rather
     * than the upper-left corner). Once the tile is ready to be drawn, it is passed to
     * onTileReadyToDraw where custom manipulations can be made before drawing the tile.
     */
    public void drawTiles(final Canvas c, final int zoomLevel, final int tileSizePx,
                          final Rect viewPort) {

        mTileLooper.loop(c, zoomLevel, tileSizePx, viewPort);

        // draw a cross at center in debug mode
        if (DEBUGMODE) {
            final Point centerPoint = new Point(viewPort.centerX() - mWorldSize_2,
                    viewPort.centerY() - mWorldSize_2);
            c.drawLine(centerPoint.x, centerPoint.y - 9,
                    centerPoint.x, centerPoint.y + 9, mDebugPaint);
            c.drawLine(centerPoint.x - 9, centerPoint.y,
                    centerPoint.x + 9, centerPoint.y, mDebugPaint);
        }

    }

    private final TileLooper mTileLooper = new TileLooper() {
        @Override
        public void initializeLoop(final int pZoomLevel, final int pTileSizePx) {
            // make sure the cache is big enough for all the tiles
            final int numNeeded = (mLowerRight.y - mUpperLeft.y + 1) * (mLowerRight.x - mUpperLeft.x + 1);
            mTileProvider.ensureCapacity(numNeeded + mOvershootTileCache);
        }

        @Override
        public void handleTile(final Canvas pCanvas,
                               final int pTileSizePx,
                               final MapTile pTile,
                               final int pX,
                               final int pY) {
            Drawable currentMapTile = mTileProvider.getMapTile(pTile);
            boolean isReusable = currentMapTile instanceof ReusableBitmapDrawable;
            if (currentMapTile == null) {
                currentMapTile = getLoadingTile();
            }

            if (currentMapTile != null) {
                mTileRect.set(
                        pX * pTileSizePx,
                        pY * pTileSizePx,
                        pX * pTileSizePx + pTileSizePx,
                        pY * pTileSizePx + pTileSizePx);
                if (isReusable) {
                    ((ReusableBitmapDrawable) currentMapTile).beginUsingDrawable();
                }
                try {
                    if (isReusable && !((ReusableBitmapDrawable) currentMapTile).isBitmapValid()) {
                        currentMapTile = getLoadingTile();
                        isReusable = false;
                    }
                    mTileRect.offset(-mWorldSize_2, -mWorldSize_2);
                    currentMapTile.setBounds(mTileRect);
                    currentMapTile.draw(pCanvas);
                } finally {
                    if (isReusable) {
                        ((ReusableBitmapDrawable) currentMapTile).finishUsingDrawable();
                    }
                }
            }

            if (DEBUGMODE) {
                mTileRect.set(pX * pTileSizePx,
                        pY * pTileSizePx,
                        pX * pTileSizePx + pTileSizePx, pY
                        * pTileSizePx + pTileSizePx);
                mTileRect.offset(-mWorldSize_2, -mWorldSize_2);
                pCanvas.drawText(pTile.toString(), mTileRect.left + 1,
                        mTileRect.top + mDebugPaint.getTextSize(), mDebugPaint);
                pCanvas.drawLine(mTileRect.left, mTileRect.top, mTileRect.right, mTileRect.top,
                        mDebugPaint);
                pCanvas.drawLine(mTileRect.left, mTileRect.top, mTileRect.left, mTileRect.bottom,
                        mDebugPaint);
            }
        }

        @Override
        public void finalizeLoop() {
        }
    };

    public int getLoadingBackgroundColor() {
        return mLoadingBackgroundColor;
    }

    /**
     * Set the color to use to draw the background while we're waiting for the tile to load.
     *
     * @param pLoadingBackgroundColor the color to use. If the value is {@link Color#TRANSPARENT} then there will be no
     *                                loading tile.
     */
    public void setLoadingBackgroundColor(final int pLoadingBackgroundColor) {
        if (mLoadingBackgroundColor != pLoadingBackgroundColor) {
            mLoadingBackgroundColor = pLoadingBackgroundColor;
            clearLoadingTile();
        }
    }

    public int getLoadingLineColor() {
        return mLoadingLineColor;
    }

    public void setLoadingLineColor(final int pLoadingLineColor) {
        if (mLoadingLineColor != pLoadingLineColor) {
            mLoadingLineColor = pLoadingLineColor;
            clearLoadingTile();
        }
    }

    /**
     * Draw a 'loading' placeholder with a canvas.
     * @return
     */
    private Drawable getLoadingTile() {
        if (mLoadingTile == null && mLoadingBackgroundColor != Color.TRANSPARENT) {
            try {
                final int tileSize = mTileProvider.getTileSource() != null ?
                        mTileProvider
                        .getTileSource().getTileSizePixels() : 256;
                final Bitmap bitmap = Bitmap.createBitmap(tileSize, tileSize,
                        Bitmap.Config.ARGB_8888);
                final Canvas canvas = new Canvas(bitmap);
                final Paint paint = new Paint();
                canvas.drawColor(mLoadingBackgroundColor);
                paint.setColor(mLoadingLineColor);
                paint.setStrokeWidth(0);
                final int lineSize = tileSize / 16;
                for (int a = 0; a < tileSize; a += lineSize) {
                    canvas.drawLine(0, a, tileSize, a, paint);
                    canvas.drawLine(a, 0, a, tileSize, paint);
                }
                mLoadingTile = new BitmapDrawable(bitmap);
            } catch (final OutOfMemoryError e) {
                Log.e(TAG, "OutOfMemoryError getting loading tile");
                System.gc();
            }
        }
        return mLoadingTile;
    }

    private void clearLoadingTile() {
        final BitmapDrawable bitmapDrawable = mLoadingTile;
        mLoadingTile = null;
        // Only recycle if we are running on a project less than 2.3.3 Gingerbread.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.GINGERBREAD) {
            if (bitmapDrawable != null) {
                bitmapDrawable.getBitmap().recycle();
            }
        }
    }

    private static final String TAG = "TilesOverlay";
}
