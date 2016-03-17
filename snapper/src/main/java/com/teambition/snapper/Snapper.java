package com.teambition.snapper;

import android.text.TextUtils;
import android.util.Log;

import io.socket.emitter.Emitter;
import io.socket.engineio.client.Socket;
import io.socket.engineio.parser.Packet;

/**
 * Created by zeatual on 16/3/15.
 */
public class Snapper {

    private static Snapper snapper;

    private Socket socket;

    private String hostname;
    private String query;

    private boolean log;
    private boolean autoRetry;
    private int maxRetryTimes = 10;
    private int retryInterval = 10 * 1000;
    private Listener listener;

    private int retryCount;

    public static Snapper getInstance() {
        if (snapper == null) {
            snapper = new Snapper();
        }
        return snapper;
    }

    public void init(String hostname, String query) {
        this.hostname = hostname;
        this.query = query;
    }

    public boolean checkInit() {
        return !TextUtils.isEmpty(hostname);
    }

    public void setAutoRetry(boolean autoRetry) {
        if (snapper != null) {
            snapper.autoRetry = autoRetry;
        }
    }

    public void setListener(Listener listener) {
        if (snapper != null) {
            snapper.listener = listener;
        }
    }

    public void setMaxRetryTimes(int times) {
        if (snapper != null) {
            snapper.maxRetryTimes = times;
        }
    }

    public void setRetryInterval(int times) {
        if (snapper != null) {
            snapper.retryInterval = times;
        }
    }

    public void log(boolean log) {
        if (snapper != null) {
            snapper.log = log;
        }
    }

    private void setSocket(final boolean autoRetry, final Listener listener) {
        socket.on(Socket.EVENT_UPGRADE, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                retryCount = 0;
                if (listener != null) {
                    listener.onOpen();
                }
            }
        }).on(Socket.EVENT_MESSAGE, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                if (listener != null && args.length > 0) {
                    listener.onMessage((String) args[0]);
                }
            }
        }).on(Socket.EVENT_ERROR, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                if (listener != null && args.length > 0) {
                    log("Snapper", "error:" + ((Exception) args[0]).getMessage());
                    listener.onError((Exception) args[0]);
                }
            }
        }).on(Socket.EVENT_PACKET, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                Packet packet = (Packet) args[0];
                log("Snapper", "packet: {type:\"" + packet.type + "\", data:\"" + packet.data + "\"}");
            }
        }).on(Socket.EVENT_PING, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                log("Snapper", "ping");
            }
        }).on(Socket.EVENT_PONG, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                log("Snapper", "pong");
            }
        }).on(Socket.EVENT_CLOSE, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                log("Snapper", "close: " + args[0]);
                if (listener != null) {
                    listener.onClose();
                }
                if (autoRetry && retryCount < maxRetryTimes) {
                    try {
                        if (retryCount > 0) {
                            Thread.sleep(retryInterval);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } finally {
                        log("Snapper", "retrying: " + retryCount + "/" + maxRetryTimes);
                        open();
                        retryCount++;
                    }

                }
            }
        });
    }

    public void open() {
        Socket.Options options = new Socket.Options();
        options.hostname = hostname;
        options.query = query;
        socket = new Socket(options);
        setSocket(autoRetry, listener);
        socket.open();
    }

    public void close() {
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }

    public interface Listener {
        void onOpen();

        void onMessage(String msg);

        void onError(Exception e);

        void onClose();
    }

    private void log(String a, String msg) {
        if (log) {
            Log.d(a, msg);
        }
    }

}