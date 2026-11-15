# Membuat APK hanya dengan HP Android

Project ini dapat dicoba dibangun lewat GitHub Actions dari browser HP. Build cloud yang berhasil akan menghasilkan APK debug sebagai artifact; project ini belum dinyatakan berhasil build atau diuji pada perangkat.

1. Unduh dan ekstrak ZIP project.
2. Buka https://github.com lewat Chrome, masuk akun, lalu buat repository baru bernama `HQAudioProcessor` (Public atau Private).
3. Upload seluruh isi folder `HQAudioProcessor` hasil ekstrak ke root repository. Pastikan folder `.github/workflows` ikut terunggah dan file `android-apk.yml` berada di `.github/workflows/`.
4. Buka tab **Actions** pada repository. Pilih workflow **Build HQ Audio Processor APK**, lalu tekan **Run workflow**.
5. Tunggu proses selesai. Jika hijau, buka run tersebut dan unduh artifact `HQAudioProcessor-debug-apk`.
6. Ekstrak artifact ZIP di HP, lalu buka `app-debug.apk`. Izinkan instalasi dari sumber tersebut bila Android meminta.

Jika build merah/gagal, buka log langkah yang merah dan kirim teks error-nya agar bisa diperbaiki. Jangan menganggap APK tersedia sebelum artifact berhasil dihasilkan.

Catatan: ini APK debug untuk pengujian, bukan release bertanda tangan untuk distribusi. AudioPlaybackCapture hanya menangkap aplikasi sumber yang mengizinkan capture. Android tetap dapat memutar audio asli secara bersamaan dengan hasil DSP; aplikasi ini tidak dapat memaksa seluruh audio sistem melewati DSP.
