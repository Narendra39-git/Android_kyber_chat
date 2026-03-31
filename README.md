# Quantum-Safe Android Chat Application

This is a secure, post-quantum cryptography (PQC) enabled chat application built for Android. It uses state-of-the-art algorithms standardized by NIST (FIPS 203 and FIPS 204) to protect messages against future threats from quantum computers.

## 🚀 Features

*   **Post-Quantum Security**: Utilizes ML-KEM (Kyber) for key exchange and ML-DSA (Dilithium) for sender authentication.
*   **End-to-End Encryption (E2EE)**: Messages are encrypted locally on the device using AES-256-GCM before transmission.
*   **Real-Time Messsaging**: WebSocket-based architecture for instant communication.
*   **Modern Android UI**: Built with Jetpack Compose using Material 3 design.

## 🛠️ Technical Specifications

### Cryptographic Algorithms
| Function | Algorithm | NIST Standard | Details |
| :--- | :--- | :--- | :--- |
| **Key Encapsulation** | **ML-KEM-768** | FIPS 203 | Formerly Kyber-768. Used to securely establish a shared secret between clients. |
| **Digital Signatures** | **ML-DSA-65** | FIPS 204 | Formerly Dilithium-3. Used to sign every message payload to verify sender identity and prevent tampering. |
| **Symmetric Encryption** | **AES-256-GCM** | FIPS 197 | Used for the actual message content encryption using the key derived from ML-KEM. |
| **Hashing** | **SHA-256** | FIPS 180-4 | Used for key derivation (HKDF-style) and payload hashing. |

### Technology Stack
*   **Android Client**:
    *   Language: **Kotlin**
    *   Crypto Library: **Bouncy Castle 1.79** (Java)
    *   UI Framework: Jetpack Compose
*   **Backend Server**:
    *   Language: **Python 3.10+**
    *   Framework: **FastAPI** + WebSockets
    *   Crypto Library: **liboqs-python** (Open Quantum Safe wrapper)

## 📋 Prerequisites

*   **Android**:
    *   Android Studio Iguana or later
    *   JDK 17
    *   Android Device/Emulator (Min SDK 26)
*   **Backend**:
    *   Python 3.10 or later
    *   `liboqs` (C library installed on system)
    *   `pip` packages: `fastapi`, `uvicorn`, `websockets`, `pydantic`

## ⚙️ Installation & Run Instructions

### 1. Backend Server Setup
The backend facilitates key exchange and message routing. It does **not** see plaintext messages.

```bash
# Navigate to backend directory
cd backend

# Install dependencies (ensure liboqs is installed on your system first!)
pip install -r requirements.txt

# Start the server (host 0.0.0.0 is important for external access)
python3 -m uvicorn main:app --host 0.0.0.0 --port 8001
```

### 2. Android Client Setup
You can run the app from Android Studio or install the pre-built APK.

**Pre-requisite:** Ensure your Android device and computer are on the **same Wi-Fi network**.

1.  **Configure IP Address**:
    *   Open `android/app/src/main/java/com/example/kyberchat/viewmodel/ChatViewModel.kt`.
    *   Update `baseUrl` and `wsUrl` with your computer's local IP (e.g., `http://192.168.1.5:8001`).
    *   *Note: This has effectively been done for the current build.*

2.  **Build & Install**:
    ```bash
    cd android
    # Build the debug APK
    ./gradlew assembleDebug
    ```
    *   **APK Location**: `android/app/build/outputs/apk/debug/app-debug.apk`
    *   Install this APK on your physical Android device.

## 📱 Usage

1.  Start the Python backend server.
2.  Open the **KyberChat** app on your phone.
3.  Enter a unique **Client ID** (username) and click **Register**.
    *   This generates your ephemeral ML-KEM and ML-DSA keys and uploads the public keys to the server.
4.  Once registered, enter the **Recipient ID** of another connected user.
5.  Type a message and hit Send.
    *   The app will:
        1.  Fetch recipient's ML-KEM public key.
        2.  Encapsulate a shared secret.
        3.  Encrypt message with AES-256 (derived from secret).
        4.  Sign the encrypted payload with your ML-DSA private key.
        5.  Send to server -> Server forwards to recipient.

