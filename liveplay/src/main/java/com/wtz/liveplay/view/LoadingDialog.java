package com.wtz.liveplay.view;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Build;
import android.widget.ProgressBar;

import com.wtz.liveplay.R;

public class LoadingDialog {

    private Dialog mDialog;
    private DialogInterface.OnCancelListener mOnCancelListener;
    private DialogInterface.OnDismissListener mOnDismissListener;

    public LoadingDialog(Context context) {
        mDialog = new Dialog(context, R.style.NormalDialogStyle);
        ProgressBar progressBar = new ProgressBar(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ColorStateList colorStateList = ColorStateList.valueOf(Color.RED);
            progressBar.setIndeterminateTintList(colorStateList);
            progressBar.setIndeterminateTintMode(PorterDuff.Mode.SRC_ATOP);
        } else {
            progressBar.setIndeterminateDrawable(context.getResources()
                    .getDrawable(R.drawable.circle_progress_bar_anim));
        }
        mDialog.setContentView(progressBar);

        mDialog.setCanceledOnTouchOutside(true);
        mDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (mOnCancelListener != null) {
                    mOnCancelListener.onCancel(dialog);
                }
            }
        });
        mDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (mOnDismissListener != null) {
                    mOnDismissListener.onDismiss(dialog);
                }
            }
        });
    }

    public void setOnCancelListener(DialogInterface.OnCancelListener listener) {
        this.mOnCancelListener = listener;
    }

    public void setOnDismissListener(DialogInterface.OnDismissListener listener) {
        this.mOnDismissListener = listener;
    }

    public void show() {
        mDialog.show();
    }

    public void cancel() {
        // 不用判断 mDialog.isShowing()
        mDialog.cancel();
    }

}
