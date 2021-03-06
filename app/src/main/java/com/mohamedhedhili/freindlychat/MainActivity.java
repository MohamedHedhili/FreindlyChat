package com.mohamedhedhili.freindlychat;

import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final String NEWUSER = "newuser";
    private static final int RC_PHOTO_PICKER =  2;
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    public static final String FRIENDLY_MSG_LENGTH_KEY = "friendly_msg_length";

    public static final int RC_SIGN_IN = 1;
    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;

    private String mUsername;

    private FirebaseDatabase firebaseDatabase ;
   private DatabaseReference mMessagesDatabaseReference  ;
    private ChildEventListener childEventListener ;
    private FirebaseStorage  mFirebaseStorage ;
    private StorageReference chatPhotoReference  ;
    private FirebaseRemoteConfig mFirebaseRemoteConfig ;

    //  Authen
    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mFirebaseStorage = FirebaseStorage.getInstance();
        chatPhotoReference = mFirebaseStorage.getReference().child("chat_photos");
        mUsername = NEWUSER;
        firebaseDatabase =FirebaseDatabase.getInstance();
        mFirebaseAuth = FirebaseAuth.getInstance();

        mMessagesDatabaseReference = firebaseDatabase.getReference().child("messages");
                // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);

        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);
            }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Send messages on click
                FriendlyMessage friendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString(), mUsername, null);
                mMessagesDatabaseReference.push().setValue(friendlyMessage);
                // Clear input box
                mMessageEditText.setText("");
            }
        });

        mAuthStateListener = new FirebaseAuth.AuthStateListener(){
            @Override
            public void onAuthStateChanged(FirebaseAuth firebaseAuth) {
                FirebaseUser user =firebaseAuth.getCurrentUser();
                if (user != null )
                {
                Toast.makeText(MainActivity.this, "You're now signed in. Welcome to FriendlyChat.", Toast.LENGTH_SHORT).show();
                    OnSingnedInIniliaze(user.getDisplayName()) ;
                }
                else
                {
                   OnSingnedOutCleanup();
                    // User is signed out
                startActivityForResult(
                 AuthUI.getInstance()
                         .createSignInIntentBuilder()
                         .setIsSmartLockEnabled(false)
                         .setProviders(
                 AuthUI.EMAIL_PROVIDER,
                 AuthUI.GOOGLE_PROVIDER)
                 .build(), RC_SIGN_IN);
                }

            }
        };
// Remote Config

            FirebaseRemoteConfigSettings  configSettings =  new FirebaseRemoteConfigSettings.Builder()
                    .setDeveloperModeEnabled(BuildConfig.DEBUG)
                    .build();
        mFirebaseRemoteConfig.setConfigSettings(configSettings);
        Map<String, Object> defaultConfigMap = new HashMap<>();
             defaultConfigMap.put(FRIENDLY_MSG_LENGTH_KEY, DEFAULT_MSG_LENGTH_LIMIT);
              mFirebaseRemoteConfig.setDefaults(defaultConfigMap);
        fetchConfig();


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sign_out_menu :
                AuthUI.getInstance().signOut(this);
                return true ;
            default :
            return super.onOptionsItemSelected(item);

        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mAuthStateListener !=null)
        mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        DetachDatabaseReadlistner();
        mMessageAdapter.clear();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);

    }
    public  void OnSingnedInIniliaze (String  user )
    {
   mUsername=  user  ;
        AttachDatabaseReadListner();


    }
    public  void OnSingnedOutCleanup () {
        mUsername =NEWUSER ;
        mMessageAdapter.clear();
        DetachDatabaseReadlistner();


    }

    public  void AttachDatabaseReadListner ()
    {    if (childEventListener == null ) {
        childEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);
                mMessageAdapter.add(friendlyMessage);
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {

            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        };
        mMessagesDatabaseReference.addChildEventListener(childEventListener);
    }
    }
    public  void DetachDatabaseReadlistner()
    {  if (childEventListener!=null)
        mMessagesDatabaseReference.removeEventListener(childEventListener);
        childEventListener=null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode==RC_SIGN_IN)
        {
            if (resultCode==RESULT_OK)
            {
                Toast.makeText(getApplicationContext(),"Signed in ",Toast.LENGTH_SHORT).show();
            }
            else if (resultCode==RESULT_CANCELED)
            {
                Toast.makeText(getApplicationContext(),"Signed in canceled ",Toast.LENGTH_SHORT).show();

            }
        }
        else  if (requestCode==RC_PHOTO_PICKER && resultCode ==RESULT_OK)

        {
            Uri selectedImage =  data.getData() ;
            // Get  a reference to  store file at chat_photos
            StorageReference photoRef  = chatPhotoReference.child(selectedImage.getLastPathSegment()) ;
            // upload file to firebase
            photoRef.putFile(selectedImage).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    // taskSnapshot.getMetadata() contains file metadata such as size, content-type, and download URL.
                    Uri downloadUrl = taskSnapshot.getDownloadUrl();
                    FriendlyMessage freindlyMessage =  new FriendlyMessage(null , mUsername,downloadUrl.toString());
                    mMessagesDatabaseReference.push().setValue(freindlyMessage);
                }
            });
        }

    }
    public void fetchConfig() {
           long cacheExpiration = 3600; // 1 hour in seconds
           // If developer mode is enabled reduce cacheExpiration to 0 so that each fetch goes to the
                 // server. This should not be used in release builds.
                          if (mFirebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()) {
                cacheExpiration = 0;
              }
        mFirebaseRemoteConfig.fetch(cacheExpiration)
                       .addOnSuccessListener(new OnSuccessListener<Void>() {
                           @Override
                          public void onSuccess(Void aVoid) {
                            // Make the fetched config available
                                          // via FirebaseRemoteConfig get<type> calls, e.g., getLong, getString.
                                          mFirebaseRemoteConfig.activateFetched();

                                           // Update the EditText length limit with
                                                  // the newly retrieved values from Remote Config.
                                 applyRetrievedLengthLimit();
                               }
        })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // An error occurred when fetching the config.
                        Log.w(TAG, "Error fetching config", e);

                        // Update the EditText length limit with
                        // the newly retrieved values from Remote Config.
                        applyRetrievedLengthLimit();
                                 }
                           });
          }

               /**
        * Apply retrieved length limit to edit text field. This result may be fresh from the server or it may be from
         * cached values.*/
              private void applyRetrievedLengthLimit() {
               Long friendly_msg_length = mFirebaseRemoteConfig.getLong(FRIENDLY_MSG_LENGTH_KEY);
               mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(friendly_msg_length.intValue())});
                Log.d(TAG, FRIENDLY_MSG_LENGTH_KEY + " = " + friendly_msg_length);
            }
}

