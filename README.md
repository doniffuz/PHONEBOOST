# PHONEBOOST
Clean • Optimize • Boost

## Termux'da build
```
pkg install openjdk-17 gradle aapt2
export ANDROID_HOME=$HOME/android-sdk   # platforms;android-35 va build-tools kerak
./build.sh                              # debug APK
./build.sh assembleRelease              # release APK
```
