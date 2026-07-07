-repackageclasses 'com.splunk.rum.common.encoder'

# PreferencesExt.videoBitrate
-keepnames class com.splunk.rum.common.encoder.Encoder

-keepclassmembers class com.splunk.rum.common.encoder.Encoder {
    java.lang.Integer bitrateOverride;
}

-dontwarn java.lang.invoke.StringConcatFactory