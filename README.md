# XrayT – Xray-core Android client (core TUN only)
> [!CAUTION]
> # **THIS IS EXTREMLY VIBE CODED, USE AT YOUR RISK, I DO NOT KNOW WHAT THE FUCK DID CLAUDE AND GEMINI WROTE HERE, IT WORKS FOR ME, IT MIGHT BE NOT FOR YOU**
the xray core used here is from https://github.com/XTLS/libXray/
1. install that `jdk17-openjdk gradle` idk what it will be on your system i compiled it on arch **btw**
2. then do `archlinux-java set java-17-openjdk`
3. this one is from aur `android-sdk-cmdline-tools-latest`
1. download the libXray.aar from that one repo above me and put it in `app/libs/`
to update just replace it with the new one
1. you dont need to download the .dat files, they are autoupdated daily (this is what claude told me)
2. then just compile it with the noted command below

note to self, this is how you compile the release project you fucking dumbass: `./gradlew clean assembleRelease`
