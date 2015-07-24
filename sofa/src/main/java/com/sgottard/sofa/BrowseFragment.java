package com.sgottard.sofa;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.v17.leanback.transition.TransitionListener;
import android.support.v17.leanback.widget.BrowseFrameLayout;
import android.support.v17.leanback.widget.HorizontalGridView;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ObjectAdapter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.PresenterSelector;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowHeaderPresenter;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v17.leanback.widget.TitleView;
import android.support.v17.leanback.widget.VerticalGridView;
import android.support.v4.view.ViewCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import static android.support.v7.widget.RecyclerView.NO_POSITION;

/**
 * A fragment for creating Leanback browse screens. It is composed of a
 * RowsFragment and a HeadersFragment.
 * <p>
 * A BrowseFragment renders the elements of its {@link ObjectAdapter} as a set
 * of rows in a vertical list. The elements in this adapter must be subclasses
 * of {@link Row}.
 * <p>
 * The HeadersFragment can be set to be either shown or hidden by default, or
 * may be disabled entirely. See {@link #setHeadersState} for details.
 * <p>
 * By default the BrowseFragment includes support for returning to the headers
 * when the user presses Back. For Activities that customize {@link
 * android.app.Activity#onBackPressed()}, you must disable this default Back key support by
 * calling {@link #setHeadersTransitionOnBackEnabled(boolean)} with false and
 * use {@link BrowseFragment.BrowseTransitionListener} and
 * {@link #startHeadersTransition(boolean)}.
 * <p>
 * The recommended theme to use with a BrowseFragment is
 * {@link R.style#Theme_Leanback_Browse}.
 * </p>
 */
public class BrowseFragment extends BaseFragment {

    // BUNDLE attribute for saving header show/hide status when backstack is used:
    static final String HEADER_STACK_INDEX = "headerStackIndex";
    // BUNDLE attribute for saving header show/hide status when backstack is not used:
    static final String HEADER_SHOW = "headerShow";


    final class BackStackListener implements FragmentManager.OnBackStackChangedListener {
        int mLastEntryCount;
        int mIndexOfHeadersBackStack;

        BackStackListener() {
            mLastEntryCount = getFragmentManager().getBackStackEntryCount();
            mIndexOfHeadersBackStack = -1;
        }

        void load(Bundle savedInstanceState) {
            if (savedInstanceState != null) {
                mIndexOfHeadersBackStack = savedInstanceState.getInt(HEADER_STACK_INDEX, -1);
                mShowingHeaders = mIndexOfHeadersBackStack == -1;
            } else {
                if (!mShowingHeaders) {
                    getFragmentManager().beginTransaction()
                            .addToBackStack(mWithHeadersBackStackName).commit();
                }
            }
        }

        void save(Bundle outState) {
            outState.putInt(HEADER_STACK_INDEX, mIndexOfHeadersBackStack);
        }


        @Override
        public void onBackStackChanged() {
            if (getFragmentManager() == null) {
                Log.w(TAG, "getFragmentManager() is null, stack:", new Exception());
                return;
            }
            int count = getFragmentManager().getBackStackEntryCount();
            // if backstack is growing and last pushed entry is "headers" backstack,
            // remember the index of the entry.
            if (count > mLastEntryCount) {
                FragmentManager.BackStackEntry entry = getFragmentManager().getBackStackEntryAt(count - 1);
                if (mWithHeadersBackStackName.equals(entry.getName())) {
                    mIndexOfHeadersBackStack = count - 1;
                }
            } else if (count < mLastEntryCount) {
                // if popped "headers" backstack, initiate the show header transition if needed
                if (mIndexOfHeadersBackStack >= count) {
                    mIndexOfHeadersBackStack = -1;
                    if (!mShowingHeaders) {
                        startHeadersTransitionInternal(true);
                    }
                }
            }
            mLastEntryCount = count;
        }
    }

    /**
     * Listener for transitions between browse headers and rows.
     */
    public static class BrowseTransitionListener {
        /**
         * Callback when headers transition starts.
         *
         * @param withHeaders True if the transition will result in headers
         *        being shown, false otherwise.
         */
        public void onHeadersTransitionStart(boolean withHeaders) {
        }
        /**
         * Callback when headers transition stops.
         *
         * @param withHeaders True if the transition will result in headers
         *        being shown, false otherwise.
         */
        public void onHeadersTransitionStop(boolean withHeaders) {
        }
    }

    private class SetSelectionRunnable implements Runnable {
        static final int TYPE_INVALID = -1;
        static final int TYPE_INTERNAL_SYNC = 0;
        static final int TYPE_USER_REQUEST = 1;

        private int mPosition;
        private int mType;
        private boolean mSmooth;

        SetSelectionRunnable() {
            reset();
        }

        void post(int position, int type, boolean smooth) {
            // Posting the set selection, rather than calling it immediately, prevents an issue
            // with adapter changes.  Example: a row is added before the current selected row;
            // first the fast lane view updates its selection, then the rows fragment has that
            // new selection propagated immediately; THEN the rows view processes the same adapter
            // change and moves the selection again.
            if (type >= mType) {
                mPosition = position;
                mType = type;
                mSmooth = smooth;
                mBrowseFrame.removeCallbacks(this);
                mBrowseFrame.post(this);
            }
        }

        @Override
        public void run() {
            setSelection(mPosition, mSmooth);
            reset();
        }

        private void reset() {
            mPosition = -1;
            mType = TYPE_INVALID;
            mSmooth = false;
        }
    }

    private static final String TAG = "BrowseFragment";

    private static final String LB_HEADERS_BACKSTACK = "lbHeadersBackStack_";

    private static boolean DEBUG = false;

    /** The headers fragment is enabled and shown by default. */
    public static final int HEADERS_ENABLED = 1;

    /** The headers fragment is enabled and hidden by default. */
    public static final int HEADERS_HIDDEN = 2;

    /** The headers fragment is disabled and will never be shown. */
    public static final int HEADERS_DISABLED = 3;

    private ContentFragment mCurrentFragment;
    private RowsFragment mRowsFragment;
    private HeadersFragment mHeadersFragment;

    private ObjectAdapter mAdapter;

    private int mHeadersState = HEADERS_ENABLED;
    private int mBrandColor = Color.TRANSPARENT;
    private boolean mBrandColorSet;

    private BrowseFrameLayout mBrowseFrame;
    private boolean mHeadersBackStackEnabled = true;
    private String mWithHeadersBackStackName;
    private boolean mShowingHeaders = true;
    private boolean mCanShowHeaders = true;
    private int mContainerListMarginStart;
    private int mContainerListAlignTop;
    private boolean mRowScaleEnabled = true;
    private OnItemViewSelectedListener mExternalOnItemViewSelectedListener;
    private OnItemViewClickedListener mOnItemViewClickedListener;
    private int mSelectedPosition = -1;

    private PresenterSelector mHeaderPresenterSelector;
    private final SetSelectionRunnable mSetSelectionRunnable = new SetSelectionRunnable();

    // transition related:
    private Object mSceneWithHeaders;
    private Object mSceneWithoutHeaders;
    private Object mSceneAfterEntranceTransition;
    private Object mHeadersTransition;
    private BackStackListener mBackStackChangedListener;
    private BrowseTransitionListener mBrowseTransitionListener;

    private static final String ARG_TITLE = BrowseFragment.class.getCanonicalName() + ".title";
    private static final String ARG_BADGE_URI = BrowseFragment.class.getCanonicalName() + ".badge";
    private static final String ARG_HEADERS_STATE =
            BrowseFragment.class.getCanonicalName() + ".headersState";

    /**
     * Creates arguments for a browse fragment.
     *
     * @param args The Bundle to place arguments into, or null if the method
     *        should return a new Bundle.
     * @param title The title of the BrowseFragment.
     * @param headersState The initial state of the headers of the
     *        BrowseFragment. Must be one of {@link #HEADERS_ENABLED}, {@link
     *        #HEADERS_HIDDEN}, or {@link #HEADERS_DISABLED}.
     * @return A Bundle with the given arguments for creating a BrowseFragment.
     */
    public static Bundle createArgs(Bundle args, String title, int headersState) {
        if (args == null) {
            args = new Bundle();
        }
        args.putString(ARG_TITLE, title);
        args.putInt(ARG_HEADERS_STATE, headersState);
        return args;
    }

    /**
     * Sets the brand color for the browse fragment. The brand color is used as
     * the primary color for UI elements in the browse fragment. For example,
     * the background color of the headers fragment uses the brand color.
     *
     * @param color The color to use as the brand color of the fragment.
     */
    public void setBrandColor(int color) {
        mBrandColor = color;
        mBrandColorSet = true;

        if (mHeadersFragment != null) {
            mHeadersFragment.setBackgroundColor(mBrandColor);
        }
    }

    /**
     * Returns the brand color for the browse fragment.
     * The default is transparent.
     */
    public int getBrandColor() {
        return mBrandColor;
    }

    /**
     * Sets the adapter containing the rows for the fragment.
     *
     * <p>The items referenced by the adapter must be be derived from
     * {@link Row}. These rows will be used by the rows fragment and the headers
     * fragment (if not disabled) to render the browse rows.
     *
     * @param adapter An ObjectAdapter for the browse rows. All items must
     *        derive from {@link Row}.
     */
    public void setAdapter(ObjectAdapter adapter) {
        mAdapter = adapter;
        Object firstElement = mAdapter.get(0);

        if (firstElement instanceof ListRow
                && !(((ListRow) firstElement).getAdapter().get(0) instanceof RowsFragment)
                && !(((ListRow) firstElement).getAdapter().get(0) instanceof ContentFragment)) {

            if (mRowsFragment != null && mHeadersFragment != null) {
                mHeadersFragment.setAdapter(adapter);
                mRowsFragment.setAdapter(adapter);
            }
        } else {
            mRowsFragment = null;
            if (mHeadersFragment != null) {
                mHeadersFragment.setAdapter(adapter);
            }

            mCurrentFragment = (ContentFragment) ((ListRow) firstElement).getAdapter().get(0);
        }
    }

    /**
     * Returns the adapter containing the rows for the fragment.
     */
    public ObjectAdapter getAdapter() {
        return mAdapter;
    }

    /**
     * Sets an item selection listener.
     */
    public void setOnItemViewSelectedListener(OnItemViewSelectedListener listener) {
        mExternalOnItemViewSelectedListener = listener;
    }

    /**
     * Returns an item selection listener.
     */
    public OnItemViewSelectedListener getOnItemViewSelectedListener() {
        return mExternalOnItemViewSelectedListener;
    }

    /**
     * Sets an item clicked listener on the fragment.
     * OnItemViewClickedListener will override {@link View.OnClickListener} that
     * item presenter sets during {@link Presenter#onCreateViewHolder(ViewGroup)}.
     * So in general,  developer should choose one of the listeners but not both.
     */
    public void setOnItemViewClickedListener(OnItemViewClickedListener listener) {
        mOnItemViewClickedListener = listener;
        if (mRowsFragment != null) {
            mRowsFragment.setOnItemViewClickedListener(listener);
        }
    }

    /**
     * Returns the item Clicked listener.
     */
    public OnItemViewClickedListener getOnItemViewClickedListener() {
        return mOnItemViewClickedListener;
    }

    /**
     * Starts a headers transition.
     *
     * <p>This method will begin a transition to either show or hide the
     * headers, depending on the value of withHeaders. If headers are disabled
     * for this browse fragment, this method will throw an exception.
     *
     * @param withHeaders True if the headers should transition to being shown,
     *        false if the transition should result in headers being hidden.
     */
    public void startHeadersTransition(boolean withHeaders) {
        if (!mCanShowHeaders) {
            throw new IllegalStateException("Cannot start headers transition");
        }
        if (isInHeadersTransition() || mShowingHeaders == withHeaders) {
            return;
        }
        startHeadersTransitionInternal(withHeaders);
    }

    /**
     * Returns true if the headers transition is currently running.
     */
    public boolean isInHeadersTransition() {
        return mHeadersTransition != null;
    }

    /**
     * Returns true if headers are shown.
     */
    public boolean isShowingHeaders() {
        return mShowingHeaders;
    }

    /**
     * Sets a listener for browse fragment transitions.
     *
     * @param listener The listener to call when a browse headers transition
     *        begins or ends.
     */
    public void setBrowseTransitionListener(BrowseTransitionListener listener) {
        mBrowseTransitionListener = listener;
    }

    /**
     * Enables scaling of rows when headers are present.
     * By default enabled to increase density.
     *
     * @param enable true to enable row scaling
     */
    public void enableRowScaling(boolean enable) {
        mRowScaleEnabled = enable;
        if (mRowsFragment != null) {
            mRowsFragment.enableRowScaling(mRowScaleEnabled);
        }
    }

    private void startHeadersTransitionInternal(final boolean withHeaders) {
        if (getFragmentManager().isDestroyed()) {
            return;
        }
        mShowingHeaders = withHeaders;
        RowsFragment target = null;
        if (mRowsFragment != null) {
            target = mRowsFragment;
        } else if (mCurrentFragment instanceof RowsFragment) {
            target = (RowsFragment) mCurrentFragment;
        }
        Runnable transitionRunnable = new Runnable() {
            @Override
            public void run() {
                mHeadersFragment.onTransitionStart();
                createHeadersTransition();
                if (mBrowseTransitionListener != null) {
                    mBrowseTransitionListener.onHeadersTransitionStart(withHeaders);
                }
                sTransitionHelper.runTransition(withHeaders ? mSceneWithHeaders : mSceneWithoutHeaders,
                        mHeadersTransition);
                if (mHeadersBackStackEnabled) {
                    if (!withHeaders) {
                        getFragmentManager().beginTransaction()
                                .addToBackStack(mWithHeadersBackStackName).commit();
                    } else {
                        int index = mBackStackChangedListener.mIndexOfHeadersBackStack;
                        if (index >= 0) {
                            FragmentManager.BackStackEntry entry = getFragmentManager().getBackStackEntryAt(index);
                            getFragmentManager().popBackStackImmediate(entry.getId(),
                                    FragmentManager.POP_BACK_STACK_INCLUSIVE);
                        }
                    }
                }
            }
        };
        if (target != null) {
            target.onExpandTransitionStart(!withHeaders, transitionRunnable);
        } else {
            // used for custom fragments, just run the headers transition
            transitionRunnable.run();
        }
    }

    private boolean isVerticalScrolling() {
        // don't run transition
        boolean isScrolling = (mHeadersFragment.getVerticalGridView().getScrollState()
                != HorizontalGridView.SCROLL_STATE_IDLE);
        if (mRowsFragment != null) {
            isScrolling = isScrolling || mRowsFragment.getVerticalGridView().getScrollState()
                    != HorizontalGridView.SCROLL_STATE_IDLE;
        } else if (mCurrentFragment != null && mCurrentFragment instanceof ContentFragment) {
            isScrolling = isScrolling || mCurrentFragment.isScrolling();
        } else if (mCurrentFragment != null && mCurrentFragment instanceof RowsFragment) {
            isScrolling = isScrolling || mRowsFragment.getVerticalGridView().getScrollState()
                    != HorizontalGridView.SCROLL_STATE_IDLE;
        }

        return isScrolling;
    }


    private final BrowseFrameLayout.OnFocusSearchListener mOnFocusSearchListener =
            new BrowseFrameLayout.OnFocusSearchListener() {
                @Override
                public View onFocusSearch(View focused, int direction) {
                    // if headers is running transition,  focus stays
                    if (mCanShowHeaders && isInHeadersTransition()) {
                        return focused;
                    }
                    if (DEBUG) Log.v(TAG, "onFocusSearch focused " + focused + " + direction " + direction);

                    if (getTitleView() != null && focused != getTitleView() &&
                            direction == View.FOCUS_UP) {
                        return getTitleView();
                    }
                    if (getTitleView() != null && getTitleView().hasFocus() &&
                            direction == View.FOCUS_DOWN) {
                        if (mCanShowHeaders && mShowingHeaders) {
                            return mHeadersFragment.getVerticalGridView();
                        } else {
                            if (mRowsFragment != null) {
                                return mRowsFragment.getVerticalGridView();
                            } else if (mCurrentFragment != null && mCurrentFragment instanceof RowsFragment) {
                                return ((RowsFragment) mCurrentFragment).getVerticalGridView();
                            } else {
                                return mCurrentFragment.getFocusRootView();
                            }
                        }
                    }

                    boolean isRtl = ViewCompat.getLayoutDirection(focused) == ViewCompat.LAYOUT_DIRECTION_RTL;
                    int towardStart = isRtl ? View.FOCUS_RIGHT : View.FOCUS_LEFT;
                    int towardEnd = isRtl ? View.FOCUS_LEFT : View.FOCUS_RIGHT;
                    if (mCanShowHeaders && direction == towardStart) {
                        if (isVerticalScrolling() || mShowingHeaders) {
                            return focused;
                        }
                        return mHeadersFragment.getVerticalGridView();
                    } else if (direction == towardEnd) {
                        if (isVerticalScrolling()) {
                            return focused;
                        }
                        if (mRowsFragment != null) {
                            return mRowsFragment.getVerticalGridView();
                        } else if (mCurrentFragment != null && mCurrentFragment instanceof RowsFragment) {
                            return ((RowsFragment) mCurrentFragment).getVerticalGridView();
                        } else {
                            return mCurrentFragment.getFocusRootView();
                        }
                    } else {
                        return null;
                    }
                }
            };

    private final BrowseFrameLayout.OnChildFocusListener mOnChildFocusListener =
            new BrowseFrameLayout.OnChildFocusListener() {

                @Override
                public boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
                    if (getChildFragmentManager().isDestroyed()) {
                        return true;
                    }
                    // Make sure not changing focus when requestFocus() is called.
                    if (mCanShowHeaders && mShowingHeaders) {
                        if (mHeadersFragment != null && mHeadersFragment.getView() != null &&
                                mHeadersFragment.getView().requestFocus(direction, previouslyFocusedRect)) {
                            return true;
                        }
                    }
                    if (mRowsFragment != null && mRowsFragment.getView() != null &&
                            mRowsFragment.getView().requestFocus(direction, previouslyFocusedRect)) {
                        return true;
                    }
                    if (mCurrentFragment != null && mCurrentFragment.getFocusRootView() != null &&
                            mCurrentFragment.getFocusRootView().requestFocus(direction, previouslyFocusedRect)) {
                        return true;
                    }
                    if (getTitleView() != null &&
                            getTitleView().requestFocus(direction, previouslyFocusedRect)) {
                        return true;
                    }
                    return false;
                };

                @Override
                public void onRequestChildFocus(View child, View focused) {
                    if (getChildFragmentManager().isDestroyed()) {
                        return;
                    }
                    if (!mCanShowHeaders || isInHeadersTransition()) return;
                    int childId = child.getId();
                    if (childId == R.id.browse_container_dock && mShowingHeaders) {
                        startHeadersTransitionInternal(false);
                    } else if (childId == R.id.browse_headers_dock && !mShowingHeaders) {
                        startHeadersTransitionInternal(true);
                    }
                }
            };

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mBackStackChangedListener != null) {
            mBackStackChangedListener.save(outState);
        } else {
            outState.putBoolean(HEADER_SHOW, mShowingHeaders);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TypedArray ta = getActivity().obtainStyledAttributes(R.styleable.LeanbackTheme);
        mContainerListMarginStart = (int) ta.getDimension(
                R.styleable.LeanbackTheme_browseRowsMarginStart, getActivity().getResources()
                        .getDimensionPixelSize(R.dimen.lb_browse_rows_margin_start));
        mContainerListAlignTop = (int) ta.getDimension(
                R.styleable.LeanbackTheme_browseRowsMarginTop, getActivity().getResources()
                        .getDimensionPixelSize(R.dimen.lb_browse_rows_margin_top));
        ta.recycle();

        readArguments(getArguments());

        if (mCanShowHeaders) {
            if (mHeadersBackStackEnabled) {
                mWithHeadersBackStackName = LB_HEADERS_BACKSTACK + this;
                mBackStackChangedListener = new BackStackListener();
                getFragmentManager().addOnBackStackChangedListener(mBackStackChangedListener);
                mBackStackChangedListener.load(savedInstanceState);
            } else {
                if (savedInstanceState != null) {
                    mShowingHeaders = savedInstanceState.getBoolean(HEADER_SHOW);
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        if (mBackStackChangedListener != null) {
            getFragmentManager().removeOnBackStackChangedListener(mBackStackChangedListener);
        }
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        if (getChildFragmentManager().findFragmentById(R.id.browse_container_dock) == null) {
            mHeadersFragment = new HeadersFragment();
            if (mRowsFragment == null && mCurrentFragment == null) {
                mRowsFragment = new RowsFragment();
                getChildFragmentManager().beginTransaction()
                        .replace(R.id.browse_headers_dock, mHeadersFragment)
                        .replace(R.id.browse_container_dock, mRowsFragment).commit();
            } else {
                getChildFragmentManager().beginTransaction()
                        .replace(R.id.browse_headers_dock, mHeadersFragment)
                        .replace(R.id.browse_container_dock, (Fragment) mCurrentFragment).commit();
            }
        } else {
            mHeadersFragment = (HeadersFragment) getChildFragmentManager()
                    .findFragmentById(R.id.browse_headers_dock);
            Fragment fragment = getChildFragmentManager()
                    .findFragmentById(R.id.browse_container_dock);
            if (fragment instanceof RowsFragment) {
                mRowsFragment = (RowsFragment) fragment;
            } else {
                mCurrentFragment = (ContentFragment) fragment;
            }
        }

        mHeadersFragment.setHeadersGone(!mCanShowHeaders);

        if (mRowsFragment != null) {
            mRowsFragment.setAdapter(mAdapter);
            mRowsFragment.enableRowScaling(mRowScaleEnabled);
            mRowsFragment.setOnItemViewSelectedListener(mRowViewSelectedListener);
            mRowsFragment.setOnItemViewClickedListener(mOnItemViewClickedListener);
        }

        if (mHeaderPresenterSelector != null) {
            mHeadersFragment.setPresenterSelector(mHeaderPresenterSelector);
        }
        mHeadersFragment.setAdapter(mAdapter);
        mHeadersFragment.setOnHeaderViewSelectedListener(mHeaderViewSelectedListener);
        mHeadersFragment.setOnHeaderClickedListener(mHeaderClickedListener);

        View root = inflater.inflate(R.layout.lb_browse_fragment, container, false);

        setTitleView((TitleView) root.findViewById(R.id.browse_title_group));

        mBrowseFrame = (BrowseFrameLayout) root.findViewById(R.id.browse_frame);
        mBrowseFrame.setOnChildFocusListener(mOnChildFocusListener);
        mBrowseFrame.setOnFocusSearchListener(mOnFocusSearchListener);

        if (mBrandColorSet) {
            mHeadersFragment.setBackgroundColor(mBrandColor);
        }

        mSceneWithHeaders = sTransitionHelper.createScene(mBrowseFrame, new Runnable() {
            @Override
            public void run() {
                showHeaders(true);
            }
        });
        mSceneWithoutHeaders =  sTransitionHelper.createScene(mBrowseFrame, new Runnable() {
            @Override
            public void run() {
                showHeaders(false);
            }
        });
        mSceneAfterEntranceTransition = sTransitionHelper.createScene(mBrowseFrame, new Runnable() {
            @Override
            public void run() {
                setEntranceTransitionEndState();
            }
        });
        return root;
    }

    private void createHeadersTransition() {
        mHeadersTransition = sTransitionHelper.loadTransition(getActivity(),
                mShowingHeaders ?
                        R.transition.lb_browse_headers_in : R.transition.lb_browse_headers_out);

        sTransitionHelper.setTransitionListener(mHeadersTransition, new TransitionListener() {
            @Override
            public void onTransitionStart(Object transition) {
            }
            @Override
            public void onTransitionEnd(Object transition) {
                mHeadersTransition = null;
                if (mRowsFragment != null) {
                    mRowsFragment.onTransitionEnd();
                } else if (mCurrentFragment != null && mCurrentFragment instanceof RowsFragment) {
                    ((RowsFragment) mCurrentFragment).onTransitionEnd();
                }
                mHeadersFragment.onTransitionEnd();
                if (mShowingHeaders) {
                    VerticalGridView headerGridView = mHeadersFragment.getVerticalGridView();
                    if (headerGridView != null && !headerGridView.hasFocus()) {
                        headerGridView.requestFocus();
                    }
                } else {
                    VerticalGridView rowsGridView = null;
                    if (mRowsFragment != null) {
                        rowsGridView = mRowsFragment.getVerticalGridView();
                    } else if (mCurrentFragment != null && mCurrentFragment instanceof RowsFragment) {
                        rowsGridView = ((RowsFragment) mCurrentFragment).getVerticalGridView();
                    }
                    if (rowsGridView != null && !rowsGridView.hasFocus()) {
                        rowsGridView.requestFocus();
                    }
                }
                toggleTitle();

                if (mBrowseTransitionListener != null) {
                    mBrowseTransitionListener.onHeadersTransitionStop(mShowingHeaders);
                }
            }
        });
    }

    /**
     * Sets the {@link PresenterSelector} used to render the row headers.
     *
     * @param headerPresenterSelector The PresenterSelector that will determine
     *        the Presenter for each row header.
     */
    public void setHeaderPresenterSelector(PresenterSelector headerPresenterSelector) {
        mHeaderPresenterSelector = headerPresenterSelector;
        if (mHeadersFragment != null) {
            mHeadersFragment.setPresenterSelector(mHeaderPresenterSelector);
        }
    }

    private void setRowsAlignedLeft(boolean alignLeft) {
        ViewGroup.MarginLayoutParams lp;
        View containerList;
        if (mRowsFragment != null) {
            containerList = mRowsFragment.getView();
            lp = (ViewGroup.MarginLayoutParams) containerList.getLayoutParams();
            lp.setMarginStart(alignLeft ? 0 : mContainerListMarginStart);
            containerList.setLayoutParams(lp);
        } else if (mCurrentFragment != null && mCurrentFragment instanceof RowsFragment) {
            containerList = ((RowsFragment) mCurrentFragment).getView();
            if (containerList == null) {
                mCurrentFragment.setExtraMargin(mContainerListAlignTop, mContainerListMarginStart);
            } else {
                lp = (ViewGroup.MarginLayoutParams) containerList.getLayoutParams();
                lp.setMarginStart(alignLeft ? 0 : mContainerListMarginStart);
                containerList.setLayoutParams(lp);
            }
        } else {
            containerList = mCurrentFragment.getView();
            if (containerList == null) {
                mCurrentFragment.setExtraMargin(mContainerListAlignTop, mContainerListMarginStart);
            } else {
                lp = (ViewGroup.MarginLayoutParams) containerList.getLayoutParams();
                lp.setMarginStart(alignLeft ? 0 : mContainerListMarginStart);
                containerList.setLayoutParams(lp);
            }
        }
    }

    private void setHeadersOnScreen(boolean onScreen) {
        ViewGroup.MarginLayoutParams lp;
        View containerList;
        containerList = mHeadersFragment.getView();
        lp = (ViewGroup.MarginLayoutParams) containerList.getLayoutParams();
        lp.setMarginStart(onScreen ? 0 : -mContainerListMarginStart);
        containerList.setLayoutParams(lp);
    }

    private void showHeaders(boolean show) {
        if (DEBUG) Log.v(TAG, "showHeaders " + show);
        mHeadersFragment.setHeadersEnabled(show);
        setHeadersOnScreen(show);
        setRowsAlignedLeft(!show);
        if (mRowsFragment != null) {
            mRowsFragment.setExpand(!show);
        } else if (mCurrentFragment != null && mCurrentFragment instanceof RowsFragment) {
            ((RowsFragment) mCurrentFragment).setExpand(!show);
        }
    }

    private HeadersFragment.OnHeaderClickedListener mHeaderClickedListener =
            new HeadersFragment.OnHeaderClickedListener() {
                @Override
                public void onHeaderClicked() {
                    if (!mCanShowHeaders || !mShowingHeaders || isInHeadersTransition()) {
                        return;
                    }
                    startHeadersTransitionInternal(false);
                    if (mRowsFragment != null) {
                        mRowsFragment.getVerticalGridView().requestFocus();
                    } else if (mCurrentFragment != null && mCurrentFragment instanceof RowsFragment) {
                        ((RowsFragment) mCurrentFragment).getVerticalGridView().requestFocus();
                    } else {
                        mCurrentFragment.getFocusRootView().requestFocus();
                    }
                }
            };

    private OnItemViewSelectedListener mRowViewSelectedListener = new OnItemViewSelectedListener() {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                   RowPresenter.ViewHolder rowViewHolder, Row row) {
            int position = -1;
            if (mRowsFragment != null) {
                position = mRowsFragment.getVerticalGridView().getSelectedPosition();
                onRowSelected(position);
            } else if (mCurrentFragment != null && mCurrentFragment instanceof RowsFragment) {
                position = ((RowsFragment) mCurrentFragment).getVerticalGridView().getSelectedPosition();
                toggleTitle();
            }
            if (DEBUG) Log.v(TAG, "row selected position " + position);
            if (mExternalOnItemViewSelectedListener != null) {
                mExternalOnItemViewSelectedListener.onItemSelected(itemViewHolder, item,
                        rowViewHolder, row);
            }
        }
    };

    private HeadersFragment.OnHeaderViewSelectedListener mHeaderViewSelectedListener =
            new HeadersFragment.OnHeaderViewSelectedListener() {
                @Override
                public void onHeaderSelected(RowHeaderPresenter.ViewHolder viewHolder, Row row) {
                    int position = mHeadersFragment.getVerticalGridView().getSelectedPosition();
                    if (DEBUG) Log.v(TAG, "header selected position " + position);

                    // switch fragments (if needed)
                    if (mRowsFragment == null) {
                        ContentFragment nextFragment = (ContentFragment) ((ListRow) mAdapter.get(position)).getAdapter().get(0);
                        FragmentManager cfManager = getChildFragmentManager();
                        Fragment foundFragment = cfManager.findFragmentById(R.id.browse_container_dock);
                        if (foundFragment == null || (foundFragment instanceof ContentFragment && !foundFragment.equals(nextFragment))) {
                            FragmentTransaction transaction = cfManager.beginTransaction();
                            transaction.replace(R.id.browse_container_dock, (Fragment) nextFragment, nextFragment.getTag());
                            transaction.commit();
                            mCurrentFragment = nextFragment;
                            if (nextFragment instanceof RowsFragment) {
                                ((RowsFragment) nextFragment).setOnItemViewSelectedListener(mRowViewSelectedListener);
                                ((RowsFragment) nextFragment).setOnItemViewClickedListener(mOnItemViewClickedListener);
                            }
                            showHeaders(mShowingHeaders);
                        }
                    } else {
                        onRowSelected(position);
                    }
                }
            };

    private void onRowSelected(int position) {
        if (position != mSelectedPosition) {
            mSetSelectionRunnable.post(
                    position, SetSelectionRunnable.TYPE_INTERNAL_SYNC, true);
            toggleTitle();
        }
    }

    private void setSelection(int position, boolean smooth) {
        if (position != NO_POSITION) {
            if (mRowsFragment != null) {
                mRowsFragment.setSelectedPosition(position, smooth);
            }
            mHeadersFragment.setSelectedPosition(position, smooth);
        }
        mSelectedPosition = position;
    }

    /**
     * Sets the selected row position with smooth animation.
     */
    public void setSelectedPosition(int position) {
        setSelectedPosition(position, true);
    }

    /**
     * Sets the selected row position.
     */
    public void setSelectedPosition(int position, boolean smooth) {
        mSetSelectionRunnable.post(
                position, SetSelectionRunnable.TYPE_USER_REQUEST, smooth);
    }

    @Override
    public void onStart() {
        super.onStart();
        mHeadersFragment.setWindowAlignmentFromTop(mContainerListAlignTop);
        mHeadersFragment.setItemAlignment();
        if (mRowsFragment != null) {
            mRowsFragment.setWindowAlignmentFromTop(mContainerListAlignTop);
            mRowsFragment.setItemAlignment();
            mRowsFragment.setScalePivots(0, mContainerListAlignTop);
        } else if (mCurrentFragment instanceof RowsFragment) {
            ((RowsFragment) mCurrentFragment).setWindowAlignmentFromTop(mContainerListAlignTop);
            ((RowsFragment) mCurrentFragment).setItemAlignment();
            ((RowsFragment) mCurrentFragment).setScalePivots(0, mContainerListAlignTop);
            ((RowsFragment) mCurrentFragment).setOnItemViewSelectedListener(mRowViewSelectedListener);
            ((RowsFragment) mCurrentFragment).setOnItemViewClickedListener(mOnItemViewClickedListener);
        } else {
            // FIXME handle custom content
        }

        if (mCanShowHeaders && mShowingHeaders && mHeadersFragment.getView() != null) {
            mHeadersFragment.getView().requestFocus();
        } else if (!mCanShowHeaders || !mShowingHeaders) {
            if (mRowsFragment != null && mRowsFragment.getView() != null) {
                mRowsFragment.getView().requestFocus();
            } else if (mCurrentFragment != null) {
                mCurrentFragment.getFocusRootView().requestFocus();
            }
        }

        if (mCanShowHeaders) {
            showHeaders(mShowingHeaders);
        }
        if (isEntranceTransitionEnabled()) {
            setEntranceTransitionStartState();
        }
    }

    /**
     * Enables/disables headers transition on back key support. This is enabled by
     * default. The BrowseFragment will add a back stack entry when headers are
     * showing. Running a headers transition when the back key is pressed only
     * works when the headers state is {@link #HEADERS_ENABLED} or
     * {@link #HEADERS_HIDDEN}.
     * <p>
     * NOTE: If an Activity has its own onBackPressed() handling, you must
     * disable this feature. You may use {@link #startHeadersTransition(boolean)}
     * and {@link BrowseTransitionListener} in your own back stack handling.
     */
    public final void setHeadersTransitionOnBackEnabled(boolean headersBackStackEnabled) {
        mHeadersBackStackEnabled = headersBackStackEnabled;
    }

    /**
     * Returns true if headers transition on back key support is enabled.
     */
    public final boolean isHeadersTransitionOnBackEnabled() {
        return mHeadersBackStackEnabled;
    }

    private void readArguments(Bundle args) {
        if (args == null) {
            return;
        }
        if (args.containsKey(ARG_TITLE)) {
            setTitle(args.getString(ARG_TITLE));
        }
        if (args.containsKey(ARG_HEADERS_STATE)) {
            setHeadersState(args.getInt(ARG_HEADERS_STATE));
        }
    }

    /**
     * Sets the state for the headers column in the browse fragment. Must be one
     * of {@link #HEADERS_ENABLED}, {@link #HEADERS_HIDDEN}, or
     * {@link #HEADERS_DISABLED}.
     *
     * @param headersState The state of the headers for the browse fragment.
     */
    public void setHeadersState(int headersState) {
        if (headersState < HEADERS_ENABLED || headersState > HEADERS_DISABLED) {
            throw new IllegalArgumentException("Invalid headers state: " + headersState);
        }
        if (DEBUG) Log.v(TAG, "setHeadersState " + headersState);

        if (headersState != mHeadersState) {
            mHeadersState = headersState;
            switch (headersState) {
                case HEADERS_ENABLED:
                    mCanShowHeaders = true;
                    mShowingHeaders = true;
                    break;
                case HEADERS_HIDDEN:
                    mCanShowHeaders = true;
                    mShowingHeaders = false;
                    break;
                case HEADERS_DISABLED:
                    mCanShowHeaders = false;
                    mShowingHeaders = false;
                    break;
                default:
                    Log.w(TAG, "Unknown headers state: " + headersState);
                    break;
            }
            if (mHeadersFragment != null) {
                mHeadersFragment.setHeadersGone(!mCanShowHeaders);
            }
        }
    }

    /**
     * Returns the state of the headers column in the browse fragment.
     */
    public int getHeadersState() {
        return mHeadersState;
    }

    @Override
    protected Object createEntranceTransition() {
        return sTransitionHelper.loadTransition(getActivity(),
                R.transition.lb_browse_entrance_transition);
    }

    @Override
    protected void runEntranceTransition(Object entranceTransition) {
        sTransitionHelper.runTransition(mSceneAfterEntranceTransition,
                entranceTransition);
    }

    @Override
    protected void onEntranceTransitionStart() {
        mHeadersFragment.onTransitionStart();
        if (mRowsFragment != null) {
            mRowsFragment.onTransitionStart();
        } else if (mCurrentFragment != null && mCurrentFragment instanceof RowsFragment) {
            ((RowsFragment) mCurrentFragment).onTransitionStart();
        }
    }

    @Override
    protected void onEntranceTransitionEnd() {
        if (mRowsFragment != null) {
            mRowsFragment.onTransitionEnd();
        } else if (mCurrentFragment != null && mCurrentFragment instanceof RowsFragment) {
            ((RowsFragment) mCurrentFragment).onTransitionStart();
        }
        mHeadersFragment.onTransitionEnd();
    }

    void setSearchOrbViewOnScreen(boolean onScreen) {
        View searchOrbView = getTitleView().getSearchAffordanceView();
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) searchOrbView.getLayoutParams();
        lp.setMarginStart(onScreen ? 0 : -mContainerListMarginStart);
        searchOrbView.setLayoutParams(lp);
    }

    void setEntranceTransitionStartState() {
        setHeadersOnScreen(false);
        setSearchOrbViewOnScreen(false);
        if (mRowsFragment != null) {
            mRowsFragment.setEntranceTransitionState(false);
        } else if (mCurrentFragment != null && mCurrentFragment instanceof RowsFragment) {
            ((RowsFragment) mCurrentFragment).setEntranceTransitionState(false);
        }
    }

    void setEntranceTransitionEndState() {
        setHeadersOnScreen(mShowingHeaders);
        setSearchOrbViewOnScreen(true);
        if (mRowsFragment != null) {
            mRowsFragment.setEntranceTransitionState(true);
        } else if (mCurrentFragment != null && mCurrentFragment instanceof RowsFragment) {
            ((RowsFragment) mCurrentFragment).setEntranceTransitionState(true);
        }
    }

    // this has been exposed to the developer, mainly to allow control over the title block
    // for custom fragments
    public void toggleTitle(boolean show) {
        showTitle(show);
    }

    private void toggleTitle() {
        int headersPosition = mHeadersFragment.getVerticalGridView().getSelectedPosition();
        headersPosition = headersPosition < 0 ? 0 : headersPosition;
        int rowsPosition = 0;
        if (mRowsFragment != null) {
            rowsPosition = mRowsFragment.getVerticalGridView().getSelectedPosition();
        } else if (mCurrentFragment != null && mCurrentFragment instanceof RowsFragment) {
            rowsPosition = ((RowsFragment) mCurrentFragment).getVerticalGridView().getSelectedPosition();
        }
        if ((!mShowingHeaders && rowsPosition == 0) ||
                (mShowingHeaders && headersPosition == 0)) {
            showTitle(true);
        } else {
            showTitle(false);
        }
    }

}
