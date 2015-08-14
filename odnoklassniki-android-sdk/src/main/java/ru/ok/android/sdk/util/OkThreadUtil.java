package ru.ok.android.sdk.util;

import android.os.Handler;
import android.os.Looper;

public class OkThreadUtil {
	
	private static Handler sMainThreadHandler;

	/**
	 * @return handler associated with the main (UI) thread.
	 */
	public static final Handler getMainThreadHandler() {
		if (sMainThreadHandler == null) {
			sMainThreadHandler = new Handler(Looper.getMainLooper());
		}
		return sMainThreadHandler;
	}

	/**
	 * @return true if we are currently in the main (UI) thread.
	 */
	public static final boolean isMainThread() {
		return Looper.getMainLooper() == Looper.myLooper();
	}

	/**
	 * Executes a task in the main thread.<br>
	 * If we are currently in the main thread, task will be executed immediately.
	 * 
	 * @param toExecute
	 *            - task to execute.
	 */
	public static final void executeOnMain(final Runnable toExecute) {
		if (isMainThread()) {
			toExecute.run();
		} else {
			queueOnMain(toExecute, 0);
		}
	}

	/**
	 * Queues a task in the main thread to be executed after a certain delay.<br>
	 * 
	 * @param toExecute
	 *            - task to execute.
	 */
	public static final void queueOnMain(final Runnable toExecute, final long delayMillis) {
		getMainThreadHandler().postDelayed(toExecute, delayMillis);
	}

}
