/*
 *                 Twidere - Twitter client for Android
 *
 *  Copyright (C) 2012-2015 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mariotaku.twidere.fragment

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.RecyclerView.ItemDecoration
import android.view.*
import com.bumptech.glide.RequestManager
import kotlinx.android.synthetic.main.fragment_content_recyclerview.*
import kotlinx.android.synthetic.main.layout_content_fragment_common.*
import org.mariotaku.twidere.R
import org.mariotaku.twidere.activity.iface.IControlBarActivity
import org.mariotaku.twidere.activity.iface.IControlBarActivity.ControlBarShowHideHelper
import org.mariotaku.twidere.adapter.LoadMoreSupportAdapter
import org.mariotaku.twidere.annotation.LoadMorePosition
import org.mariotaku.twidere.fragment.iface.RefreshScrollTopInterface
import org.mariotaku.twidere.util.ContentScrollHandler
import org.mariotaku.twidere.util.RecyclerViewScrollHandler
import org.mariotaku.twidere.util.ThemeUtils
import org.mariotaku.twidere.util.TwidereColorUtils
import org.mariotaku.twidere.view.ExtendedSwipeRefreshLayout
import org.mariotaku.twidere.view.iface.IExtendedView

abstract class AbsContentRecyclerViewFragment<A : LoadMoreSupportAdapter<RecyclerView.ViewHolder>,
        L : RecyclerView.LayoutManager> : BaseFragment(), SwipeRefreshLayout.OnRefreshListener,
        RefreshScrollTopInterface, IControlBarActivity.ControlBarOffsetListener,
        ContentScrollHandler.ContentListSupport<A>, ControlBarShowHideHelper.ControlBarAnimationListener {

    lateinit var layoutManager: L
        protected set
    override lateinit var adapter: A
        protected set
    var itemDecoration: ItemDecoration? = null
        private set

    // Callbacks and listeners
    lateinit var scrollListener: RecyclerViewScrollHandler<A>
    // Data fields
    private val systemWindowInsets = Rect()

    private val refreshCompleteListener: RefreshCompleteListener?
        get() = parentFragment as? RefreshCompleteListener

    val isProgressShowing: Boolean
        get() = progressContainer.visibility == View.VISIBLE

    override var refreshing: Boolean
        get () = swipeLayout.isRefreshing
        set(value) {
            if (isProgressShowing) return
            val currentRefreshing = swipeLayout.isRefreshing
            if (!currentRefreshing) {
                updateRefreshProgressOffset()
            }
            if (!value) {
                refreshCompleteListener?.onRefreshComplete(this)
            }
            if (value == currentRefreshing) return
            val layoutRefreshing = value && adapter.loadMoreIndicatorPosition != LoadMorePosition.NONE
            swipeLayout.isRefreshing = layoutRefreshing
        }

    override fun onControlBarOffsetChanged(activity: IControlBarActivity, offset: Float) {
        updateRefreshProgressOffset()
    }

    override final fun onRefresh() {
        if (!triggerRefresh()) {
            refreshing = false
        }
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        updateRefreshProgressOffset()
    }

    override fun scrollToStart(): Boolean {
        scrollToPositionWithOffset(0, 0)
        recyclerView.stopScroll()
        setControlVisible(true)
        return true
    }

    protected abstract fun scrollToPositionWithOffset(position: Int, offset: Int)

    override fun onControlBarVisibleAnimationFinish(visible: Boolean) {
        updateRefreshProgressOffset()
    }

    override fun setControlVisible(visible: Boolean) {
        val activity = activity
        if (activity is IControlBarActivity) {
            //TODO hide only if top > actionBar.height
            val manager = layoutManager
            if (manager.childCount == 0) return
            val firstView = manager.getChildAt(0)
            if (manager.getPosition(firstView) != 0) {
                activity.setControlBarVisibleAnimate(visible, this)
                return
            }
            val top = firstView.top
            activity.setControlBarVisibleAnimate(visible || top > 0, this)
        }
    }

    var refreshEnabled: Boolean
        get() = swipeLayout.isEnabled
        set(value) {
            swipeLayout.isEnabled = value
        }

    override fun onLoadMoreContents(@LoadMorePosition position: Int) {
        setLoadMoreIndicatorPosition(position)
        refreshEnabled = position == LoadMorePosition.NONE
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is IControlBarActivity) {
            context.registerControlBarOffsetListener(this)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_content_recyclerview, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val backgroundColor = ThemeUtils.getColorBackground(context!!)
        val colorRes = TwidereColorUtils.getContrastYIQ(backgroundColor,
                R.color.bg_refresh_progress_color_light, R.color.bg_refresh_progress_color_dark)
        swipeLayout.setOnRefreshListener(this)
        swipeLayout.setProgressBackgroundColorSchemeResource(colorRes)
        adapter = onCreateAdapter(context!!, requestManager)
        layoutManager = onCreateLayoutManager(context!!)
        scrollListener = RecyclerViewScrollHandler(this, RecyclerViewScrollHandler.RecyclerViewCallback(recyclerView))

        recyclerView.layoutManager = layoutManager
        recyclerView.setHasFixedSize(true)
        val swipeLayout = swipeLayout
        if (swipeLayout is ExtendedSwipeRefreshLayout) {
            swipeLayout.touchInterceptor = object : IExtendedView.TouchInterceptor {
                override fun dispatchTouchEvent(view: View, event: MotionEvent): Boolean {
                    scrollListener.touchListener.onTouch(view, event)
                    return false
                }

                override fun onInterceptTouchEvent(view: View, event: MotionEvent): Boolean {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        updateRefreshProgressOffset()
                    }
                    return false
                }

                override fun onTouchEvent(view: View, event: MotionEvent): Boolean {
                    return false
                }

            }
        } else {
            recyclerView.setOnTouchListener(scrollListener.touchListener)
        }
        setupRecyclerView(context!!, recyclerView)
        recyclerView.adapter = adapter

        scrollListener.touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    }

    protected open fun setupRecyclerView(context: Context, recyclerView: RecyclerView) {
        itemDecoration = onCreateItemDecoration(context, recyclerView, layoutManager)
        if (itemDecoration != null) {
            recyclerView.addItemDecoration(itemDecoration)
        }
    }

    protected abstract fun onCreateLayoutManager(context: Context): L

    override fun onStart() {
        super.onStart()
        recyclerView.addOnScrollListener(scrollListener)
    }

    override fun onStop() {
        recyclerView.removeOnScrollListener(scrollListener)
        super.onStop()
    }


    override fun onDetach() {
        val activity = activity
        if (activity is IControlBarActivity) {
            activity.unregisterControlBarOffsetListener(this)
        }
        super.onDetach()
    }

    protected open val extraContentPadding: Rect
        get() = Rect()

    override fun onApplySystemWindowInsets(insets: Rect) {
        val extraPadding = extraContentPadding
        recyclerView.setPadding(insets.left + extraPadding.left, insets.top + extraPadding.top,
                insets.right + extraPadding.right, insets.bottom + extraPadding.bottom)
        errorContainer.setPadding(insets.left, insets.top, insets.right, insets.bottom)
        progressContainer.setPadding(insets.left, insets.top, insets.right, insets.bottom)
        systemWindowInsets.set(insets)
        updateRefreshProgressOffset()
    }

    open fun setLoadMoreIndicatorPosition(@LoadMorePosition position: Int) {
        adapter.loadMoreIndicatorPosition = position
    }

    override fun triggerRefresh(): Boolean {
        return false
    }

    protected abstract fun onCreateAdapter(context: Context, requestManager: RequestManager): A

    protected open fun onCreateItemDecoration(context: Context, recyclerView: RecyclerView,
            layoutManager: L): ItemDecoration? {
        return null
    }

    protected fun showContent() {
        errorContainer.visibility = View.GONE
        progressContainer.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }

    protected fun showProgress() {
        errorContainer.visibility = View.GONE
        progressContainer.visibility = View.VISIBLE
        recyclerView.visibility = View.VISIBLE
    }

    protected fun showError(icon: Int, text: CharSequence) {
        errorContainer.visibility = View.VISIBLE
        progressContainer.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        errorIcon.setImageResource(icon)
        errorText.text = text
    }

    protected fun showEmpty(icon: Int, text: CharSequence) {
        errorContainer.visibility = View.VISIBLE
        progressContainer.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        errorIcon.setImageResource(icon)
        errorText.text = text
    }

    protected fun updateRefreshProgressOffset() {
        val insets = this.systemWindowInsets
        if (insets.top == 0 || swipeLayout == null || swipeLayout.isRefreshing) {
            return
        }
        val progressCircleDiameter = swipeLayout.progressCircleDiameter
        if (progressCircleDiameter == 0) return
        val progressViewStart = 0 - progressCircleDiameter
        val progressViewEnd = insets.top + resources.getDimensionPixelSize(R.dimen.element_spacing_large)
        swipeLayout.setProgressViewOffset(false, progressViewStart, progressViewEnd)
    }

    interface RefreshCompleteListener {
        fun onRefreshComplete(fragment: AbsContentRecyclerViewFragment<*, *>)
    }
}
