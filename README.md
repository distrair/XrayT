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

some features are
- hwid support (just type some random bullshit in the text field)
- **removes `inbounds` if imported subsctiption has it (useful if you use russian apps that scans for localhost open ports)**
- **tunnel only, it does not have hev-socks5 and it will not have it why the fuck you should use it on android xray literally has tun support you dumbfuck**
- per app split tunneling
- autoupdating .dat files
- pure kotlin (you can verify it by looking at the right part of your lcd or oled or crt screen)
- pure jetpack compose (⚡lightning fast⚡)

note to self, this is how you compile the release project you fucking dumbass: `./gradlew clean assembleRelease`

## Release signing

Release APKs must be signed with a configured key. Keep the keystore and its passwords out of Git.

For a local release build, create an ignored `signing.properties` file in the project root:

```properties
storeFile=XrayT-release.p12
storePassword=YOUR_KEYSTORE_PASSWORD
keyAlias=xrayt-release
keyPassword=YOUR_KEY_PASSWORD
```

The local `signing.properties` already contains the generated key's credentials. Keep it private. Then run `./gradlew assembleRelease`.

For GitHub Actions, add these repository secrets under **Settings → Secrets and variables → Actions**:

- `ANDROID_KEYSTORE_BASE64`: output of `base64 -w0 XrayT-release.p12`
- `ANDROID_KEYSTORE_PASSWORD`: keystore password
- `ANDROID_KEY_ALIAS`: `xrayt-release`
- `ANDROID_KEY_PASSWORD`: private key password

The workflow decodes the keystore into the runner's temporary directory and signs the release APK there.
