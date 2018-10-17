package com.d.music.transfer.manager;

import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.d.lib.common.utils.log.ULog;
import com.d.lib.rxnet.RxNet;
import com.d.lib.rxnet.base.Params;
import com.d.lib.rxnet.callback.ProgressCallback;
import com.d.lib.rxnet.callback.SimpleCallback;
import com.d.music.api.API;
import com.d.music.data.Constants;
import com.d.music.data.database.greendao.bean.MusicModel;
import com.d.music.data.database.greendao.bean.TransferModel;
import com.d.music.online.model.SongInfoRespModel;
import com.d.music.utils.FileUtil;

/**
 * Transfer
 * Created by D on 2018/10/10.
 */
public class Transfer {
    public final static String PREFIX_SONG = ".mp3";
    public final static String PREFIX_MV = ".mp4";
    public final static String PREFIX_LRC = ".lrc";
    public final static String PREFIX_DOWNLOAD = ".download";

    public static <T extends MusicModel> void getInfo(@NonNull final T model, final SimpleCallback<T> callback) {
        getInfo(model.songId, new SimpleCallback<MusicModel>() {
            @Override
            public void onSuccess(@NonNull MusicModel response) {
                model.songName = response.songName;
                model.songUrl = response.songUrl;
                model.artistId = response.artistId;
                model.artistName = response.artistName;
                model.albumId = "" + response.albumId;
                model.albumName = response.albumName;
                model.albumUrl = response.albumUrl;
                model.lrcUrl = response.lrcUrl;
                model.fileFolder = response.fileFolder;
                model.filePostfix = response.filePostfix;

                if (callback != null) {
                    callback.onSuccess(model);
                }
            }

            @Override
            public void onError(Throwable e) {
                if (callback != null) {
                    callback.onError(e);
                }
            }
        });
    }

    @SuppressWarnings("unused")
    public static void getInfo(@NonNull final String songId, final SimpleCallback<MusicModel> callback) {
        Params params = new Params(API.SongInfo.rtpType);
        params.addParam(API.SongInfo.songIds, songId);
        RxNet.get(API.SongInfo.rtpType, params)
                .request(new SimpleCallback<SongInfoRespModel>() {
                    @Override
                    public void onSuccess(SongInfoRespModel response) {
                        if (response.data == null || response.data.songList == null
                                || response.data.songList.size() <= 0) {
                            onError(new Exception("Data is empty!"));
                            return;
                        }
                        SongInfoRespModel.DataBean.SongListBean song = response.data.songList.get(0);
                        MusicModel model = new MusicModel();
                        model.songName = song.songName;
                        model.songUrl = song.songLink;
                        model.artistId = song.artistId;
                        model.artistName = song.artistName;
                        model.albumId = "" + song.albumId;
                        model.albumName = song.albumName;
                        model.albumUrl = song.songPicSmall;
                        model.lrcUrl = song.lrcLink;
                        model.fileFolder = Constants.Path.song;
                        model.filePostfix = song.format;

                        if (callback != null) {
                            callback.onSuccess(model);
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        if (callback != null) {
                            callback.onError(e);
                        }
                    }
                });
    }

    public static <T extends MusicModel> void download(final T model, final boolean withLrc,
                                                       final OnTransferCallback<T> callback) {
        download(false, model, withLrc, callback);
    }

    public static <T extends MusicModel> void downloadCache(final T model, final boolean withLrc,
                                                            final OnTransferCallback<T> callback) {
        download(true, model, withLrc, callback);
    }

    private static <T extends MusicModel> void download(final boolean isCache, final T model, final boolean withLrc,
                                                        final OnTransferCallback<T> callback) {
        getInfo(model, new SimpleCallback<T>() {
            @Override
            public void onSuccess(T response) {
                if (callback != null) {
                    callback.onFirst(response);
                }
                // Download song
                if (isCache) {
                    downloadSongCache(model, callback);
                } else {
                    downloadSong(model, callback);
                }

                if (withLrc) {
                    // Download lrc
                    if (isCache) {
                        downloadLrcCache(model, null);
                    } else {
                        downloadLrc(model, null);
                    }
                }
            }

            @Override
            public void onError(Throwable e) {
                callback.onError(model, e);
            }
        });
    }

    private static <T extends MusicModel> void downloadSong(final T model, final OnTransferCallback<T> callback) {
        downloadSong(Constants.Path.song, model, callback);
    }

    private static <T extends MusicModel> void downloadSongCache(final T model, final OnTransferCallback<T> callback) {
        downloadSong(Constants.Path.cache, model, callback);
    }

    private static <T extends MusicModel> void downloadSong(@NonNull final String path, @NonNull final T model,
                                                            final OnTransferCallback<T> callback) {
        final String url = model.songUrl;
        final String name = model.songName + "." + model.filePostfix;
        final String cache = model.songName + "." + model.filePostfix + PREFIX_DOWNLOAD;
        RxNet.download(url)
                .connectTimeout(60 * 1000)
                .readTimeout(60 * 1000)
                .writeTimeout(60 * 1000)
                .retryCount(3)
                .retryDelayMillis(1000)
                .tag(TransferModel.generateId(model))
                .request(path, cache, new ProgressCallback() {

                    Speed speed = new Speed();

                    @Override
                    public void onStart() {

                    }

                    @Override
                    public void onProgress(long currentLength, long totalLength) {
                        ULog.d("dsiner_request--> onProgresss download: " + currentLength + " total: " + totalLength);
                        if (model instanceof TransferModel) {
                            TransferModel transferModel = (TransferModel) model;
                            transferModel.transferState = TransferModel.TRANSFER_STATE_PROGRESS;
                            transferModel.transferCurrentLength = currentLength;
                            transferModel.transferTotalLength = totalLength;
                            transferModel.transferSpeed = speed.calculateSpeed(currentLength);
                            if (transferModel.progressCallback != null) {
                                transferModel.progressCallback.onProgress(currentLength, totalLength);
                            }
                        }
                    }

                    @Override
                    public void onSuccess() {
                        ULog.d("dsiner_request--> onComplete");
                        FileUtil.renameFile(path + cache, path + name);
                        if (model instanceof TransferModel) {
                            TransferModel transferModel = (TransferModel) model;
                            transferModel.transferState = TransferModel.TRANSFER_STATE_DONE;
                            if (transferModel.progressCallback != null) {
                                transferModel.progressCallback.onSuccess();
                            }
                        }
                        if (callback != null) {
                            callback.onSecond(model);
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        ULog.d("dsiner_request--> onError: " + e.getMessage());
                        FileUtil.deleteFile(path + cache);
                        if (model instanceof TransferModel) {
                            TransferModel transferModel = (TransferModel) model;
                            transferModel.transferState = TransferModel.TRANSFER_STATE_ERROR;
                            if (transferModel.progressCallback != null) {
                                transferModel.progressCallback.onError(e);
                            }
                        }
                        if (callback != null) {
                            callback.onError(model, e);
                        }
                    }

                    @Override
                    public void onCancel() {

                    }
                });
    }

    public static <T extends MusicModel> void downloadMV(@NonNull final T model, final OnTransferCallback<T> callback) {
        final String path = Constants.Path.mv;
        final String url = model.songUrl;
        final String name = model.songName + PREFIX_MV;
        final String cache = model.songName + PREFIX_MV + PREFIX_DOWNLOAD;
        RxNet.download(url)
                .connectTimeout(60 * 1000)
                .readTimeout(60 * 1000)
                .writeTimeout(60 * 1000)
                .retryCount(3)
                .retryDelayMillis(1000)
                .tag(TransferModel.generateId(model))
                .request(path, cache, new ProgressCallback() {
                    Speed speed = new Speed();

                    @Override
                    public void onStart() {

                    }

                    @Override
                    public void onProgress(long currentLength, long totalLength) {
                        ULog.d("dsiner_request--> onProgresss download: " + currentLength + " total: " + totalLength);
                        if (model instanceof TransferModel) {
                            TransferModel transferModel = (TransferModel) model;
                            transferModel.transferState = TransferModel.TRANSFER_STATE_PROGRESS;
                            transferModel.transferCurrentLength = currentLength;
                            transferModel.transferTotalLength = totalLength;
                            transferModel.transferSpeed = speed.calculateSpeed(currentLength);
                            if (transferModel.progressCallback != null) {
                                transferModel.progressCallback.onProgress(currentLength, totalLength);
                            }
                        }
                    }

                    @Override
                    public void onSuccess() {
                        ULog.d("dsiner_request--> onComplete");
                        FileUtil.renameFile(path + cache, path + name);
                        if (model instanceof TransferModel) {
                            TransferModel transferModel = (TransferModel) model;
                            transferModel.transferState = TransferModel.TRANSFER_STATE_DONE;
                            if (transferModel.progressCallback != null) {
                                transferModel.progressCallback.onSuccess();
                            }
                        }
                        if (callback != null) {
                            callback.onSecond(model);
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        ULog.d("dsiner_request--> onError: " + e.getMessage());
                        FileUtil.deleteFile(path + cache);
                        if (model instanceof TransferModel) {
                            TransferModel transferModel = (TransferModel) model;
                            transferModel.transferState = TransferModel.TRANSFER_STATE_ERROR;
                            if (transferModel.progressCallback != null) {
                                transferModel.progressCallback.onError(e);
                            }
                        }
                        if (callback != null) {
                            callback.onError(model, e);
                        }
                    }

                    @Override
                    public void onCancel() {

                    }
                });
    }

    private static <T extends MusicModel> void downloadLrc(@NonNull final T model, final SimpleCallback<T> callback) {
        downloadLrc(Constants.Path.lyric, model, callback);
    }

    public static <T extends MusicModel> void downloadLrcCache(@NonNull final T model, final SimpleCallback<T> callback) {
        downloadLrc(Constants.Path.cache, model, callback);
    }

    private static <T extends MusicModel> void downloadLrc(final String path, @NonNull final T model, final SimpleCallback<T> callback) {
        if (TextUtils.isEmpty(model.lrcUrl)) {
            getInfo(model, new SimpleCallback<T>() {
                @Override
                public void onSuccess(T response) {
                    // Download lrc
                    downloadLrc(path, model, callback);
                }

                @Override
                public void onError(Throwable e) {

                }
            });
            return;
        }
        final String url = model.lrcUrl;
        final String name = model.songName + PREFIX_LRC;
        final String cache = model.songName + PREFIX_LRC + PREFIX_DOWNLOAD;
        RxNet.download(url)
                .connectTimeout(60 * 1000)
                .readTimeout(60 * 1000)
                .writeTimeout(60 * 1000)
                .retryCount(3)
                .retryDelayMillis(1000)
                .request(path, cache, new ProgressCallback() {

                    @Override
                    public void onStart() {

                    }

                    @Override
                    public void onProgress(long currentLength, long totalLength) {
                        ULog.d("dsiner_request--> onProgresss --> download: " + currentLength + " total: " + totalLength);
                    }

                    @Override
                    public void onSuccess() {
                        ULog.d("dsiner_request--> onComplete");
                        FileUtil.renameFile(path + cache, path + name);
                        if (callback != null) {
                            callback.onSuccess(model);
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        ULog.d("dsiner_request--> onError: " + e.getMessage());
                        FileUtil.deleteFile(path + cache);
                        if (callback != null) {
                            callback.onError(e);
                        }
                    }

                    @Override
                    public void onCancel() {

                    }
                });
    }

    public interface OnTransferCallback<T extends MusicModel> {
        void onFirst(T model);

        void onSecond(T model);

        void onError(T model, Throwable e);
    }

    public static class Speed {

        /**
         * K单位转换大小, 如 1K=1024 Byte
         */
        private static final int KB = 1024;

        /**
         * M单位转换大小, 如 1M = 1024*1024 Byte
         */
        private static final int MB = KB * KB;

        /**
         * G单位转换大小, 如 1G = 1024*1024*1024 Byte
         */
        private static final long GB = MB * KB;

        private static final int MIN_DELAY_TIME = 1000; // 两次进度更新间隔不能少于1000ms

        private float speed;
        private long currentLength;
        private long lastLength;
        private long lastTime;

        public float calculateSpeed(long current) {
            currentLength = current;
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastTime >= MIN_DELAY_TIME || lastTime == 0) {
                if (lastTime != 0 && currentTime - lastTime > 0 && currentLength - lastLength >= 0) {
                    speed = 1f * (currentLength - lastLength) / ((currentTime - lastTime) / 1000);
                } else {
                    speed = 0;
                }
                lastLength = currentLength;
                lastTime = currentTime;
            }
            return speed;
        }

        public static String formatSpeed(float speed) {
            if (speed <= 0) {
                return "0KB/S";
            } else if (speed < KB) {
                return String.format("%.0f B/S", speed);
            } else if (speed < MB) {
                return String.format("%.2f KB/S", speed / KB);
            } else if (speed < GB) {
                return String.format("%.2f MB/S", speed / MB);
            } else {
                return String.format("%.2f GB/S", speed / GB);
            }
        }

        public static String formatInfo(float currentLength, float totalLength) {
            return String.format("%.2fM/%.2fM", currentLength / MB, totalLength / MB);
        }
    }
}
