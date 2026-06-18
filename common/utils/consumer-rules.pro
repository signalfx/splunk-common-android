-dontwarn com.splunk.android.common.utils.**

# ViewExt
-keepnames class androidx.recyclerview.widget.RecyclerView

-keepnames class androidx.viewpager2.widget.ViewPager2$RecyclerViewImpl

-keepclassmembers class androidx.viewpager2.widget.ViewPager2 {
    private androidx.recyclerview.widget.LinearLayoutManager mLayoutManager;
}

-keepnames class androidx.viewpager.widget.ViewPager

-keepclassmembers class androidx.viewpager.widget.ViewPager {
    private final java.util.ArrayList mItems;
}

-keepclassmembers class androidx.viewpager.widget.ViewPager$ItemInfo {
    java.lang.Object object;
    int position;
}

-keepnames class android.support.v7.widget.RecyclerView

-keepclassmembers class android.support.v7.widget.RecyclerView {
    android.support.v7.widget.RecyclerView$LayoutManager mLayout;
}

-keepclassmembers class android.support.v7.widget.RecyclerView$LayoutManager {
    public int getPosition(android.view.View);
}

-keepnames class com.google.android.material.tabs.TabLayout$TabView

# WindowCallbackManager
-keepnames class androidx.appcompat.view.WindowCallbackWrapper

# FragmentTransactionObserver
-keepnames class androidx.fragment.app.SpecialEffectsController {
    final java.util.ArrayList mPendingOperations;
    final java.util.ArrayList pendingOperations;
}

-keepnames class androidx.fragment.app.SpecialEffectsController$Operation {
    private final androidx.fragment.app.Fragment mFragment;
    private final androidx.fragment.app.Fragment fragment;
    final void addCompletionListener(java.lang.Runnable);
}