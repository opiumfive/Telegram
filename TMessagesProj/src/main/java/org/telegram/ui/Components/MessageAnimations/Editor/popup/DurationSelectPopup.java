package org.telegram.ui.Components.MessageAnimations.Editor.popup;

import android.graphics.Color;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.MessageAnimations.Editor.data.AnimationItemType;
import org.telegram.ui.Components.RecyclerListView;

public class DurationSelectPopup {

    private PopupWindow popupWindow;
    private final View anchor;
    private AnimationItemType animationItemType;
    private int[] items = {200, 300, 400, 500, 600, 700, 800, 900, 1000, 1500, 2000, 3000};

    public DurationSelectPopup(View anchor, AnimationItemType animationItemType) {
        this.anchor = anchor;
        this.animationItemType = animationItemType;
        if (animationItemType == AnimationItemType.VideoMessage) {
            items = new int[]{200, 300, 400, 500, 600, 700, 800, 900, 1000};
        }
    }

    public void show(DurationSelectedListener durationSelectedListener) {
        popupWindow = null;
        FrameLayout container = new FrameLayout(anchor.getContext());
        container.setBackground(anchor.getContext().getResources().getDrawable(R.drawable.popup_fixed_alert2).mutate());
        container.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        RecyclerListView recyclerListView = new RecyclerListView(anchor.getContext().getApplicationContext());
        container.addView(recyclerListView);
        recyclerListView.setScrollingTouchSlop(RecyclerView.TOUCH_SLOP_PAGING);
        recyclerListView.setItemAnimator(null);
        recyclerListView.setClipToPadding(false);
        recyclerListView.setSectionsType(2);

        recyclerListView.setLayoutManager(new LinearLayoutManager(anchor.getContext().getApplicationContext()));
        recyclerListView.setOnItemClickListener((view, position) -> {
            int result = items[position];
            if (durationSelectedListener != null) {
                durationSelectedListener.invoke(result);
            }
            if (popupWindow != null) {
                popupWindow.dismiss();
            }
        });
        recyclerListView.setAdapter(new RecyclerListView.SelectionAdapter() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                TextCell textView = new TextCell(anchor.getContext());
                textView.setTextColor(Color.BLACK);
                return new RecyclerListView.Holder(textView);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                TextCell cell = (TextCell) holder.itemView;
                cell.setText(items[position] + "ms", false);
            }

            @Override
            public int getItemCount() {
                return items.length;
            }

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }
        });

        popupWindow = new PopupWindow(container, AndroidUtilities.dp(180), WindowManager.LayoutParams.WRAP_CONTENT, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            popupWindow.showAsDropDown(anchor, 0, 0, Gravity.END);
        } else {
            popupWindow.showAsDropDown(anchor);
        }
    }

    public interface DurationSelectedListener {
        void invoke(int ms);
    }
}
