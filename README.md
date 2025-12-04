# 🐰 RedBunny Native

![Build Status](https://github.com/Whymyhuman/RedBunnyNative/actions/workflows/android.yml/badge.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-purple.svg)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Enabled-green.svg)
![License](https://img.shields.io/badge/License-MIT-blue.svg)

**RedBunny Native** adalah aplikasi Android modern untuk **Scraping, Checking, dan Generating** konfigurasi proxy (VLESS, Trojan, VMess) secara otomatis. Dibangun ulang dari nol menggunakan **Kotlin** dan **Jetpack Compose** untuk performa maksimal dan tampilan yang elegan.

---

## ✨ Fitur Unggulan

### 🚀 1. Multi-Source Scraping
Mengambil ribuan proxy secara otomatis dari **15+ sumber premium** di GitHub (termasuk sumber asli RedBunny).
- **Smart Parsing:** Mendukung format `IP:Port`, CSV, JSON, hingga Base64 subscription link.
- **Anti-Cache:** Selalu mendapatkan data terbaru (fresh) setiap kali tombol ditekan.
- **Custom Sources:** Tambahkan URL sumber scraping Anda sendiri melalui menu pengaturan.

### ⚡ 2. Advanced Proxy Checker
Pastikan proxy berfungsi sebelum digunakan!
- **TCP Ping:** Mengukur latensi (ms) koneksi ke server proxy secara akurat.
- **Speedtest Check:** Memverifikasi apakah proxy bisa membuka `speedtest.net`.
- **GeoIP Detection:** Otomatis mendeteksi **Negara** (Flag) dan **ISP** dari setiap proxy yang aktif.

### 🛠️ 3. Config Generator
Ubah IP:Port menjadi konfigurasi siap pakai untuk aplikasi VPN favorit Anda (v2rayNG, NekoBox, dll).
- Mendukung protokol **VLESS** dan **Trojan**.
- Custom **SNI (Server Name Indication)** dan **Bug Host** (Front Domain).
- Fitur **Search & Select** untuk memilih proxy tertentu (misal: hanya proxy Indonesia).

### 🎨 4. Modern UI
- **Material Design 3:** Tampilan gelap (Dark Mode) yang nyaman di mata.
- **Responsive:** Daftar proxy yang cepat dengan fitur pencarian real-time.

---

## 📥 Download

Dapatkan versi terbaru (APK Signed) dari tab **Actions** atau **Releases**:

[**➡️ Download APK Terbaru**](https://github.com/Whymyhuman/RedBunnyNative/actions)

1. Klik pada *workflow run* paling atas (bertanda ✅).
2. Scroll ke bawah ke bagian **Artifacts**.
3. Download file **RedBunnyNative-Signed**.

---

## 📸 Screenshots

| Dashboard & Scrape | Checking & GeoIP | Generator & Config |
|:---:|:---:|:---:|
| *(Tambahkan Screenshot)* | *(Tambahkan Screenshot)* | *(Tambahkan Screenshot)* |

---

## 🛠️ Cara Build Sendiri (Developer)

Jika Anda ingin memodifikasi kode, ikuti langkah ini:

1.  **Clone Repository**
    ```bash
    git clone https://github.com/Whymyhuman/RedBunnyNative.git
    cd RedBunnyNative
    ```

2.  **Buka di Android Studio**
    *   Tunggu proses sync Gradle selesai.

3.  **Build Debug APK**
    *   Jalankan perintah: `./gradlew assembleDebug`
    *   Atau tekan tombol **Run** (▶️) di Android Studio.

---

## ⚠️ Disclaimer

Aplikasi ini dibuat untuk tujuan **edukasi dan penelitian**. Penggunaan proxy dan konfigurasi jaringan sepenuhnya menjadi tanggung jawab pengguna. Developer tidak bertanggung jawab atas penyalahgunaan aplikasi ini untuk aktivitas ilegal.

---

Made with ❤️ by **Whymyhuman** & **Gemini AI**
