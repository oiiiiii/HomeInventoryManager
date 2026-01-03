package com.example.homeinventorymanager;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

public abstract class DbAsyncTask<Params, Progress, Result> extends AsyncTask<Params, Progress, Result> {

    protected Context mContext;
    private OnDbOperationListener<Result> mListener;

    public DbAsyncTask(Context context, OnDbOperationListener<Result> listener) {
        this.mContext = context.getApplicationContext();
        this.mListener = listener;
    }

    @Override
    protected abstract Result doInBackground(Params... params);

    @Override
    protected void onPostExecute(Result result) {
        super.onPostExecute(result);
        if (mListener != null) {
            mListener.onDbOperationCompleted(result);
        }
    }

    public interface OnDbOperationListener<Result> {
        void onDbOperationCompleted(Result result);
    }
}