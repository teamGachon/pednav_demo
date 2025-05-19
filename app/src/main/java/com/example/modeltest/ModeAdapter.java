package com.example.modeltest;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ModeAdapter extends RecyclerView.Adapter<ModeAdapter.ModeViewHolder> {
    private List<Mode> modes;

    public ModeAdapter(List<Mode> modes){
        this.modes = modes;
    }

    @NonNull
    @Override
    public ModeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.mode_item, parent, false);
        return new ModeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ModeViewHolder holder, int position){
        Mode mode = modes.get(position);
        holder.tvModeName.setText(mode.getName());
        holder.ivModeImage.setImageResource(mode.getImageResId());
    }

    @Override
    public int getItemCount(){
        return modes.size();
    }

    static class ModeViewHolder extends RecyclerView.ViewHolder{
        TextView tvModeName;
        ImageView ivModeImage;

        public ModeViewHolder(@NonNull View itemView){
            super(itemView);
            tvModeName = itemView.findViewById(R.id.tv_mode_name);
            ivModeImage = itemView.findViewById(R.id.iv_mode_icon);
        }
    }
}
