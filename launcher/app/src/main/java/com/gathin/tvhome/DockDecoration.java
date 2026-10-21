package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Draws the tvOS style lighter band behind the first (dock) row and adds a gap below it. */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final boolean glass;
    private final int opacity;
    private float lastL, lastT, lastR, lastB;

    /**
     * @param translucent the cinematic shelf's band: the artwork and the glow show through it.
     * @param glass whether to render a glassmorphic gradient and specular rim.
     * @param opacity dock background opacity percentage (0-100).
     */
    DockDecoration(int dockSize, float density, boolean translucent, boolean glass, int opacity) {
        this.dockSize = dockSize;
        this.density = density;
        this.glass = glass;
        this.opacity = Math.max(0, Math.min(100, opacity));

        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density * (glass ? 1.25f : 1.0f));

        if (!glass) {
            int baseAlpha = translucent ? 0x8C : 0xFF;
            int effectiveAlpha = (baseAlpha * this.opacity) / 100;
            band.setColor((effectiveAlpha << 24) | 0x141416);
            int edgeAlpha = ((translucent ? 0x26 : 0x1F) * this.opacity) / 100;
            edge.setColor((edgeAlpha << 24) | 0xFFFFFF);
        }
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (12 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (opacity <= 0) return;

        boolean any = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || pos >= dockSize) continue;
            RecyclerView.ViewHolder vh = parent.getChildViewHolder(child);
            View card = vh instanceof AppAdapter.Holder ? ((AppAdapter.Holder) vh).card : child.findViewById(R.id.card);
            if (card == null) continue;
            float l = child.getLeft() + card.getLeft();
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float r = l + card.getWidth();
            float b = t + card.getHeight();
            if (!any) {
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        if (!any) return;
        float pad = 14 * density;
        // The band can only draw inside the grid, and the first and last dock tiles sit 12dp in from its
        // edges: a full 14dp at the sides sliced the rounded ends flat (#454). Keep the sides, with the
        // 1dp edge line, inside the grid; the top and bottom have room.
        float side = Math.max(0f, Math.min(pad, Math.min(rect.left, parent.getWidth() - rect.right) - density));
        rect.inset(-side, -pad);
        float radius = 26 * density;

        if (glass) {
            if (rect.left != lastL || rect.top != lastT || rect.right != lastR || rect.bottom != lastB) {
                lastL = rect.left;
                lastT = rect.top;
                lastR = rect.right;
                lastB = rect.bottom;

                float alphaFactor = opacity / 100f;
                // Frosted charcoal glass: top has soft light sheen, bottom blends into deep translucent dark
                int topAlpha = (int) (0x66 * alphaFactor);
                int bottomAlpha = (int) (0x33 * alphaFactor);
                int topColor = (topAlpha << 24) | 0x363946;
                int bottomColor = (bottomAlpha << 24) | 0x12131A;
                band.setShader(new LinearGradient(
                        rect.left, rect.top, rect.left, rect.bottom,
                        topColor, bottomColor, Shader.TileMode.CLAMP
                ));

                // Specular rim: top catch-light reflects ambient light, bottom rim is soft
                int strokeTopAlpha = (int) (0x75 * alphaFactor);
                int strokeBottomAlpha = (int) (0x22 * alphaFactor);
                int strokeTop = (strokeTopAlpha << 24) | 0xFFFFFF;
                int strokeBottom = (strokeBottomAlpha << 24) | 0xFFFFFF;
                edge.setShader(new LinearGradient(
                        rect.left, rect.top, rect.left, rect.bottom,
                        strokeTop, strokeBottom, Shader.TileMode.CLAMP
                ));
            }
        }

        c.drawRoundRect(rect, radius, radius, band);
        c.drawRoundRect(rect, radius, radius, edge);
    }
}
