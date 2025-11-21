# Groove Coaster 2 : Original Stile offline mode

Use my mod to play after the shutdown on Android! Play all the 4max expansion songs!

## Includes :

✅ All songs unlocked from the start (Including arcade only songs, extra difficulty songs!)

✅ All difficulties unlocked (no need to play normal to unlock hard)

✅ 99999 of each item

✅ Can play all songs offline

✅ Fixed delayed ad-lib sound

✅ Don't need root!

✅ Can chose to play on a custom server!

## How to use

- Have ~10gb of storage (to download the song data) on an Android device
- Download the xapk using the link on [this post](https://furries.club/@helloyanis/115554340112777601) ([Extra link](https://www.mediafire.com/file/lslhye23fafbxej/groove2setupv3x.xapk/file))
- Sometimes, the file will download as an `.apk.zip` file. If that's the case, rename the file to end with `.xapk` only.
- Install the xapk using the zarchiver app but **DON'T** open it yet. After the install is finished, wait for 2 minutes because a file transfer is still in progress (but not shown on the zarchiver UI). You can check if the transfer is done by going to `Internal storage/Android/obb/jp.co.taito.groovecoasterzero` and refreshing the page by sliding down every few seconds : If the size of files are increasing, then the file transfer is not yet done.
<details>
  <summary><b>If the zarchiver install failed, or you get a "Failed to extract OBB" error, click here</b></summary>
  <ul>
    <li>Rename the <code>.xapk</code> extension of the file into <code>.zip</code></li>
    <li>Extract the zip file in its own folder. There should be an <code>.apk</code> file and an <code>Android</code> folder.</li>
  </ul>
      <details>
        <summary><b>If you are on Android 13 or above, click here</b></summary>
    <ul>
      <li>Plug in your phone to a PC so you can see the internal storage of your phone on your PC's file browser</li>
            <li>From your PC, copy and paste the Android folder from the xapk you extracted earlier to the root of your internal storage. When asked to merge folders or replace files, click Yes.</li>
      <li>Follow the next steps based on your Android version :</li>
          </ul>
    </details>
       <details>
        <summary><b>If you are on Android 12 or below, click here</b></summary>
          <ul>
            <li>Copy and paste the Android folder to the root of your internal storage. When asked to merge folders or replace files, click Yes.</li>
          </ul>
    </details>
</details>

- Delete the .xapk file and any archive you might have extracted earlier, to free up space
- Start the new `GROOVE2 Setup` app
- Wait for the load to finish (It should take a minute or two)
- Click the button that sets up the app as you want :
  - If you only want to play songs with complete offline support, and don't care about missions, ranking, and social features, click on `Set up for offline play`
  - If you want to use a third party server (or your own) that has support for missions, cloud saves, ranking, etc, click on `Set up with a third party server`. Then enter the URL of a third party server. As of the time I'm writing this, one public server is `https://gc2.aitsuki.org/`
 - Click on the 1st button to save the APK file for the game
 - Open the file in your phone's file browser, install the game and enjoy!

> Note : This source code is for the apk only. All the data is stored in .obb files that are too big to put on GitHub. The xapk includes :
> - The game's OBB file. It gets edited depending on which server you set up the app with
> - The data OBB files. It should contain the `resource` and `savedata` folders at the root with the appropriate files in them to play offline. To get these files, download the .xapk from the link above, rename it as .zip, extract it and check the Android folder