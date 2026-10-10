#!/bin/sh
# Baixa e padroniza os toques de terceiros em app/src/main/res/raw. Rodar: sh tools/baixar_sons.sh
# Fontes (licenças em app/src/main/assets/licencas_sons.txt):
#   BigSoundBank (CC0)          https://bigsoundbank.com/UPLOAD/ogg/NNNN.ogg
#   Android AOSP (Apache 2.0)   frameworks/base/data/sounds/alarms/ogg
set -e
RAW="$(dirname "$0")/../app/src/main/res/raw"
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
AOSP=https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/data/sounds/alarms/ogg

pega() { # nome url [base64]
  curl -sfL "$2" -o "$TMP/$1.in"
  [ "${3:-}" = b64 ] && base64 -d "$TMP/$1.in" > "$TMP/$1.dec" && mv "$TMP/$1.dec" "$TMP/$1.in"
  # tira silêncio do começo, iguala volume (todos igualmente altos), mono, máx 12 s, 1 s de respiro antes de repetir
  ffmpeg -v error -y -i "$TMP/$1.in" -t 14 -af "silenceremove=start_periods=1:start_threshold=-45dB,atrim=0:12,loudnorm=I=-13:TP=-1:LRA=11,apad=pad_dur=1" \
    -ac 1 -ar 44100 -c:a libvorbis -q:a 4 "$RAW/$1.ogg"
  echo "$1"
}
pega despertador_antigo https://bigsoundbank.com/UPLOAD/ogg/1375.ogg
pega telefone_antigo    https://bigsoundbank.com/UPLOAD/ogg/3302.ogg
pega galo               https://bigsoundbank.com/UPLOAD/ogg/0283.ogg
pega relogio_pendulo    https://bigsoundbank.com/UPLOAD/ogg/3360.ogg
pega sino_igreja        https://bigsoundbank.com/UPLOAD/ogg/0135.ogg
pega caixinha_musica    https://bigsoundbank.com/UPLOAD/ogg/0195.ogg
pega melodia_calma      "$AOSP/Argon.ogg?format=TEXT" b64
pega melodia_alegre     "$AOSP/Hassium.ogg?format=TEXT" b64
