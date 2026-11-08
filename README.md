# XrayT – Xray-core Android client (core TUN only)

1. Download `libXray.aar` from the XTLS/libXray releases -> `app/libs/`
   (it must be built against an Xray-core version that includes the TUN inbound).
2. geoip.dat / geosite.dat are downloaded from Loyalsoldier/v2ray-rules-dat on first launch and then daily.
   Optionally bundle copies in `app/src/main/assets/` as an offline fallback.
3. Open in Android Studio (it generates the Gradle wrapper), build, install.

- if you want to update to a new xray-core: `./gradlew clean :app:assembleRelease`
