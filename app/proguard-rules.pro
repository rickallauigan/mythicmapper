# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html


# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class xtr.keymapper.**  { *; }
-keep class xtr.keymapper.**$* { *; }

# SnakeYAML 2.5's optional desktop bean introspection references JavaBeans,
# absent on Android. PropertyUtils selects FIELD when introspection is absent;
# ProfileCodec uses safe scalar/map/sequence parsing and map-only exports.
# Suppress only these unavailable desktop types, never all missing classes.
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.FeatureDescriptor
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
