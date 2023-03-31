package com.example.musicplayer_spotify_api

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.musicplayer_spotify_api.databinding.ActivityMainBinding
import com.google.gson.GsonBuilder
import com.spotify.android.appremote.api.*
import com.spotify.android.appremote.api.error.SpotifyDisconnectedException
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.*
import com.spotify.sdk.android.auth.AccountsQueryParameters.CLIENT_ID
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import com.spotify.sdk.android.auth.LoginActivity.REQUEST_CODE
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "App-Remote Sample"
        const val STEP_MS = 15000L
    }

//    private val clientId = "------------------your client id here--------------------"
    private val redirectUri = "https://com.spotify.android.spotifysdkkotlindemo/callback"
    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var userApi: UserApi? = null
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val errorCallback = { throwable: Throwable -> logError(throwable) }

    private val playerStateEventCallback = Subscription.EventCallback<PlayerState> { playerState ->
        Log.v(TAG, String.format("Player State: %s", gson.toJson(playerState)))

//        updateShuffleButton(playerState)

//        updateRepeatButton(playerState)

//        updateTrackStateButton(playerState)

//        updatePlayPauseButton(playerState)

//        updatePlaybackSpeed(playerState)

//        updateTrackCoverArt(playerState)

//        updateSeekbar(playerState)
    }

    lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onStart() {
        super.onStart()
        // We will start writing our code here.
        // Set the connection parameters
        val connectionParams =
            ConnectionParams.Builder(clientId).setRedirectUri(redirectUri).showAuthView(true)
                .build()

        SpotifyAppRemote.connect(this, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                Log.d("MainActivity", "Connected! Yay!")
                // Now you can start interacting with App Remote
                connected()
            }

            override fun onFailure(throwable: Throwable) {
                Log.e("MainActivity", throwable.message, throwable)
                // Something went wrong when attempting to connect! Handle errors here
            }
        })

        // Request code will be used to verify if result comes from the login activity. Can be set to any integer.
        // Request code will be used to verify if result comes from the login activity. Can be set to any integer.
        val REQUEST_CODE = 1337
        val REDIRECT_URI = "yourcustomprotocol://callback"

        val builder =
            AuthorizationRequest.Builder(CLIENT_ID, AuthorizationResponse.Type.TOKEN, REDIRECT_URI)

        builder.setScopes(arrayOf("streaming"))
        val request = builder.build()

        AuthorizationClient.openLoginActivity(this, REQUEST_CODE, request)
    }

    private fun connected() {
        // Then we will write some more code here.
        // Play a playlist

        binding.button.setOnClickListener {
//          spotifyAppRemote?.playerApi?.play("spotify:playlist:37i9dQZF1DX2sUQwD7tbmL")
            spotifyAppRemote?.playerApi?.play("spotify:track:3bH4HzoZZFq8UpZmI2AMgV")
        }
        binding.button2.setOnClickListener {
            spotifyAppRemote?.playerApi?.pause()
        }


        // Subscribe to PlayerState
        spotifyAppRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback {
            val track: Track = it.track
            binding.textView.text = track.name
            spotifyAppRemote!!.imagesApi.getImage(track.imageUri, Image.Dimension.MEDIUM)
                .setResultCallback { bitmap ->
                    binding.image.setImageBitmap(bitmap)
                }

            Log.d("Check link", "link uri" + track.uri)
            Log.d("MainActivity", track.name + " by " + track.artist.name)

        }
        binding.button3.setOnClickListener {
            assertAppRemoteConnected().let {
                val scope = MainScope()
                scope.launch {
                    val combined = ArrayList<ListItem>(1)
                    val listItems = loadRootRecommendations(it)
                    listItems?.apply {
                        for (i in items.indices) {
                            if (items[i].playable) {
                                combined.add(items[i])
                            } else {
                                val children: ListItems? = loadChildren(it, items[i])
                                val convertolist = children?.items?.toList() as List<ListItem>
                                combined.addAll(convertolist)
                            }
                        }
                    }
                    AlertDialog.Builder(this@MainActivity).setTitle(title)
                        .setMessage(gson.toJson(combined)).create().show()
                    

                }
            }
        }

    }

    override fun onStop() {
        super.onStop()
        // Aaand we will finish off here.
        spotifyAppRemote?.let {
            SpotifyAppRemote.disconnect(it)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)

        // Check if result comes from the correct activity
        if (requestCode == REQUEST_CODE) {
            val response = AuthorizationClient.getResponse(resultCode, intent)
            when (response.type) {
                AuthorizationResponse.Type.TOKEN -> {}
                AuthorizationResponse.Type.ERROR -> {}
                else -> {}
            }
        }
    }

    private fun assertAppRemoteConnected(): SpotifyAppRemote {
        spotifyAppRemote?.let {
            if (it.isConnected) {
                return it
            }
        }
        Log.e(TAG, getString(R.string.err_spotify_disconnected))
        throw SpotifyDisconnectedException()
    }


    private suspend fun loadRootRecommendations(appRemote: SpotifyAppRemote): ListItems? =
        suspendCoroutine { cont ->
            appRemote.contentApi
                .getRecommendedContentItems(ContentApi.ContentType.FITNESS)
                .setResultCallback { listItems -> cont.resume(listItems) }
                .setErrorCallback { throwable ->
                    errorCallback.invoke(throwable)
                    cont.resumeWithException(throwable)
                }
        }
    private fun logError(throwable: Throwable) {
        Toast.makeText(this, "An error has occurred. Check the logs to know more.", Toast.LENGTH_SHORT).show()
        Log.e(TAG, "", throwable)
    }
    private suspend fun loadChildren(appRemote: SpotifyAppRemote, parent: ListItem): ListItems? =
        suspendCoroutine { cont ->
            appRemote.contentApi
                .getChildrenOfItem(parent, 6, 0)
                .setResultCallback { listItems -> cont.resume(listItems) }
                .setErrorCallback { throwable ->
                    errorCallback.invoke(throwable)
                    cont.resumeWithException(throwable)
                }
        }
    /*private fun updateTrackCoverArt(playerState: PlayerState) {
        // Get image from track
        assertAppRemoteConnected()
            .imagesApi
            .getImage(playerState.track.imageUri, Image.Dimension.LARGE)
            .setResultCallback { bitmap ->
                binding.image.setImageBitmap(bitmap)
//                 binding.imageLabel.text = String.format(
//                     Locale.ENGLISH, "%d x %d", bitmap.width, bitmap.height)
            }
    }*/

}