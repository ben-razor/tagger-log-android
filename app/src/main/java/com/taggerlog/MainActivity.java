package com.taggerlog;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Debug;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.firebase.ui.auth.data.model.User;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;
import com.google.gson.Gson;

/*
import com.google.auth.oauth2.GoogleCredentials;

 */

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity {

    private Gson gson;
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private FirebaseFirestore db;
    private FirebaseUser user;
    private WebView webView;
    private String TAG = "MainActivity";
    private static final int RC_SIGN_IN = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        WebView.setWebContentsDebuggingEnabled(true);
        webView = (WebView) findViewById(R.id.webview);

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new TaggerLogInterface(this), "AndroidInterface");
        webSettings.setDomStorageEnabled(true);
        webView.loadUrl("file:///android_asset/web/index.html");

        initAuthListener();
    }

    protected void initAuthListener() {
        mAuth = FirebaseAuth.getInstance();
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                user = mAuth.getCurrentUser();
                setUser(user);
            }
        };
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        user = mAuth.getCurrentUser();
    }

    /**
     * Javascript interface for use by the WebView
    */
    public class TaggerLogInterface {
        Context mContext;

        /** Instantiate the interface and set the context */
        TaggerLogInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void init() {
            webView.post(new Runnable() {
                @Override
                public void run() {
                    setUser(user);
                    webView.evaluateJavascript("taggerlog.init();", null);
                }
            });
        }

        @JavascriptInterface
        public void deleteEntry(String id) {
            CollectionReference diaryEntryRef = db.collection("diary-entry");
            DocumentReference docRef = diaryEntryRef.document(id);

            docRef.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                    @Override
                    public void onSuccess(DocumentSnapshot documentSnapshot) {
                        Map<String, Object> data = documentSnapshot.getData();
                        ArrayList<String> tagList = (ArrayList<String>)data.get("tag-list");
                        Map<String, Object> dataUpdate = new HashMap<String, Object>();
                        dataUpdate.put("deleted", true);
                        dataUpdate.put("date-modified", FieldValue.serverTimestamp());

                        docRef.update(dataUpdate).addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                deleteOrphanTags(tagList);
                                Log.d("DELETE", "deletedRecord");
                            }
                        });
                    }
                }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.d("deleteEntry", e.toString());
                }
            });
        }

        @JavascriptInterface
        public String addEntry(String entryJSON) {
            Entry e = entryFromJSON(entryJSON);
            Map<String, Object> dataUpdate = new HashMap<String, Object>();
            dataUpdate.put("entry", e.entry);
            dataUpdate.put("date", new Timestamp(e.date));
            dataUpdate.put("date-modified", FieldValue.serverTimestamp());
            dataUpdate.put("tag-list", e.tagList);
            dataUpdate.put("uid", user.getUid());
            dataUpdate.put("deleted", false);
            CollectionReference diaryEntryRef = db.collection("diary-entry");
            CollectionReference tagsColRef = db.collection("diary-tags");
            DocumentReference newEntryRef = diaryEntryRef.document();
            DocumentReference tagsDocRef = tagsColRef.document(user.getUid());

            runJS("taggerlog.json(taggerlog.allTags)" , new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String s) {
                    s = cleanReceivedJSON(s);
                    List<String> allTags = gson.fromJson(s, new TypeToken<List<String>>(){}.getType());
                    String allTagsCSV = TextUtils.join(",", allTags);

                    WriteBatch batch = db.batch();
                    batch.set(newEntryRef, dataUpdate);
                    Map<String, Object> tagsDoc = new HashMap<>();
                    tagsDoc.put("tags", allTagsCSV);
                    batch.set(tagsDocRef, tagsDoc);
                    batch.commit().addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e("addEntry", e.toString());
                        }
                    });
                }
            });

            return newEntryRef.getId();
        }

        @JavascriptInterface
        public void editEntry(String id, String currentEntryJSON, String entryJSON) {
            CollectionReference diaryColRef = db.collection("diary-entry");

            Entry currentEntry = gson.fromJson(currentEntryJSON, Entry.class);
            Entry entry = gson.fromJson(entryJSON, Entry.class);

            ArrayList<String> tagsRemoved = new ArrayList<>(currentEntry.tagList);
            tagsRemoved.removeAll(entry.tagList);

            Map<String, Object> entryFirebase = new HashMap<>();
            entryFirebase.put("date-modified", FieldValue.serverTimestamp());
            entryFirebase.put("date", entry.date);
            entryFirebase.put("entry", entry.entry);
            entryFirebase.put("tag-list", entry.tagList);
            diaryColRef.document(id).update(entryFirebase).addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void aVoid) {
                    deleteOrphanTags(tagsRemoved);
                    runJS("taggerlog.updateQueryRelatedTags(); taggerlog.refreshEntryDisplay();", null);
                }
            });
        }

        @JavascriptInterface
        public void saveTags() {
            CollectionReference diaryTagsColRef = db.collection("diary-tags");

            runJS("taggerlog.json(taggerlog.allTags)", new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String s) {
                    s = cleanReceivedJSON(s);
                    List<String> allTags = gson.fromJson(s, new TypeToken<List<String>>() {
                    }.getType());
                    String allTagsCSV = TextUtils.join(",", allTags);
                    Map<String, String> tagsFirestore = new HashMap<>();
                    tagsFirestore.put("tags", allTagsCSV);

                    diaryTagsColRef.document(user.getUid()).set(tagsFirestore)
                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                runJS("taggerlog.saveTagsRefresh()", null);
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.e("saveTags", e.toString());
                            }
                        });
                }
            });
        }

        @JavascriptInterface
        public void deleteOrphanTags(ArrayList<String> tags) {
            CollectionReference query = db.collection("diary-entry");
            ArrayList<String> orphans = new ArrayList<>(tags);
            Set<String> storedTagSet = new HashSet<>();

            if(tags.size() > 0) {
                query.whereEqualTo("uid", user.getUid())
                        .whereArrayContainsAny("tag-list", tags);
                query.get(Source.CACHE).addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot doc : task.getResult()) {
                                Map<String, Object> data = doc.getData();

                                if(data.get("deleted") != null && !(boolean)data.get("deleted")) {
                                    ArrayList<String> tagList = (ArrayList<String>)data.get("tag-list");

                                    for(String tag: tagList) {
                                        storedTagSet.add(tag);
                                    }
                                }
                            }

                            orphans.removeAll(storedTagSet);
                            String orphansCSV = TextUtils.join(",", orphans);
                            runJS(String.format("taggerlog.removeTags('%s');", orphansCSV), new ValueCallback<String>() {
                                @Override
                                public void onReceiveValue(String s) {
                                    saveTags();
                                }
                            });
                        }
                        else {
                            Log.e("findOrphanTags", task.getException().toString());
                        }
                    }
                });
            }
        }

        @JavascriptInterface
        public void logIn() {
            // Choose authentication providers
            List<AuthUI.IdpConfig> providers = Arrays.asList(
                    new AuthUI.IdpConfig.GoogleBuilder().build());

            // Create and launch sign-in intent
            startActivityForResult(
                    AuthUI.getInstance()
                            .createSignInIntentBuilder()
                            .setAvailableProviders(providers)
                            .build(),
                    RC_SIGN_IN);
        }

        @JavascriptInterface
        public void logOut() {
            AuthUI.getInstance()
                    .signOut(mContext)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            user = null;
                            webView.evaluateJavascript("taggerlog.setUser(null);", null);
                            init();
                        }
                    });
        }

        public void runJS(String js, ValueCallback<String> valueCallback) {
            webView.post(new Runnable() {
                @Override
                public void run() {
                    webView.evaluateJavascript(js, valueCallback);
                }
            });
        }

        /**
         * WebView.evaluateJavascript returns an escaped string wrapped in double quotes.
         *
         * This function turns this into a string that will be valid when passed to a JSON
         * parser.
         *
         * @param s The input string
         * @return The cleaned String
         */
        public String cleanReceivedJSON(String s) {
            return s.substring(1, s.length() - 1)
                    .replace("\\\\", "\\")
                    .replace("\\\"", "\"");
        }

        public void runGetEntriesQuery(Query q) {
            q.get(Source.CACHE).addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                @Override
                public void onComplete(@NonNull Task<QuerySnapshot> task) {
                    if(task.isSuccessful()) {
                        Date mostRecentModify = new Date(0L);

                        int numCachedEntries = task.getResult().size();

                        for(QueryDocumentSnapshot doc : task.getResult()) {
                            Map<String, Object> data = doc.getData();
                            Date dateModified = ((Timestamp)data.get("date-modified")).toDate();
                            String json = entryToJSON(doc.getId(), data);

                            webView.evaluateJavascript(String.format("taggerlog.insertEntry('%s', true)", json), null);

                            if(dateModified.getTime() > mostRecentModify.getTime()) {
                                mostRecentModify = dateModified;
                            }
                        }
                        webView.evaluateJavascript(String.format("taggerlog.updateQueryRelatedTags();"), null);
                        webView.evaluateJavascript(String.format("taggerlog.refreshUI();"), null);

                        Query q = db.collection("diary-entry")
                                .orderBy("date-modified", Query.Direction.DESCENDING)
                                .whereEqualTo("uid", user.getUid());

                        if(numCachedEntries > 0) {
                            Timestamp ts = new Timestamp(mostRecentModify);
                            q = q.whereGreaterThan("date-modified", ts);
                        }

                        q.get(Source.SERVER).addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                            @Override
                            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                if (task.isSuccessful()) {
                                    if(task.getResult().size() > 0) {
                                        for(QueryDocumentSnapshot doc : task.getResult()) {
                                            Map<String, Object> data = doc.getData();
                                            String json = entryToJSON(doc.getId(), data);

                                            webView.evaluateJavascript(String.format("taggerlog.insertEntry('%s', true)", json), null);
                                        }

                                        webView.evaluateJavascript(String.format("taggerlog.updateQueryRelatedTags();"), null);
                                        webView.evaluateJavascript(String.format("taggerlog.refreshUI();"), null);
                                    }
                                    webView.evaluateJavascript(String.format("taggerlog.refreshUI();"), null);
                                }
                            }
                        });
                    }
                    else {
                        Log.w(TAG, "Error getting documents.", task.getException());
                    }
                }
            });
        }

        @JavascriptInterface
        public void getEntries() {
            webView.post(new Runnable() {
                @Override
                public void run() {

                    webView.evaluateJavascript("taggerlog.json(taggerlog.queryTags)", new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String s) {
                             Query q = db.collection("diary-entry")
                                     .orderBy("date", Query.Direction.DESCENDING)
                                     .whereEqualTo("uid", user.getUid())
                                     .whereEqualTo("deleted", false);

                             s = cleanReceivedJSON(s);
                             List<String> queryTags = gson.fromJson(s, new TypeToken<List<String>>(){}.getType());

                             if(queryTags.size() > 0) {
                                 q = q.whereArrayContainsAny("tag-list", queryTags);
                             }
                             else {
                                 q = q.limit(10);
                             }

                             runGetEntriesQuery(q);
                         }
                     });
                 }
             });
        }

        @JavascriptInterface
        public void getTags() {
           db.collection("diary-tags").document(user.getUid())
                   .get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
               @Override
               public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                   if (task.isSuccessful()) {
                       DocumentSnapshot doc = task.getResult();
                       Map<String, Object> data = doc.getData();
                       String tags = (String)data.get("tags");
                       webView.evaluateJavascript(String.format("taggerlog.setAllTags('%s');", tags), null);
                   }

                   db.collection("diary-tag-combos").document(user.getUid())
                           .get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                       @Override
                       public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                           if (task.isSuccessful()) {
                               DocumentSnapshot doc = task.getResult();
                               Map<String, Object> data = doc.getData();
                               String dataJSON = gson.toJson(data.get("tag-combos"));

                                webView.evaluateJavascript(
                                        String.format("taggerlog.setTagCombos('%s', true);", dataJSON),
                                null
                                );
                           }
                       }
                   });
               }
           });
        }

        class TagCombo {
            @SerializedName("title")
            public String title;
            @SerializedName("tags")
            public String tags;
        }

        /**
         * Save the array of favourite tag combinations to the db.
         */
        @JavascriptInterface
        public void saveTagCombos() {
            runJS("taggerlog.json(taggerlog.tagCombos)", new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String s) {
                    s = cleanReceivedJSON(s);
                    Log.d("saveTagCombos", s);
                    List<TagCombo> tagCombos = gson.fromJson(s, new TypeToken<List<TagCombo>>(){}.getType());
                    ArrayList<Map<String, Object>> tagCombosFirestore = new ArrayList<Map<String, Object>>();
                    for(TagCombo t: tagCombos) {
                        HashMap<String, Object> tagCombo = new HashMap<>();
                        tagCombo.put("title", t.title);
                        tagCombo.put("tags", t.tags);
                        tagCombosFirestore.add(tagCombo);
                    }
                    Map<String, Object> firestoreDoc = new HashMap<>();
                    firestoreDoc.put("tag-combos", tagCombosFirestore);

                    CollectionReference colRef = db.collection("diary-tag-combos");
                    DocumentReference docRef = colRef.document(user.getUid());
                    docRef.set(firestoreDoc).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e("saveTagCombos", e.toString());
                        }
                    });

                }
            });
        }

        public String entryToJSON(String id, Map<String, Object> data) {
            Timestamp dateTS = (Timestamp)data.get("date");
            Timestamp dateModifiedTS = (Timestamp)data.get("date-modified");
            Date dateModified = dateModifiedTS.toDate();
            data.put("date", dateToISO(dateTS.toDate()));
            data.put("date-modified", dateToISO(dateModifiedTS.toDate()));
            data.put("entry", data.get("entry").toString().replaceAll("\\n", "\\\\n"));
            data.put("id", id);

            String json = gson.toJson(data);
            return json;
        }

        class Entry {
            @SerializedName("uid")
            public String uid;
            @SerializedName("entry")
            public String entry;
            @SerializedName("date")
            public Date date;
            @SerializedName("date-modified")
            public Date dateModified;
            @SerializedName("tag-list")
            public ArrayList<String> tagList;
        }

        public Entry entryFromJSON(String entryJSON) {
            Entry entry = gson.fromJson(entryJSON, Entry.class);
            return entry;
        }

        /**
         * Convert date to ISO 8601 format.
         *
         * @param d A Date object
         * @return A date string
         */
        public String dateToISO(Date d) {
            SimpleDateFormat sdf;
            sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            String text = sdf.format(d);
            return text;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            IdpResponse response = IdpResponse.fromResultIntent(data);

            if (resultCode == RESULT_OK) {
                user = FirebaseAuth.getInstance().getCurrentUser();
                setUser(user);
            } else {
                // Sign in failed. If response is null the user canceled the
                // sign-in flow using the back button. Otherwise check
                // response.getError().getErrorCode() and handle the error.
                // ...
            }
        }
    }

    protected void setUser(FirebaseUser user) {
        if(user != null) {
            gson = new Gson();
            Map<String, String> userData = new HashMap<String, String>();
            userData.put("uid", user.getUid());
            userData.put("displayName", user.getDisplayName());
            userData.put("email", user.getEmail());
            userData.put("photoURL", user.getPhotoUrl().toString());
            String json = gson.toJson(userData);
            webView.evaluateJavascript(String.format("taggerlog.setUser(JSON.parse('%s'));", json), null);
        }
    }
}