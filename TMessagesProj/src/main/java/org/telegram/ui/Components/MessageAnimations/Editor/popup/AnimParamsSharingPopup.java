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
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.RecyclerListView;

public class AnimParamsSharingPopup {

    private PopupWindow popupWindow;
    private final View anchor;
    private String[] items = {"Share Parameters", "Import Parameters", "Restore to Default"};

    public AnimParamsSharingPopup(View anchor) {
        this.anchor = anchor;
    }

    public void show(ImportListener importListener) {
        popupWindow = null;
        FrameLayout container = new FrameLayout(anchor.getContext());
        container.setBackground(anchor.getContext().getResources().getDrawable(R.drawable.popup_fixed_alert2).mutate());
        container.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        RecyclerListView recyclerListView = new RecyclerListView(anchor.getContext());
        container.addView(recyclerListView);
        recyclerListView.setScrollingTouchSlop(RecyclerView.TOUCH_SLOP_PAGING);
        recyclerListView.setItemAnimator(null);
        recyclerListView.setClipToPadding(false);
        recyclerListView.setSectionsType(2);

        recyclerListView.setLayoutManager(new LinearLayoutManager(anchor.getContext()));
        recyclerListView.setOnItemClickListener((view, position) -> {
            if (importListener != null) {
                importListener.invoke(position);
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

                return new RecyclerListView.Holder(textView);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                TextCell cell = (TextCell) holder.itemView;
                cell.setText(items[position], false);
                if (position == 2) {
                    cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText2));
                } else {
                    cell.setTextColor(Color.BLACK);
                }
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

        popupWindow = new PopupWindow(container, AndroidUtilities.dp(250), WindowManager.LayoutParams.WRAP_CONTENT, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            popupWindow.showAsDropDown(anchor, 0, 0, Gravity.END);
        } else {
            popupWindow.showAsDropDown(anchor);
        }
    }

    public interface ImportListener {
        void invoke(int index);
    }
}
