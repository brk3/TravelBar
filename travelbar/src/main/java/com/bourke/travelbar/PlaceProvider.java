package com.bourke.travelbar;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Code adapted from http://goo.gl/qXI3l7
 */

public class PlaceProvider extends ContentProvider {

    public static final String TAG = "TravelBar/PlaceProvider";

    public static final String AUTHORITY = "com.bourke.travelbar.PlaceProvider";

    public static final Uri SEARCH_URI = Uri.parse("content://"+AUTHORITY+"/search");
    public static final Uri DETAILS_URI = Uri.parse("content://"+AUTHORITY+"/details");

    private static final int SEARCH = 1;
    private static final int SUGGESTIONS = 2;
    private static final int DETAILS = 3;

    // Defines a set of uris allowed with this content provider
    private static final UriMatcher mUriMatcher = buildUriMatcher();

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        MatrixCursor cursor = null;

        switch(mUriMatcher.match(uri)) {
            case SUGGESTIONS:
                cursor = new MatrixCursor(new String[] {
                        "_id",
                        SearchManager.SUGGEST_COLUMN_TEXT_1,
                        SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA
                });

                // Get Places from Google Places API
                String response = getPlaces(selectionArgs);
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, response);
                }

                try {
                    JSONObject json = new JSONObject(response);
                    JSONArray predictions = json.getJSONArray("predictions");

                    // Creating cursor object with places
                    for (int i=0; i < predictions.length(); i++) {
                        JSONObject place = predictions.getJSONObject(i);

                        // Adding place details to cursor
                        String[] row = new String[] {
                                Integer.toString(i),
                                place.getString("description"),
                                place.getString("reference")
                        };
                        cursor.addRow(row);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                break;

            case SEARCH:
                // TODO
                break;

            case DETAILS:
                // TODO
                break;
            }
        return cursor;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public boolean onCreate() {
        return false;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    /** A method to download json data from url */
    private String downloadUrl(String strUrl) throws IOException {
        String data = "";
        InputStream in = null;
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(strUrl);

            // Creating an http connection to communicate with url
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.connect();

            // Reading data from url
            in = urlConnection.getInputStream();

            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            StringBuilder sb  = new StringBuilder();

            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

            data = sb.toString();
            br.close();
        } finally {
            in.close();
            urlConnection.disconnect();
        }

        return data;
    }

    private String getPlaceDetailsUrl(String ref) {
        // reference of place
        String reference = "reference=" + ref;
        String sensor = "sensor=false";
        String output = "json";
        String parameters = new StringBuilder()
                .append(reference)
                .append("&")
                .append(sensor)
                .append("&")
                .append("key=")
                .append(Constants.API_BROWSER_KEY).toString();

        // Building the url to the web service
        return new StringBuilder()
                .append("https://maps.googleapis.com/maps/api/place/details/")
                .append(output).append("?")
                .append(parameters).toString();
    }

    private String getPlacesUrl(String qry){
        try {
            qry = "input=" + URLEncoder.encode(qry, "utf-8");
        } catch (UnsupportedEncodingException e1) {
            e1.printStackTrace();
        }

        String sensor = "sensor=false";
        String types = "types=geocode";
        String output = "json";
        String parameters = new StringBuilder()
                .append(qry)
                .append("&")
                .append(types)
                .append("&")
                .append(sensor)
                .append("&")
                .append("key=")
                .append(Constants.API_BROWSER_KEY).toString();

        return new StringBuilder()
                .append("https://maps.googleapis.com/maps/api/place/autocomplete/")
                .append(output).append("?")
                .append(parameters).toString();
    }

    private String getPlaces(String[] params) {
        // For storing data from web service
        String data = "";
        String url = getPlacesUrl(params[0]);
        // Fetching the data from web service in background
        try {
            data = downloadUrl(url);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return data;
    }

    private String getPlaceDetails(String reference){
        String data = "";
        String url = getPlaceDetailsUrl(reference);
        try {
            data = downloadUrl(url);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return data;
    }

    private static UriMatcher buildUriMatcher() {
        UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        // URI for search button
        uriMatcher.addURI(AUTHORITY, "search", SEARCH);

        // URI for suggestions in Search Dialog
        uriMatcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, SUGGESTIONS);

        // URI for Details
        uriMatcher.addURI(AUTHORITY, "details", DETAILS);

        return uriMatcher;
    }
}
