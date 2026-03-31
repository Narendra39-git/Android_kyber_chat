# Project Presentation Guide & Script

Use this document as a script or outline when explaining your project to your guide or mentor.

## 1. High-Level Summary (The "Elevator Pitch")

**"This is a Quantum-Safe Android Chat Application."**

*   **Objective**: To build a secure messaging platform resistant to attacks from future quantum computers, avoiding the vulnerabilities of traditional systems (RSA/ECC) by using NIST-standardized Post-Quantum Cryptography (PQC).
*   **Key Features**:
    1.  **End-to-End Encryption**: Messages are encrypted locally on the device.
    2.  **Quantum Resistance**: Uses **ML-KEM-768 (Kyber)** for key exchange.
    3.  **Authentication**: Uses **ML-DSA-65 (Dilithium)** for digital signatures.
    4.  **Real-Time**: Low-latency communication via WebSockets.

---

## 2. Architecture Overview

Explain that the system uses a standard **Client-Server** model, but with a "Zero-Knowledge" server approach regarding message content.

*   **Android Client (Kotlin)**:
    *   **UI**: Built with **Jetpack Compose** (Modern Android UI).
    *   **Architecture**: MVVM (Model-View-ViewModel).
    *   **Crypto Engine**: Uses **Bouncy Castle (Java)** for low-level cryptographic primitives.
*   **Backend Server (Python)**:
    *   **Tech**: FastAPI + WebSockets + **liboqs-python**.
    *   **Role**: Acts as a relay. It handles user registration and key storage but **cannot decrypt messages**.

---

## 3. The Core Workflow (Step-by-Step)

This is the technical heart of the project. Explain the lifecycle of a message.

### Step A: Registration
"When a user creates an account, the app generates two key pairs locally:"
1.  **Identity Key (ML-DSA-65)**: Used to *sign* outgoing messages.
2.  **Encryption Key (ML-KEM-768)**: Used to *receive* messages.
*   *Note*: The Public Keys are sent to the server; the Private Keys **never** leave the device.

### Step B: Sending a Message (The "Encapsulate" Flow)
"We use a Key Encapsulation Mechanism (KEM) instead of static encryption keys."

1.  **Fetch Key**: The sender fetches the recipient's **ML-KEM Public Key** from the server.
2.  **Encapsulate**: The sender runs the `Encapsulate` function.
    *   *Output 1*: A **Shared Secret** (random 32-byte key).
    *   *Output 2*: A **Ciphertext** (sent to the recipient to recover the secret).
3.  **Encrypt (AES)**: The actual text message is encrypted with **AES-256-GCM** using the Shared Secret.
4.  **Sign**: The encrypted payload is signed with the sender's **ML-DSA Private Key**.

### Step C: Receiving
1.  **Verify**: The recipient verifies the signature using the sender's Public Key.
2.  **Decapsulate**: The recipient uses their **ML-KEM Private Key** on the Ciphertext to recover the **Shared Secret**.
3.  **Decrypt**: The Shared Secret unlocks the AES-encrypted message.

---

## 4. Code Walkthrough (What to Show)

Navigate to these files to demonstrate your implementation:

### 1. The Logic Core: `ChatViewModel.kt`
*   **Path**: `android/app/src/main/java/com/example/kyberchat/viewmodel/ChatViewModel.kt`
*   **Highlight**:
    *   `register()`: Shows key generation and server registration.
    *   `sendMessage()`: The detailed flow of `getKey` -> `encapsulate` -> `encrypt` -> `sign` -> `send`.

### 2. The Cryptography Engine: `CryptoManager.kt`
*   **Path**: `android/app/src/main/java/com/example/kyberchat/crypto/CryptoManager.kt`
*   **Highlight**:
    *   `encapsulate()`: The call to the Bouncy Castle/Kyber library.
    *   `deriveAesKey()`: Connecting the quantum secret to standard AES encryption.

### 3. The Data Structure: `Message.kt`
*   **Path**: `android/app/src/main/java/com/example/kyberchat/network/Message.kt`
*   **Highlight**:
    *   Show the JSON fields: `kemCiphertext`, `nonce`, `signature`. This proves that all necessary components for the protocol are being transmitted.

---

## 5. Potential Questions & Answers

*   **Q: Why do you need AES if you have Kyber?**
    *   *A: Kyber is a KEM (Key Encapsulation Mechanism). It is designed to share a small secret key securely, not to encrypt large amounts of data. We use Kyber to agree on a key, and then AES to lock the actual message.*
*   **Q: Can the server read the messages?**
    *   *A: No. The server only sees encrypted blobs. It does not have the private keys required to Decapsulate the shared secret.*
