# Server Hosting & Configuration Guide

This guide covers how to host the KyberChat backend on different platforms and how to configure the Android client to connect to it.

## 1. Hosting Options

### A. Local Hosting (LAN)
Best for testing during development.
**Requirements:** Computer and Android device must be on same Wi-Fi.

1.  **Start Server**:
    ```bash
    cd backend
    python3 -m uvicorn main:app --host 0.0.0.0 --port 8001
    ```
2.  **Find Your IP**:
    *   **macOS/Linux**: `ifconfig` or `ipconfig getifaddr en0` (e.g., `192.168.1.6`)
    *   **Windows**: `ipconfig` (Look for IPv4 Address)
3.  **Firewall**: Ensure port `8001` is allowed through your computer's firewall.

### B. Docker Deployment (Recommended)
We have provided a `Dockerfile` that automatically handles the complex installation of `liboqs` (C library).

1.  **Build Image**:
    ```bash
    docker build -t kyber-backend ./backend
    ```
2.  **Run Container**:
    ```bash
    docker run -p 8001:8001 kyber-backend
    ```

### C. Cloud Hosting (VPS - AWS/DigitalOcean/Linode)
Using a Virtual Private Server (VPS) allows global access over the internet.

#### Option 1: Using Docker (Easier)
1.  **SSH into your VPS**.
2.  **Install Docker**.
3.  **Clone your repo** or upload the `backend` folder.
4.  **Run** the Docker commands from Section B above.
5.  **Firewall/Security Groups**: Open Inbound Port `8001` (Custom TCP) in your cloud provider's console.

#### Option 2: Manual Installation (Ubuntu/Debian)
1.  **Install Build Tools**:
    ```bash
    sudo apt update
    sudo apt install -y git cmake gcc libssl-dev python3-pip
    ```
2.  **Build liboqs (C Library)**:
    ```bash
    git clone --branch main --single-branch https://github.com/open-quantum-safe/liboqs.git
    cd liboqs && mkdir build && cd build
    cmake -GNinja -DOQS_USE_OPENSSL=ON -DCMAKE_INSTALL_PREFIX=/usr/local ..
    ninja
    sudo ninja install
    sudo ldconfig
    ```
3.  **Install Python Wrapper**:
    ```bash
    pip3 install liboqs
    ```
4.  **Run Server**:
    ```bash
    pip3 install -r requirements.txt
    python3 -m uvicorn main:app --host 0.0.0.0 --port 8001
    ```

---

## 2. Configuring the Android Client

Once your server is running (Locally or on Cloud), you must tell the Android app where to find it.

### Step 1: Get the Server URL
*   **Local**: `http://<YOUR_LOCAL_IP>:8001` (e.g., `http://192.168.1.6:8001`)
*   **Cloud**: `http://<YOUR_VPS_PUBLIC_IP>:8001` (e.g., `http://203.0.113.5:8001`)
    *   *Note: If you set up a domain (e.g., api.kyberchat.com), use that instead.*

### Step 2: Update Android Code
1.  Open `android/app/src/main/java/com/example/kyberchat/viewmodel/ChatViewModel.kt`.
2.  Find the network configuration variables:
    ```kotlin
    // OLD
    // private val baseUrl = "http://10.0.2.2:8001" 
    
    // NEW (Example for Cloud)
    private val baseUrl = "http://203.0.113.5:8001" 
    private val wsUrl = "ws://203.0.113.5:8001/ws"
    ```
    *Important: Use `ws://` for the WebSocket URL.*

### Step 3: Rebuild the App
Since the URL is hardcoded, you must rebuild the APK and reinstall it on your device.
```bash
cd android
./gradlew assembleDebug
```
Install the new APK from `android/app/build/outputs/apk/debug/app-debug.apk`.
