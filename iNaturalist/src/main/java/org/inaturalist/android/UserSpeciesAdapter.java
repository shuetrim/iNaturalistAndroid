package org.inaturalist.android;


import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.koushikdutta.urlimageviewhelper.UrlImageViewCallback;
import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;

class UserSpeciesAdapter extends ArrayAdapter<String> implements AbsListView.OnScrollListener {
    private ArrayList<JSONObject> mResultList;
    private Context mContext;
    private int mViewType;
    private int mDimension;
    private PullToRefreshGridViewExtended mGrid;

    public static final int VIEW_TYPE_LIST = 0x1000;
    public static final int VIEW_TYPE_GRID = 0x1001;
    public static final int VIEW_TYPE_CARDS = 0x1002;

    private boolean mIsScrolling = false;

    public UserSpeciesAdapter(Context context, ArrayList<JSONObject> results) {
        this(context, results, VIEW_TYPE_LIST, null);
    }

    public UserSpeciesAdapter(Context context, ArrayList<JSONObject> results, int viewType, PullToRefreshGridViewExtended grid) {
        super(context, android.R.layout.simple_list_item_1);

        mContext = context;
        mResultList = results;
        mViewType = viewType;
        mGrid = grid;

        mObservationPhotoNames = new HashMap<>();
        mImageViews = new HashMap<>();
        mObservationLoaded = new HashMap<>();
    }

    @Override
    public int getCount() {
        return (mResultList != null ? mResultList.size() : 0);
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        int layout;
        switch (mViewType) {
            case VIEW_TYPE_CARDS:
                layout = R.layout.mission_grid_item;
                break;
            case VIEW_TYPE_GRID:
                layout = R.layout.observation_grid_item;
                break;
            case VIEW_TYPE_LIST:
            default:
                layout = R.layout.user_profile_species_item;
        }

        View view = (convertView != null) ? convertView : inflater.inflate(layout, parent, false);
        JSONObject item = null;
        try {
            item = mResultList.get(position).getJSONObject("taxon");
        } catch (JSONException e) {
            e.printStackTrace();
            return view;
        }

        TextView speciesName = (TextView) view.findViewById(mViewType == VIEW_TYPE_LIST ? R.id.species_name : R.id.species_guess);

        // Get the taxon display name according to device locale
        try {
            ImageView speciesPic = (ImageView) view.findViewById(mViewType == VIEW_TYPE_LIST ? R.id.species_pic : R.id.observation_pic);
            ImageView speciesIconicPic = (ImageView) view.findViewById(R.id.observation_iconic_pic);

            if (mViewType == VIEW_TYPE_GRID) {
                mDimension = mGrid.getColumnWidth();
                speciesPic.setLayoutParams(new RelativeLayout.LayoutParams(mDimension, mDimension));

                int newDimension = (int) (mDimension * 0.48); // So final image size will be 48% of original size
                int speciesGuessHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, mContext.getResources().getDisplayMetrics());
                int leftRightMargin = (mDimension - newDimension) / 2;
                int topBottomMargin = (mDimension - speciesGuessHeight - newDimension) / 2;
                RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(newDimension, newDimension);
                layoutParams.setMargins(leftRightMargin, topBottomMargin, leftRightMargin, 0);
                speciesIconicPic.setLayoutParams(layoutParams);
            } else if ((mViewType == VIEW_TYPE_CARDS) && (mGrid != null)) {
                mDimension = mGrid.getColumnWidth();
                int lineHeight = speciesName.getLineHeight();
                if (convertView == null) view.setLayoutParams(new LinearLayout.LayoutParams(mDimension, mDimension + (lineHeight * 2)));
            }

            TextView scienceName = (TextView) view.findViewById(R.id.species_science_name);
            JSONObject defaultName = item.optJSONObject("default_name");

            if (defaultName != null) {
                speciesName.setText(defaultName.getString("name"));
                if (mViewType == VIEW_TYPE_LIST) scienceName.setText(item.getString("name"));
            } else {
                String preferredCommonName = item.optString("preferred_common_name", "");
                if (preferredCommonName.length() == 0) preferredCommonName = item.optString("english_common_name");
                if (preferredCommonName.length() == 0) {
                    speciesName.setText(item.getString("name"));
                    if (mViewType == VIEW_TYPE_LIST) scienceName.setVisibility(View.GONE);
                } else {
                    speciesName.setText(preferredCommonName);
                    if (mViewType == VIEW_TYPE_LIST) scienceName.setText(item.getString("name"));
                }
            }

            String photoUrl = item.optString("photo_url");
            if (item.has("taxon_photos")) {
                JSONArray taxonPhotos = item.optJSONArray("taxon_photos");
                if ((taxonPhotos != null) && (taxonPhotos.length() > 0)) {
                    JSONObject photo = taxonPhotos.getJSONObject(0);
                    JSONObject photoInner = photo.optJSONObject("photo");
                    if ((photoInner != null) && (!photoInner.isNull("medium_url"))) photoUrl = photoInner.optString("medium_url");
                }
            } else if (item.has("default_photo")) {
                JSONObject defaultPhoto = item.getJSONObject("default_photo");
                if (defaultPhoto.has("medium_url")) photoUrl = defaultPhoto.getString("medium_url");
            }

            speciesPic.setVisibility(View.INVISIBLE);
            speciesIconicPic.setVisibility(View.VISIBLE);
            speciesIconicPic.setImageResource(ObservationPhotosViewer.observationIcon(item));

            if (photoUrl != null) {
                if (!mIsScrolling) {
                    // Only load image if user is not scrolling
                    loadObsImage(position, speciesPic, photoUrl);
                }
            } else {
                speciesPic.setVisibility(View.INVISIBLE);
            }

            mObservationPhotoNames.put(position, photoUrl);
            mImageViews.put(position, speciesPic);

            if (mViewType == VIEW_TYPE_LIST) {
                TextView speciesCount = (TextView) view.findViewById(R.id.species_count);
                int obsCount = mResultList.get(position).optInt("count", -1);
                int count = obsCount > -1 ? obsCount : item.getInt("observations_count");
                DecimalFormat formatter = new DecimalFormat("#,###,###");
                speciesCount.setText(formatter.format(count));
            }

            view.setTag(item);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return view;
    }

    private void loadObsImage(final int position, ImageView imageView, String url) {
        UrlImageViewCallback callback = new UrlImageViewCallback() {
            @Override
            public void onLoaded(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                if (loadedBitmap != null)
                    imageView.setImageBitmap(ImageUtils.getRoundedCornerBitmap(loadedBitmap, 4));
                if (mViewType == VIEW_TYPE_GRID) {
                    imageView.setLayoutParams(new RelativeLayout.LayoutParams(mDimension, mDimension));
                }

                imageView.setVisibility(View.VISIBLE);

                if ((!mObservationLoaded.containsKey(position)) || (mObservationLoaded.get(position) == false)) {
                    Animation animation = AnimationUtils.loadAnimation(mContext, R.anim.slow_fade_in);
                    imageView.startAnimation(animation);
                    mObservationLoaded.put(position, true);
                }
            }

            @Override
            public Bitmap onPreSetBitmap(ImageView imageView, Bitmap loadedBitmap, String url, boolean loadedFromCache) {
                return loadedBitmap;
            }
        };

        UrlImageViewHelper.setUrlDrawable(imageView, url, callback);
    }

    private HashMap<Integer, ImageView> mImageViews;
    private HashMap<Integer, String> mObservationPhotoNames;
    private HashMap<Integer, Boolean> mObservationLoaded;

    // Load an observation image for one of the views
    private void loadImageByPosition(int position) {
        if (!mImageViews.containsKey(position) || !mObservationPhotoNames.containsKey(position)) return;

        ImageView imageView = mImageViews.get(position);
        String photoName = mObservationPhotoNames.get(position);

        if ((photoName == null) || (imageView == null)) return;

        loadObsImage(position, imageView, photoName);
    }


    @Override
    public void onScrollStateChanged(AbsListView listView, int state) {
        switch (state) {
            case SCROLL_STATE_FLING:
            case SCROLL_STATE_TOUCH_SCROLL:
                mIsScrolling = true;
                break;
            case SCROLL_STATE_IDLE:
                mIsScrolling = false;
                for (int visiblePosition = listView.getFirstVisiblePosition(); visiblePosition <= listView.getLastVisiblePosition(); visiblePosition++) {
                    loadImageByPosition(visiblePosition);
                }
                break;
        }
    }
    @Override
    public void onScroll(AbsListView absListView, int i, int i1, int i2) {

    }
}

