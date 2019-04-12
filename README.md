Join our chat on the app: Telegram Messenger, https://t.me/OkAnfroidSdk

# ok-android-sdk
Android SDK and sample for native apps integrated with OK.RU


## Connecting With Maven
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/ru.ok/odnoklassniki-android-sdk/badge.svg)](https://maven-badges.herokuapp.com/maven-central/ru.ok/odnoklassniki-android-sdk)

You can add next maven dependency in your project:

`ru.ok:odnoklassniki-android-sdk:[MAVEN_CENTRAL_VERSION]`

For example, your gradle script will contains such dependencies: 
```
dependencies {
    implementation fileTree(dir: 'libs', include: ['*.jar'])
    implementation 'ru.ok:odnoklassniki-android-sdk:2.1.7'
}
```


## Application (registered on OK.ru) Requirements
An [application registered](https://ok.ru/showcase/myuploaded) within OK platform should have:

1. Target platform checked (like ANDROID or IOS)
2. EXTERNAL platform checked
3. Client OAUTH checkbox checked
4. A VALUABLE_ACCESS permission being checked or requested
5. (highly recommended) LONG_ACCESS_TOKEN permission requested from [api-support](mailto:api-support@ok.ru) in order to be able to use tokens with long ttl

## Library Requirements
 
- AndroidManifest.xml:
  - OkAuthActivity should be added (see Quick Start [example](https://github.com/odnoklassniki/ok-android-sdk/blob/master/odnoklassniki-android-sdk-example/src/main/AndroidManifest.xml#L22))
  - Specify correct [scheme-host](https://github.com/odnoklassniki/ok-android-sdk/blob/master/odnoklassniki-android-sdk-example/src/main/AndroidManifest.xml#L32) pair like _okauth://ok12345_ where _12345_ is your application id on OK platform for native SSO auth to work correctly
- If using sdk.getInstallSource
  - a dependency to play-services-ads to get advertising id


## Quick Start
A quick-start with login and viral widgets is available in [example](https://github.com/odnoklassniki/ok-android-sdk/tree/master/odnoklassniki-android-sdk-example)
