package com.workbuddy.nutstoreasp;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** 应用密码列表适配器 */
public class AspAdapter extends BaseAdapter {

    public interface Listener {
        void onDelete(Api.Asp asp);
    }

    private final Activity ctx;
    private final List<Api.Asp> data = new ArrayList<>();
    private final Listener listener;

    public AspAdapter(Activity ctx, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
    }

    public void setData(List<Api.Asp> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public Object getItem(int position) {
        return data.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = LayoutInflater.from(ctx).inflate(R.layout.item_asp, parent, false);
        }
        // 应用自定义卡片透明度
        v.setBackground(Ui.cardBg(ctx, Store.cardAlpha(ctx)));
        final Api.Asp asp = data.get(position);

        TextView txtName = v.findViewById(R.id.txt_name);
        TextView txtDate = v.findViewById(R.id.txt_date);
        final TextView txtPassword = v.findViewById(R.id.txt_password);
        TextView btnToggle = v.findViewById(R.id.btn_toggle);
        TextView btnCopy = v.findViewById(R.id.btn_copy);
        TextView btnDelete = v.findViewById(R.id.btn_delete);

        txtName.setText(asp.name);
        txtDate.setText("授权日期：" + (asp.creationTime.isEmpty() ? "-" : asp.creationTime));

        // 根据「默认显示密码」设置决定初始状态
        final boolean defaultPlain = Store.showPasswordPlain(ctx);
        final boolean[] shown = {defaultPlain};
        applyMask(txtPassword, btnToggle, asp, shown[0]);

        btnToggle.setOnClickListener(btn -> {
            shown[0] = !shown[0];
            applyMask(txtPassword, btnToggle, asp, shown[0]);
        });

        btnCopy.setOnClickListener(btn -> copyPassword(asp.credential));

        // 点击密码文本：开启「点击复制」时直接复制，否则切换显示/隐藏
        txtPassword.setOnClickListener(ignored -> {
            if (Store.clickToCopy(ctx)) {
                copyPassword(asp.credential);
            } else {
                shown[0] = !shown[0];
                applyMask(txtPassword, btnToggle, asp, shown[0]);
            }
        });

        btnDelete.setOnClickListener(btn -> {
            if (listener != null) listener.onDelete(asp);
        });

        return v;
    }

    private void applyMask(TextView txtPassword, TextView btnToggle, Api.Asp asp, boolean show) {
        if (show) {
            txtPassword.setText(asp.credential);
            txtPassword.setTextColor(0xFF243342);
            btnToggle.setText("隐藏");
        } else {
            txtPassword.setText("••••••••••••");
            txtPassword.setTextColor(0xFF7A8BA0);
            btnToggle.setText("显示");
        }
    }

    private void copyPassword(String pwd) {
        ClipboardManager cm = (ClipboardManager)
                ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("app_password", pwd));
            Toast.makeText(ctx, "应用密码已复制", Toast.LENGTH_SHORT).show();
        }
    }
}
