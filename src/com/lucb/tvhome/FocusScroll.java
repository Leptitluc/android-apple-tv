package com.lucb.tvhome;

import android.content.Context;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.widget.ScrollView;

final class FocusScroll extends ScrollView {
    interface OnScroll {
        void onScroll(int y);
    }

    private OnScroll listener;

    FocusScroll(Context ctx) {
        super(ctx);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setFillViewport(true);
        setClipChildren(false);
        setClipToPadding(false);
    }

    void setOnScroll(OnScroll listener) {
        this.listener = listener;
    }

    @Override
    protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        return 0;
    }

    @Override
    public boolean executeKeyEvent(KeyEvent event) {
        return false;
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (listener != null) listener.onScroll(t);
    }

    int maxScroll() {
        return getChildCount() == 0 ? 0 : Math.max(0, getChildAt(0).getHeight() - getHeight());
    }
}
