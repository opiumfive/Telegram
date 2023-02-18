package org.telegram.ui.reactions;

import android.content.Context;
import android.text.TextUtils;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StickerTabView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class PopupReactionsAdapter extends RecyclerListView.SelectionAdapter {

    private Context mContext;
    private ArrayList<TLRPC.TL_availableReaction> reactions;
    private int hidePosition = -1;
    private LinearLayoutManager layoutManager;
    private Random random = new Random();
    private long lastTime = 0;
    private boolean alive = true;

    Choreographer.FrameCallback frameCallback = frameTimeNanos -> {
        if (System.currentTimeMillis() - lastTime >= 500) {
            lastTime = System.currentTimeMillis();
            iterateAndAnimate();
        }

        if (alive) {
            Choreographer.getInstance().postFrameCallbackDelayed(PopupReactionsAdapter.this.frameCallback, 500);
        }
    };

    public void stop() {
        alive = false;
    }

    public PopupReactionsAdapter(Context context) {
        mContext = context;
    }

    public PopupReactionsAdapter(Context context, ArrayList<TLRPC.TL_availableReaction> availableReactions, LinearLayoutManager manager) {
        mContext = context;
        reactions = availableReactions;
        layoutManager = manager;
        Choreographer.getInstance().postFrameCallbackDelayed(PopupReactionsAdapter.this.frameCallback, 500);
    }

    @Override
    public int getItemCount() {
        if (reactions != null && !reactions.isEmpty()) {
            return reactions.size();
        }
        return 0;
    }

    public Object getItem(int i) {
        if (reactions != null && !reactions.isEmpty()) {
            return i >= 0 && i < reactions.size() ? reactions.get(i) : null;
        }
        return null;
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        return false;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        ReactionView v = new ReactionView(mContext);
        RecyclerView.ViewHolder holder = new RecyclerListView.Holder(v);
        return holder;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        ReactionView cell = (ReactionView) holder.itemView;
        if (hidePosition == holder.getAdapterPosition()) {
            cell.setVisibility(View.INVISIBLE);
        }  else {
            cell.setReaction(reactions.get(holder.getAdapterPosition()), false);
        }
    }

    public void setItems(List<TLRPC.TL_availableReaction> list) {
        reactions = new ArrayList<>(list);
        notifyDataSetChanged();
    }

    public void hideReactionForAnimation(int position) {
        hidePosition = position;
        notifyItemChanged(position);
    }

    private void iterateAndAnimate() {
        int firstVisible = 0;
        int lastVisible = 0;
        if (layoutManager != null) {
            firstVisible = layoutManager.findFirstVisibleItemPosition();
            lastVisible = layoutManager.findLastVisibleItemPosition();

            for (int i = firstVisible; i <= lastVisible; i++) {
                View child = layoutManager.findViewByPosition(i);
                if (child == null) {
                    alive = false;
                    break;
                }
                boolean canAnimate = random.nextInt(100) < 25;
                if (child instanceof ReactionView) {
                    ReactionView cell = (ReactionView) child;
                    if (canAnimate) cell.animateOneTime();
                }
            }
        } else {
            alive = false;
        }
    }
}