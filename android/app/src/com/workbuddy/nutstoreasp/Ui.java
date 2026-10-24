package com.workbuddy.nutstoreasp;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

/** UI 工具 */
public class Ui {

    /** 生成卡片背景（白色 + 可调透明度 + 圆角） */
    public static GradientDrawable cardBg(Context ctx, int alpha) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(android.graphics.Color.argb(alpha, 255, 255, 255));
        d.setCornerRadius(dp(ctx, 12));
        return d;
    }

    public static int dp(Context ctx, int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
