#!/usr/bin/env bash
# Crea los emuladores de la matriz de pruebas, en macOS o Linux.
# Uso: tools/avd.sh            (crea los que falten)
#      tools/avd.sh --redo     (los vuelve a crear desde cero; borra sus datos)
# Imagen: android-35 google_apis_playstore de la arquitectura del equipo (arm64-v8a en la
# Mac con Apple Silicon, x86_64 en Linux), salvo la que diga otra en la 7.ª columna (LD_A6:
# Android 6, el mínimo que soporta LiteDash, con 1 GB como las pantallas viejas).
# Otra imagen para todos: AVD_IMAGE="system-images;android-35;google_apis;x86_64" tools/avd.sh
# Bajar la imagen antes: sdkmanager "<imagen>", o el SDK Manager de Android Studio.
set -euo pipefail
case "$(uname -s)" in
  Darwin) DEFAULT_SDK="$HOME/Library/Android/sdk" ;;
  *)      DEFAULT_SDK="$HOME/Android/Sdk" ;;
esac
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$DEFAULT_SDK}}"
case "$(uname -m)" in
  arm64|aarch64) ABI=arm64-v8a; ARCH=arm64 ;;
  *)             ABI=x86_64;    ARCH=x86_64 ;;
esac
IMG="${AVD_IMAGE:-system-images;android-35;google_apis_playstore;$ABI}"
AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"

# name          width height dpi ram_mb cores [imagen]
MATRIX=(
  "LD_HU7_1024x600   1024 600  160 2048 2"
  "LD_HU10_1280x720  1280 720  160 2048 4"
  "LD_HU11_2000x1200 2000 1200 240 4096 4"
  "LD_Ancha_1920x720 1920 720  160 4096 4"
  "LD_Vertical_768x1024 768 1024 160 2048 4"
  "LD_A6_1024x600    1024 600  160 1024 2 system-images;android-23;default;$ABI"
)

mkdir -p "$AVD_HOME"
for row in "${MATRIX[@]}"; do
  read -r name width height dpi ram cores image <<<"$row"
  image="${image:-$IMG}"
  dir="$AVD_HOME/$name.avd"
  if [ -d "$dir" ] && [ "${1:-}" != "--redo" ]; then echo "ya existe $name"; continue; fi
  IFS=';' read -r _ platform tag _ <<<"$image"
  sysdir="${image//;//}/"
  if [ ! -f "$SDK/$sysdir/system.img" ]; then
    echo "falta la imagen de $name: sdkmanager \"$image\" (o elige otra con AVD_IMAGE)"; continue
  fi
  case "$tag" in *playstore*) play=true ;; *) play=false ;; esac
  rm -rf "$dir"; mkdir -p "$dir"
  printf 'avd.ini.encoding=UTF-8\npath=%s\ntarget=%s\n' "$dir" "$platform" > "$AVD_HOME/$name.ini"
  # Pantalla de auto: táctil, sin botones de hardware ni marco, con teclado de la compu.
  # GPU del equipo (sin esto el emulador usa SwiftShader: lento y se cuelga) y arranque
  # rápido desde el snapshot guardado al cerrar.
  cat > "$dir/config.ini" <<EOF
AvdId=$name
avd.ini.displayname=$name
avd.ini.encoding=UTF-8
target=$platform
image.sysdir.1=$sysdir
tag.id=$tag
abi.type=$ABI
hw.cpu.arch=$ARCH
PlayStore.enabled=$play
disk.dataPartition.size=6G
hw.lcd.width=$width
hw.lcd.height=$height
hw.lcd.density=$dpi
hw.ramSize=$ram
hw.cpu.ncore=$cores
hw.keyboard=yes
hw.mainKeys=no
skin.dynamic=yes
showDeviceFrame=no
hw.gpu.enabled=yes
hw.gpu.mode=host
fastboot.forceColdBoot=no
fastboot.forceFastBoot=yes
EOF
  echo "creado $name (${width}x${height} @${dpi}dpi, ${ram} MB, ${cores} núcleos, $image)"
done
