/*
 * Copyright (c) 2014 Amahi
 *
 * This file is part of Amahi.
 *
 * Amahi is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Amahi is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Amahi. If not, see <http ://www.gnu.org/licenses/>.
 */

package org.amahi.anywhere.tv.presenter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.Presenter;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.squareup.otto.Subscribe;

import org.amahi.anywhere.R;
import org.amahi.anywhere.adapter.ServerFilesMetadataAdapter;
import org.amahi.anywhere.bus.AudioMetadataRetrievedEvent;
import org.amahi.anywhere.bus.BusProvider;
import org.amahi.anywhere.bus.FileMetadataRetrievedEvent;
import org.amahi.anywhere.bus.FileOpeningEvent;
import org.amahi.anywhere.server.client.ServerClient;
import org.amahi.anywhere.server.model.ServerFile;
import org.amahi.anywhere.server.model.ServerShare;
import org.amahi.anywhere.task.AudioMetadataRetrievingTask;
import org.amahi.anywhere.task.FileMetadataRetrievingTask;
import org.amahi.anywhere.util.Intents;
import org.amahi.anywhere.util.Mimes;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainTVPresenter extends Presenter {

    private Context mContext;
    private int mSelectedBackgroundColor = -1;
    private int mDefaultBackgroundColor = -1;
    private ServerClient mServerClient;
    private List<ServerFile> mServerFileList;

    public MainTVPresenter(Context context, ServerClient serverClient, List<ServerFile> serverFiles) {
        mContext = context;
        mServerClient = serverClient;
        mServerFileList = serverFiles;
        BusProvider.getBus().register(this);
    }

    @Override
    public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
        mDefaultBackgroundColor =
                ContextCompat.getColor(parent.getContext(), R.color.background_secondary);
        mSelectedBackgroundColor =
                ContextCompat.getColor(parent.getContext(), R.color.primary);
        ImageCardView cardView = new ImageCardView(parent.getContext()) {
            @Override
            public void setSelected(boolean selected) {
                updateCardBackgroundColor(this, selected);
                super.setSelected(selected);
            }
        };

        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);
        return new ViewHolder(cardView);
    }

    private void updateCardBackgroundColor(ImageCardView view, boolean selected) {
        int color = selected ? mSelectedBackgroundColor : mDefaultBackgroundColor;

        view.setInfoAreaBackgroundColor(color);
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolderArgs, Object item) {
        final ServerFile serverFile = (ServerFile) item;
        ViewHolder viewHolder = (ViewHolder) viewHolderArgs;
        viewHolder.mCardView.setTitleText(serverFile.getName());
        viewHolder.mCardView.setInfoAreaBackgroundColor(mDefaultBackgroundColor);
        viewHolder.mCardView.setBackgroundColor(Color.WHITE);
        if (isMetadataAvailable(serverFile)) {
            View fileView = viewHolder.view;
            fileView.setTag(ServerFilesMetadataAdapter.Tags.SHARE, serverFile.getParentShare());
            fileView.setTag(ServerFilesMetadataAdapter.Tags.FILE, serverFile);
            setUpMetaDimensions(viewHolder);
            new FileMetadataRetrievingTask(mServerClient, fileView, viewHolder).execute();
        } else {
            setUpDimensions(viewHolder);
            if (isDirectory(serverFile))
                setDate(serverFile, viewHolder);
//            else
//                setSize(serverFile, viewHolder);
            if (isImage(serverFile)) {
                setUpImageIcon(serverFile, viewHolder.mCardView.getMainImageView(), getImageUri(serverFile));
            } else if (isAudio(serverFile)) {
                AudioMetadataRetrievingTask.execute(getImageUri(serverFile), viewHolder);
            } else {
                setUpDrawable(serverFile, viewHolder);
            }
        }
        viewHolder.mCardView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isDirectory(serverFile)) {
                    Intent intent = Intents.Builder.with(mContext).buildServerTvFilesActivity(serverFile.getParentShare(), serverFile);
                    mContext.startActivity(intent);
                } else {
                    startFileOpening(serverFile);
                }
            }
        });
    }


    private boolean isMetadataAvailable(ServerFile serverFile) {
        return ServerShare.Tag.MOVIES.equals(serverFile.getParentShare().getTag());
    }

    private void setUpMetaDimensions(ViewHolder viewHolder) {
        viewHolder.mCardView.setMainImageDimensions(400, 500);
    }

    private void setDate(ServerFile serverFile, ViewHolder viewHolder) {
        Date d = serverFile.getModificationTime();
        SimpleDateFormat dt = new SimpleDateFormat("EEE LLL dd yyyy");
        viewHolder.mCardView.setContentText(dt.format(d));
    }

    private void setSize(ServerFile serverFile, ViewHolder viewHolder) {
        viewHolder.mCardView.setContentText(android.text.format.Formatter.formatFileSize(mContext, serverFile.getSize()));
    }

    private void setUpDimensions(ViewHolder viewHolder) {
        viewHolder.mCardView.setMainImageDimensions(400, 300);
    }

    private void setUpDrawable(ServerFile serverFile, ViewHolder viewHolder) {
        viewHolder.mCardView.setMainImageScaleType(ImageView.ScaleType.CENTER_INSIDE);
        viewHolder.mCardView.setMainImage(ContextCompat.getDrawable(mContext, Mimes.getTVFileIcon(serverFile)));
    }

    @Subscribe
    public void onFileMetadataRetrieved(FileMetadataRetrievedEvent event) {
        ServerFile serverFile = event.getFile();
        ViewHolder viewHolder = event.getViewHolder();
        serverFile.setMetaDataFetched(true);
        setDate(serverFile, viewHolder);
        if (event.getFileMetadata() == null) {
            if (isImage(serverFile)) {
                setUpImageIcon(serverFile, viewHolder.mCardView.getMainImageView(), getImageUri(serverFile));
            } else {
                setUpDrawable(serverFile, viewHolder);
            }
        } else {
            setUpImageIcon(serverFile, viewHolder.mCardView.getMainImageView(), Uri.parse(event.getFileMetadata().getArtworkUrl()));
        }
    }

    @Subscribe
    public void onAudioMetadataRetrieved(AudioMetadataRetrievedEvent event) {
        if (event.getAudioAlbumArt() != null) {
            ViewHolder viewHolder = event.getViewHolder();
            if(viewHolder!=null) {
                viewHolder.mCardView.setContentText(event.getAudioArtist() + " - " + event.getAudioAlbum());
                viewHolder.mCardView.getMainImageView().setImageBitmap(event.getAudioAlbumArt());
            }
        } else
            setUpMusicLogo(event.getViewHolder());
    }

    private void setUpMusicLogo(ViewHolder viewHolder) {
        viewHolder.mCardView.setMainImage(ContextCompat.getDrawable(mContext, R.drawable.tv_ic_audio));
    }

    private boolean isImage(ServerFile file) {
        return Mimes.match(file.getMime()) == Mimes.Type.IMAGE;
    }

    private boolean isAudio(ServerFile file) {
        return Mimes.match(file.getMime()) == Mimes.Type.AUDIO;
    }

    private boolean isDirectory(ServerFile file) {
        return Mimes.match(file.getMime()) == Mimes.Type.DIRECTORY;
    }

    private void setUpImageIcon(ServerFile file, ImageView fileIconView, Uri url) {
        Glide.with(fileIconView.getContext())
                .load(url.toString())
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .placeholder(Mimes.getTVFileIcon(file))
                .into(fileIconView);
    }

    private Uri getImageUri(ServerFile file) {
        return mServerClient.getFileUri(file.getParentShare(), file);
    }

    private void startFileOpening(ServerFile file) {
        BusProvider.getBus().post(new FileOpeningEvent(file.getParentShare(), mServerFileList, file));
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
    }

    public class ViewHolder extends Presenter.ViewHolder {
        private ImageCardView mCardView;

        ViewHolder(View view) {
            super(view);
            mCardView = (ImageCardView) view;
        }
    }
}