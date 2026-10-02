# Eren + Eren Admin

- `Eren/`       -> update dialog app (Eren.java + MainActivity.java), package com.eren.dialog
- `ErenAdmin/`  -> admin panel app, package com.eren.admin

Push to GitHub -> Actions tab -> "Build Eren + Eren Admin" -> download the APKs from Artifacts.

Firebase rules (Realtime Database):

    { "rules": { "eren": { ".read": true, ".write": true } } }

After building Eren, open the APK in MT Manager -> Eren.smali and replace:
- DB_URL  (your databaseURL)
- APP_KEY (App Connect Key from the admin app)
