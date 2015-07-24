package com.sgottard.sofa;

import android.view.View;

/**
 * Created by Sebastiano Gottardo on 05/07/15.
 */
public interface ContentFragment {
    boolean isScrolling();
    View getFocusRootView();
    String getTag();
    View getView();
    void setExtraMargin(int marginTop, int marginLeft);
}
