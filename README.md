# GPS Tracking and Area calculation APP
![Screenshot_2026-02-28-13-55-59-45_fe67bfd226f2707](https://github.com/user-attachments/assets/066a4d62-5068-4a56-856c-38a041dbb58c)

This Android application is designed to **track the user's location** using **GPS** and **step count** via a built-in step counter sensor. It visualizes the trajectory on a map, provides real-time step count updates, calculates the area and perimeter of the traveled path, and stores the user's tracking data for future reference.

## Features

- **Real-Time GPS Tracking**: 
  - Tracks the user’s location and displays it on the map in real-time.
  
- **Step Counter**: 
  - Uses the device's built-in **step counter sensor** to track the number of steps taken.
  
- **Map View Customization**: 
  - Supports both **satellite** and **normal** map views.
  - Ability to **zoom** to the user's current location on the map.
  
- **Path Drawing**: 
  - Visualizes the traveled path on the map as the user moves.
  
- **Area and Perimeter Calculation**: 
  - Calculates the **area** and **perimeter** of the tracked path (if the path forms a polygon).
  
- **History Storage**: 
  - Saves the tracking data (area, perimeter, step count, etc.) to a **local file** (`history.json`) for later access.
  
- **Permissions Handling**: 
  - Requests and handles the necessary **location**, **sensor**, and **notification** permissions from the user.

- **Background Notifications**: 
  - Sends **background notifications** while the service is running, ensuring the user is always informed about the tracking status.

- **User Interface**:
  - A user-friendly interface with buttons to:
    - Start/Stop tracking
    - Switch between map layers (satellite/normal)
    - View statistics and replay the path
  
## Required Permissions

- **Location Permissions**:
  - `ACCESS_FINE_LOCATION`
  - `ACCESS_COARSE_LOCATION`

- **Sensor Permissions**:
  - `ACTIVITY_RECOGNITION`

- **Notification Permissions**:
  - `POST_NOTIFICATIONS`

## Setup & Requirements

1. **Map API Key**: 
   - You need to provide an **Amap API key** (Gaode Map) for location services.
   - The app retrieves your location using Amap’s SDK.

2. **Device Compatibility**:
   - The app requires a device with a **built-in step counter sensor** (Sensor.TYPE_STEP_COUNTER). If the device doesn’t support this sensor, an error message will be displayed.

## How to Use

1. **Start Tracking**:
   - Tap the **"Start"** button to begin tracking your location and counting steps.
   - The app will prompt for permissions if they haven't been granted yet.
   - **GPS location tracking** and **step counting** will start simultaneously.

2. **Stop Tracking**:
   - Tap the **"Stop"** button to end tracking, and calculate the area and perimeter of the path.
   - The results will be displayed on the screen.

3. **Zoom to Current Location**:
   - Tap the **"Zoom"** button to zoom in on the user's current location on the map.

4. **Switch Map Layers**:
   - Tap the **"Layer"** button to toggle between **satellite** and **normal** map views.

5. **Change Theme**:
   - Tap the **"Theme"** button to switch between **day** and **night modes** for map and UI aesthetics.

6. **View Statistics**:
   - Tap the **"Stats"** button to see detailed statistics of tracked paths such as **total area**, **total perimeter**, and **total steps**.

7. **Replay Path**:
   - Tap the **"Replay"** button to visualize the path that was recorded on the map.

## Notifications

- The app **requests notification permissions** to show updates about the background tracking process.
- Background notifications keep users informed about the ongoing tracking even when the app is not in the foreground.

## Saving & Viewing History

- The app saves each tracking session’s data, including area, perimeter, steps, and path coordinates, to a **local file** (`history.json`).
- Historical data can be accessed through the **Statistics** page or exported for further use.

## Code Structure

- **MainActivity**: Handles UI interactions, permissions requests, sensor setup, and tracking logic.
- **TrackingService**: A background service that handles continuous **location tracking** and **step counting**.
- **StatsActivity**: Displays statistics for previous tracking sessions, including **area**, **perimeter**, and **steps**.

## Development Setup

1. **Clone or download** the project.
2. **Add your Amap API key** in `MainActivity.java` or configure it through the app's settings.
3. **Build** the app in **Android Studio** and run it on a compatible Android device.

## Troubleshooting

- **Location Services**: If the location services are not enabled, the app will prompt the user to enable them from **Settings**.
- **Step Counter Sensor**: If the device does not support the step counter sensor, a warning will be shown, and the step counter feature will not be available.

## Known Issues

- **Battery Optimizations**: Some devices may restrict the app’s **background activity** due to aggressive battery optimization settings. You may need to manually disable battery optimizations for the app to function smoothly.

## License

This project is open-source and available for modification under the **MIT License**.

---

Feel free to report any issues or contribute to the project via the repository. We appreciate any feedback or contributions!

Happy tracking! 🚶‍♂️📍
