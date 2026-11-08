# XrayT – Xray-core Android client (core TUN only)
> [!CAUTION]
> # **THIS IS EXTREMLY VIBE CODED, USE AT YOUR RISK, I DO NOT KNOW WHAT THE FUCK DID CLAUDE AND GEMINI WROTE HERE, IT WORKS FOR ME, IT MIGHT BE NOT FOR YOU**

1. Download `libXray.aar` from the XTLS/libXray releases -> `app/libs/`
   (it must be built against an Xray-core version that includes the TUN inbound).
2. geoip.dat / geosite.dat are downloaded from Loyalsoldier/v2ray-rules-dat on first launch and then daily.
   Optionally bundle copies in `app/src/main/assets/` as an offline fallback.
3. Open in Android Studio (it generates the Gradle wrapper), build, install.

note to self, this is how you compile the release project you fucking dumbass: `./gradlew clean :app:assembleRelease`
