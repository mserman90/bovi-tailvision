@echo off
chcp 65001 > nul
echo ========================================================
echo  BOVI-TAILVISION & BOVI-FARMER GITHUB DEPOLAMA VE YAYIN
echo ========================================================
echo.

where git >nul 2>nul
if %errorlevel% neq 0 (
    echo [UYARI] Bilgisayarınızda Git yüklü veya PATH'e ekli bulunamadı.
    echo Lütfen https://git-scm.com adresinden Git'i indirip kurun.
    echo Ardından bu betiği tekrar çalıştırın.
    echo.
    pause
    exit /b
)

echo [1/4] Git deposu başlatılıyor...
git init

echo [2/4] Dosyalar ekleniyor...
git add .

echo [3/4] İlk commit oluşturuluyor...
git commit -m "feat: Initial commit - Bovi-TailVision Edge AI, Bovi-Farmer and GitHub Pages Interactive Demo"

echo [4/4] Ana dal 'main' olarak ayarlanıyor...
git branch -M main

echo.
echo ========================================================
echo  DEPO YEREL OLARAK HAZIRLANDI!
echo ========================================================
echo  GitHub üzerinde boş bir repo açtıktan sonra çalıştırmanız
echo  gereken komutlar:
echo.
echo  git remote add origin https://github.com/<KULLANICI_ADINIZ>/bovi-tailvision.git
echo  git push -u origin main
echo.
echo  GitHub Pages'i açmak için:
echo  Repo Ayarları -> Pages -> Build and deployment -> Source: 'GitHub Actions'
echo  veya Branch: 'main', Folder: '/docs' seçin.
echo ========================================================
pause
