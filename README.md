# PSNProfilesMobile

![PlayStation Trophies](https://img.asmedia.epimg.net/resizer/v2/7VLOQNSS5BG4DBUCK6C3PZJCOI.jpg?auth=0f9eeb3703476fa38393810b1d27f43b6df8c23bf6a1147fb525ec4c7a591cd2&width=1472&height=828&smart=true&height=20%)

## Overview
PSNProfilesMobile is an Android application designed to provide mobile friendly ui for https://psnprofiles.com/.

It utilizes a `WebView` to load the website and injects custom CSS to hide advertisements, force the screen width to fit mobile devices, and restructure the layout for easier reading.

## Features
* **Seamless Web Viewing:** Wraps the PSNProfiles page in a native Android application.

* **Mobile Optimization:** Restructures the layout from rows to columns and forces elements 
to occupy 100% of the screen width for a native mobile feel. (Depends on the guide, tables are still bugged/Not properly managed)

* **Cookie Management:** Automatically handles first-party and third-party cookies for seamless session management.

### The Main Activity

The `MainActivity.java` is the entry point of the application. It handles the setup of the `WebView`, configuration of web settings, and the injection of custom CSS once the page finishes loading.

## Requirements
* Android SDK 24 or higher (Minimum supported version).

* Target SDK 36.

* Internet Permission is required and declared in the AndroidManifest.xml.

## Build Instructions

    1. git clone https://github.com/HectorPozo2007/PSNProfilesMobile.git
    
    2. Open the project in Android Studio.

    3. Sync the project with Gradle files.

    4. Build and run the project on an emulator or a physical Android device.

